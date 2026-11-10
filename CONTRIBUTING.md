# Contributing to Campus ERP

Thank you for your interest in contributing to **Campus ERP**! This document provides essential guidelines for development, security standards, coding conventions, and collaborative workflow.

---

## 1. Project Prerequisites

Before contributing, ensure your local development environment meets the following baseline requirements:
- **Java SE Development Kit (JDK):** Version 17 (LTS) or higher.
- **Apache Maven:** Version 3.8.0 or higher.
- **Operating System:** Windows 10/11, Ubuntu 20.04+, or macOS.
- **Git:** Version 2.30+.

Verify your environment:
```bash
java -version
mvn -version
git --version
```

---

## 2. Four-Member Collaboration Model

Campus ERP is maintained through a four-member modular ownership architecture. Each team member stewards a distinct institutional subsystem while collaborating on shared infrastructure:

1. **Member 1 — Core System & User Management:**
   - Architecture foundation, application startup pipeline (`App.java`), database migrations (`DatabaseInitializer.java`).
   - Authentication, RBAC session management (`AuthService.java`), password hashing, and user credential lifecycle (`UserManagementPanel.java`).
2. **Member 2 — Academic Structure & Curriculum Management:**
   - Institutional schools, departments, degree programmes, and semesters (`DepartmentService.java`, `AcademicProgramService.java`).
   - Curriculum syllabus mapping, prerequisite validation graph, and student cohort progression (`ProgrammeCurriculumService.java`, `StudentProgressionService.java`).
3. **Member 3 — Attendance Automation & Timetable Scheduling:**
   - Timed OTP attendance session management (`AttendanceSessionService.java`, `AttendancePanel.java`).
   - Anti-tamper verification, failed submission lockout enforcement, and roster review dialogs.
   - Timetable scheduling matrix and classroom allocation (`TimetableService.java`).
4. **Member 4 — Financial Ledger, Analytics & Release Integration:**
   - Fee structure definitions, student payment ledger (`FeePaymentService.java`, `FeePaymentPanel.java`).
   - Integer-paise financial accounting, dual-entry refund recording, and institutional banking (`BankDetailsService.java`).
   - Analytical reporting dashboards (`AcademicOverviewPanel.java`), CSV exports, and build pipeline verification.

---

## 3. Build & Test Workflow

Always verify that the complete test suite passes before submitting changes:

```bash
# 1. Compile all source files
mvn compile

# 2. Run the complete JUnit 5 regression test suite
mvn test

# 3. Build the packaged executable JAR
mvn package
```

### Testing Standards:
- All tests must execute against isolated, ephemeral SQLite databases or in-memory connections (`TestDBHelper.java`).
- Never run tests against `data/college.db`.
- Tests must achieve 100% pass rate with zero failures and zero skipped tests.

---

## 4. Architectural & Coding Conventions

Campus ERP strictly adheres to a 5-tier architecture:

```
Model  ───►  DAO  ───►  Service  ───►  SwingWorker  ───►  Swing UI Panel
```

1. **Model Layer (`com.college.model`):**
   - Pure, serializable domain POJOs with encapsulated private fields, getters, setters, and descriptive `toString()`.
2. **Data Access Layer (`com.college.dao`):**
   - Pure JDBC using `PreparedStatement` with parameterized queries.
   - String concatenation in SQL statements is strictly prohibited.
   - Always manage connection lifecycles cleanly with try-with-resources.
3. **Business Service Layer (`com.college.service`):**
   - Business rule enforcement, input validation, transaction boundaries (`conn.commit()` / `conn.rollback()`), and role authorization (`AuthService.requireRole()`).
4. **Threading & Concurrency (`SwingWorker`):**
   - Intensive database I/O, report exports, and BCrypt password calculations must run asynchronously on background threads via `SwingWorker` or `CompletableFuture`.
   - Never block the Swing Event Dispatch Thread (EDT).
5. **UI Layer (`com.college.ui`):**
   - Modern FlatLaf theme styling, DPI-aware vector icons (`CampusIcons.java`), and accessible tabular layouts.
6. **Financial Precision:**
   - All monetary values must be represented as integer paise (`long` or `int`), never as floating-point `double` or `float`.

---

## 5. Security & Privacy Rules

Security is a primary quality attribute of Campus ERP. Every contributor must observe these mandatory rules:

1. **Zero Hardcoded Secrets:**
   - Never commit passwords, API keys, tokens, or private keys to the repository.
   - Test fixtures must use synthetic dummy passwords (`MockPassword@123`).
2. **Database Exclusion:**
   - Never commit `data/college.db`, any `.db`, `.sqlite`, `.db-wal`, or `.db-shm` file.
   - The `.gitignore` policy strictly excludes runtime databases.
3. **Password Security:**
   - Passwords must be hashed using OpenBSD BCrypt (cost factor 12) via `PasswordUtil.hashPassword()`.
   - Newly provisioned accounts must receive cryptographically random temporary passwords with `must_change_password = 1`.
4. **Synthetic Data Policy:**
   - All sample or seed data must use RFC 2606 synthetic domains (`@example.test`) and synthetic phone numbers (`+91 98765 0...`).
   - Never commit real student, faculty, or institutional financial records.

---

## 6. Branching & Pull Request Process

1. **Branch Naming:**
   - Features: `feature/short-description` (e.g. `feature/attendance-qr-generator`)
   - Bug fixes: `bugfix/issue-description` (e.g. `bugfix/timetable-conflict-check`)
   - Chores / Maintenance: `chore/dependency-upgrade`
2. **Commits:**
   - Write clear, imperative commit messages (e.g. `feat: add semester progression audit trigger`).
3. **Pull Requests:**
   - Ensure `mvn test` passes completely on your branch.
   - Provide a clear summary of changes, modified files, and test coverage.
   - PRs require review and approval from at least one peer team member.
