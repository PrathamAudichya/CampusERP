# CAMPUS ERP — ARCHITECTURE MANUAL
**System:** Campus ERP — College Management System  
**Version:** 2.3.0  
**Target Platform:** Java SE 17+ | Java Swing + FlatLaf | Pure SQLite JDBC  
**Architecture Pattern:** Layered Desktop Architecture (`Model → DAO → Service → SwingWorker → Swing UI`)  

---

## 1. Architectural Principles

Campus ERP is built upon an offline-first, layered desktop architecture engineered for institutional stability, predictability, and responsive user feedback:
- **Separation of Concerns:** Business logic is isolated from UI presentation and database persistence.
- **Pure Java SE & Standard Libraries:** Eliminates external application servers, web containers, or distributed runtime infrastructure.
- **Thread Safety on Swing EDT:** All long-running database, credential hashing, and analytical calculations run on background threads via `SwingWorker` or `SwingUtils.executeWorker`, keeping the Swing Event Dispatch Thread (EDT) completely responsive.
- **Idempotent Data Access:** Stateless DAO layers communicating via parameterized JDBC statements to prevent SQL injection and connection leaks.
- **Deterministic Schema Evolution:** Additive, forward-only migrations managed inside `DatabaseInitializer.java` guarantee backward compatibility with legacy databases.

---

## 2. Layer Responsibilities

```mermaid
flowchart TD
    subgraph UI_Layer["1. Presentation Layer (Swing + FlatLaf)"]
        Frames["Frames: LoginFrame, MainFrame"]
        Panels["Panels: DepartmentPanel, AcademicProgramPanel, AttendancePanel..."]
        Dialogs["Dialogs: StudentCheckInDialog, ReviewAttendanceDialog, AcademicProgramDialog..."]
        Theme["Theme: CampusTheme, CampusIcons, CampusLogo, StatusBadge"]
    end

    subgraph Worker_Layer["2. Concurrency & Dispatch Layer"]
        Worker["SwingUtils.executeWorker / SwingWorker"]
        EDT["Swing Event Dispatch Thread (EDT)"]
    end

    subgraph Service_Layer["3. Business & Service Layer"]
        Services["Services: AuthService, StudentService, AttendanceSessionService, AcademicProgramService..."]
        Validation["Validation & Security: PasswordUtil, ValidationUtil, SecurityUtil"]
    end

    subgraph DAO_Layer["4. Data Access Object (DAO) Layer"]
        DAOs["DAOs: UserDAO, StudentDAO, AttendanceDAO, AcademicProgramDAO, FeePaymentDAO..."]
        DBC["DBConnection & Transaction Handlers"]
    end

    subgraph Data_Layer["5. Persistence Layer"]
        SQLite[(SQLite Database File: college.db)]
        Migrations["Migration Table: schema_migrations"]
    end

    Frames --> Worker
    Panels --> Worker
    Dialogs --> Worker
    Worker --> Services
    Worker -.->|publish / done| EDT
    EDT --> Panels
    Services --> DAOs
    Services --> Validation
    DAOs --> DBC
    DBC --> SQLite
```

### 2.1 Layer Breakdown

1. **Presentation Layer (`com.college.ui.*`):**
   - Implements modern, accessible desktop interfaces utilizing FlatLaf styling (`#1E3A5F` primary navy, `#C97A63` warm terracotta, `#F5F6F2` canvas).
   - Uses `CardLayout` in `MainFrame.java` to switch between views instantaneously without spawning secondary application windows.
   - Vector-rendered iconography in `CampusIcons.java` eliminates missing-glyph unicode errors across different operating systems.

2. **Concurrency & Dispatch Layer (`com.college.ui.util.SwingUtils`):**
   - Encapsulates `javax.swing.SwingWorker` to offload database calls, password hashing, and report exports to background worker threads.
   - Dispatches safe UI callbacks back onto the EDT to prevent GUI deadlocks.

3. **Service Layer (`com.college.service.*`):**
   - Implements business logic, transaction boundaries, domain invariant checks, and Role-Based Access Control (RBAC).
   - Validates user input through `ValidationUtil` and manages BCrypt password hashing through `PasswordUtil`.

4. **Data Access Object (DAO) Layer (`com.college.dao.*`):**
   - Encapsulates pure SQL operations using JDBC `PreparedStatement` and `ResultSet`.
   - Handles connection retrieval from `DBConnection.getConnection()`, transaction management (`conn.setAutoCommit(false)`), and explicit rollback upon exceptions.

5. **Persistence Layer (`com.college.config.*`):**
   - Manages SQLite connection pools and database initialization via `DatabaseInitializer.java`.
   - Executes DDL (`schema.sql`), seed data (`seed.sql`), and additive versioned migrations (`V1` through `V9`).

---

## 3. Module Relationships

```mermaid
classDiagram
    direction TB

    class User {
        +int id
        +String username
        +String role
        +boolean active
    }

    class Student {
        +int id
        +String studentId
        +int userId
        +int departmentId
        +Integer programId
        +Integer cohortId
        +String semester
    }

    class Faculty {
        +int id
        +String facultyId
        +int userId
        +int departmentId
        +String specialization
    }

    class Department {
        +int id
        +String code
        +String name
        +String school
    }

    class AcademicProgram {
        +int id
        +int departmentId
        +String code
        +String name
        +int durationYears
        +int totalSemesters
    }

    class StudentCohort {
        +int id
        +String cohortCode
        +int programId
        +String admissionYear
        +String expectedGraduationYear
    }

    class Course {
        +int id
        +String courseCode
        +String name
        +int credits
    }

    class AttendanceSession {
        +int id
        +int facultyId
        +int courseId
        +String sessionCode
        +String status
    }

    class FeePayment {
        +int id
        +int studentId
        +int amountPaidPaise
        +String receiptNumber
        +String status
    }

    User <|-- Student : links via user_id
    User <|-- Faculty : links via user_id
    Department <-- AcademicProgram : belongs to
    Department <-- Student : enrolled in
    Department <-- Faculty : appointed to
    AcademicProgram <-- StudentCohort : batch of
    AcademicProgram <-- Student : studies in
    StudentCohort <-- Student : member of
    Course <-- AttendanceSession : conducted for
    Student <-- FeePayment : paid by
```

---

## 4. Application Execution Flow

```mermaid
sequenceDiagram
    autonumber
    actor User as Operator / Student
    participant App as App (Main)
    participant Init as DatabaseInitializer
    participant Login as LoginFrame
    participant Auth as AuthService
    participant Main as MainFrame
    participant DB as SQLite DB

    User->>App: Launch Application
    App->>Init: initializeDatabase()
    Init->>DB: Apply DDL & Migrations (V1..V9)
    App->>Login: Display Login Screen
    User->>Login: Enter credentials (username, password)
    Login->>Auth: login(username, password)
    Auth->>DB: Query User & verify BCrypt hash
    DB-->>Auth: User record (role, must_change_password)
    alt First Login (must_change_password = true)
        Auth-->>Login: Mandatory Password Change Required
        Login->>User: Prompt for new password & confirmation
        User->>Login: Submit new password
        Login->>Auth: changePassword(userId, newPassword)
        Auth->>DB: Update password_hash & must_change_password=false
    end
    Auth-->>Login: Successful Authentication
    Login->>Main: Instantiate MainFrame(currentUser)
    Login->>Login: dispose()
    Main->>User: Render role-segregated dashboard views
```

