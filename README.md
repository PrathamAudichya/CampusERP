# Campus ERP — College Management System

[![Java 17](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/projects/jdk/17/)
[![Swing GUI](https://img.shields.io/badge/UI-Java%20Swing%20%2B%20FlatLaf-orange.svg)](https://docs.oracle.com/javase/tutorial/uiswing/)
[![SQLite](https://img.shields.io/badge/Database-SQLite%203.54-lightgrey.svg)](https://www.sqlite.org/)
[![Pure JDBC](https://img.shields.io/badge/Access-Pure%20JDBC-green.svg)](https://docs.oracle.com/javase/8/docs/technotes/guides/jdbc/)
[![Build](https://img.shields.io/badge/Build-Maven%203.8%2B-red.svg)](https://maven.apache.org/)
[![Tests](https://img.shields.io/badge/Tests-341%2F341%20PASS-brightgreen.svg)](https://junit.org/junit5/)

**Campus ERP** is an integrated, institutional-grade desktop academic management system engineered for higher education institutions. Built strictly with standard Java 17, Java Swing, FlatLaf, Pure JDBC, and SQLite, it delivers a secure, offline-capable college ERP solution without requiring cloud dependencies, heavyweight containers, or external web servers.

---

## 1. Project Objectives & Overview

Campus ERP centralizes collegiate administration across identity, academics, attendance, scheduling, and finance into a single, cohesive desktop platform.
- **Unified Relational Core:** Centralizes student, faculty, course, timetable, attendance, and payment records in an ACID-compliant embedded SQLite database.
- **Enterprise Security & RBAC:** Enforces Role-Based Access Control (`ADMIN`, `FACULTY`, `STUDENT`) with BCrypt salted password hashing, mandatory password reset on first login, and session audit logs.
- **Automated Academic Workflows:** Provides automated student credential provisioning, timed OTP attendance check-in sessions, curriculum prerequisite cycle protection, and audited semester progression.
- **Responsive Desktop Architecture:** Operates with native desktop performance using Java Swing and FlatLaf, executing all background data operations via `SwingWorker` to prevent UI thread lockups.

---

## 2. Core Features

| Functional Module | Description | Primary Role |
|:---|:---|:---:|
| **Authentication & Security** | BCrypt credential verification, session tracking, mandatory temporary password reset, and brute-force lockout safeguards. | All Users |
| **User & Account Management** | Administrator user directory, automated username formatting (`STU{YEAR}{SEQ}`, `FAC{DEPT}{SEQ}`), and account deactivation. | Admin |
| **Academic Hierarchy & Programmes** | Multi-tier Schools and Departments, qualification levels (`UNDERGRADUATE`, `POSTGRADUATE`, `RESEARCH`, `DIPLOMA`), duration-to-semester calculator, and research milestone tracks. | Admin / Deans |
| **Curriculum & Course Management** | Course catalog, semester mapping with category designations (`CORE`, `ELECTIVE`, `LABORATORY`, etc.), and graph-based circular prerequisite cycle rejection ($A \to B \to A$). | Admin / Faculty |
| **Student Cohort & Progression** | Admission batch management, automated expected graduation year derivation, and rule-bound semester advancement (`PROMOTED`, `HELD`, `REPEATED`) with mandatory audit reasons. | Admin / Registrar |
| **Attendance Automation & OTP** | Faculty-generated timed 6-digit numeric OTP sessions, student self-service check-in with anti-tamper lockouts (max 5 failed attempts per session), and live roster reviews. | Faculty / Student |
| **Timetable Scheduling** | Weekly lecture schedule matrix, classroom allocation, and faculty conflict prevention. | All Users |
| **Finance & Fee Management** | Tuition schedules, fee payment tracking, automatic sequential receipt numbers (`REC-{YEAR}-{SEQ}`), simulated online gateway flow, and dual-entry refund records. | Admin / Student |
| **Executive Reports & Exports** | Analytical dashboard KPI cards, student attendance rosters with low-attendance warnings ($< 75\%$), and syllabus CSV exports. | Admin / Faculty |

---

## 3. Technology Stack & Architecture

- **Language & Runtime:** Java Standard Edition (SE) 17 LTS (Bytecode Major Version 61).
- **Desktop UI Framework:** Java Swing with FlatLaf 3.6 modern look-and-feel.
- **Database Engine:** SQLite 3.54 (Embedded JDBC, zero-configuration local file).
- **Security:** OpenBSD BCrypt 0.4 password hashing.
- **Build System:** Apache Maven 3.8+.
- **Testing:** JUnit 5 (Jupiter 5.10.2).

### Architectural Pattern:
```
Model  ───►  DAO  ───►  Service  ───►  SwingWorker  ───►  Swing UI
```
- **Model:** Serializable domain POJOs representing institutional entities.
- **DAO:** Parameterized SQL queries via JDBC PreparedStatement with explicit connection management.
- **Service:** Business rules, transaction boundaries, input validation, and RBAC authorization.
- **SwingWorker:** Offloads database operations and hashing to background threads, delivering results to the EDT.
- **Swing UI:** Clean, responsive panels and dialogs using custom vector icons (`CampusIcons.java`).

---

## 4. Four-Member Project Team Responsibilities

Campus ERP is maintained as an integrated collegiate software engineering project divided into four specialized functional areas:

```
┌────────────────────────────────────────────────────────────────────────┐
│                      CAMPUS ERP INTEGRATED CODEBASE                    │
├───────────────────┬───────────────────┬──────────────────┬─────────────┤
│     MEMBER 1      │     MEMBER 2      │     MEMBER 3     │  MEMBER 4   │
│   Core System &   │     Academic      │   Attendance &   │  Finance &  │
│  User Management  │    Management     │    Timetable     │ Integration │
└───────────────────┴───────────────────┴──────────────────┴─────────────┘
```

1. **Member 1 — Core System & User Management ([Name]):**
   - Application architecture, startup pipeline (`App.java`), and database initialization.
   - Authentication, session lifecycle, BCrypt hashing, and mandatory first-login password changes.
   - User and role management, student/faculty credential provisioning (`UsernameUtil`, `PasswordUtil`).
2. **Member 2 — Academic Management ([Name]):**
   - Department catalog, school groupings, and leadership assignments.
   - Academic programmes, degree qualification levels, duration calculators, and research milestones.
   - Course catalog, curriculum mappings, prerequisite cycle detection, and student course registrations.
3. **Member 3 — Attendance & Timetable ([Name]):**
   - Timed OTP attendance session creation, 6-digit code generation, and countdown timers.
   - Student self-service check-in dialogs and anti-tamper lockouts.
   - Live faculty check-in review grids, attendance percentage calculations, and weekly timetable scheduling.
4. **Member 4 — Finance, Reporting & Integration ([Name]):**
   - Semester fee schedules, payment recording, and sequential receipt generation.
   - Institutional bank details, UTR verification, and mock payment gateway simulation.
   - Multi-module analytical reports, CSV exports, application shell integration (`MainFrame`), and cross-module QA.
5. **Shared Team Responsibilities:**
   - Architectural reviews, database schema design, automated JUnit regression testing, bug fixing, and final documentation.

*Detailed team module ownership and deliverables are documented in [docs/team-structure.md](docs/team-structure.md).*

---

## 5. System Requirements & Installation

### Prerequisites:
- **Java SE Development Kit (JDK) 17 LTS or higher** (Adoptium Temurin or Oracle JDK).
- **Apache Maven 3.8.0+**.

### Building the Project:
Clone or extract the repository, navigate to the project directory, and compile:
```bash
# 1. Compile source code
mvn compile

# 2. Run all 283 automated regression tests
mvn test

# 3. Package standalone executable Fat JAR
mvn package -DskipTests
```
The executable JAR is generated at `target/CollegeManagementSystem.jar`.

---

## 6. Database Separation & Profiles (Milestone 0.3)

Campus ERP strictly separates environments into four isolated profiles:

| Profile | Storage Location | Synthetic Seeds | Migrations | Purpose |
| :--- | :--- | :--- | :--- | :--- |
| **`STANDARD`** (Default) | `%LOCALAPPDATA%\CampusERP\database\campuserp.db` | **Prohibited** (Clean) | V1–V11 Applied | Standard institutional production database. Admin account created via wizard/CLI. |
| **`DEMO`** | `%LOCALAPPDATA%\CampusERP\demo\campuserp-demo.db` | **Populated** (`seed.sql`) | V1–V11 Applied | Demonstration environment with synthetic Indian academic records. |
| **`TEST`** | `%TEMP%\CampusERP-tests\` | Isolated per suite | V1–V11 Applied | Disposable SQLite instances created exclusively during automated JUnit test runs. |
| **`HISTORICAL`** | `data/college.db` | **Read-Only** | **Prohibited** | Protected Milestone 1 reference baseline (SHA-256 `27F160E79FE67F0054503F42CEEEA79C2D0E929A030F3B5463F00C6DDF0B8346`). |

---

## 7. Application Startup & Execution

### Option A: Using the Launcher Scripts (Recommended)
- **Standard Platform (Windows):** Run `run-campus-erp.bat` (or `scripts\run-campus-erp.bat`)
- **Demo Platform (Windows):** Run `scripts\run-campus-erp-demo.bat`
- **Unix / Linux / macOS:** Run `scripts/run-campus-erp.sh`

### Option B: Direct Java CLI Commands
```bash
# Launch Standard Institutional Profile (Default)
java -jar target/CollegeManagementSystem.jar --db-mode=standard

# Launch Demonstration Profile (with Synthetic Seed Data)
java -jar target/CollegeManagementSystem.jar --db-mode=demo

# Initialize Clean Schema Only (Headless)
java -jar target/CollegeManagementSystem.jar --init-schema-only

# Provision Administrator Account Interactively (Headless)
java -jar target/CollegeManagementSystem.jar --setup-admin admin
```

### Secure Account Provisioning:
Campus ERP enforces a zero-default-password credential policy. All accounts are provisioned via secure workflows:
1. **Initial Administrator:** When starting with a fresh database, `LoginFrame` automatically prompts for initial administrator account creation via `FirstRunAdminSetupDialog`, or via the headless CLI:
   ```bash
   java -jar target/CollegeManagementSystem.jar --setup-admin <username>
   ```
2. **Faculty & Student Accounts:** Administrators provision personnel and student accounts with cryptographically random temporary passwords via **User Management** or profile creation forms (`createStudentWithAutoAccount` / `createFacultyWithAutoAccount`).
3. **Mandatory First-Login Password Reset:** All newly provisioned accounts require a mandatory personal password update upon first sign-in (`must_change_password=1`).

---

## 7. Database Information

- **Database Engine:** Pure SQLite 3.54 (file-based).
- **Production Database:** `data/college.db` (SHA-256: `27f160e79fe67f0054503f42ceeea79c2d0e929a030f3b5463f00c6ddf0b8346`).
- **Migration Mechanism:** Fully automated, forward-only additive migrations managed by `DatabaseInitializer.java` (Migrations `V1` through `V11`). All migrations are 100% idempotent and ensure zero data loss.

---

## 8. Technical Documentation Directory

Comprehensive technical documentation is organized within the `docs/` folder:
- [**Project Overview**](docs/project-overview.md) — Problem statement, objectives, scope, and user personas.
- [**Architecture Manual**](docs/architecture.md) — 5-tier architecture, concurrency model, and execution sequences.
- [**Database Design**](docs/database-design.md) — ER diagram, table schemas, paise-based accounting, and migration rules.
- [**Developer Setup**](docs/developer-setup.md) — JDK configuration, Maven setup, compilation, and CLI options.
- [**User Manual**](docs/user-manual.md) — Step-by-step operational guide for Admins, Faculty, and Students.
- [**Testing Manual**](docs/testing.md) — Test categories, JUnit 5 instructions, and acceptance boundaries.
- [**Team Structure**](docs/team-structure.md) — Detailed four-member responsibility matrix and shared duties.
- [**Project Stabilization Report**](docs/project-stabilization-report.md) — Final comprehensive Milestone 4 stabilization report and acceptance verdict.

---

## 9. Quality Assurance & Testing

The system includes 52 automated test classes covering 283 test scenarios with a 100% pass rate:
```bash
mvn test
```
All test executions run against isolated in-memory or disposable file databases in `target/test-dbs/`.

---

## 10. License & Academic Attribution
Developed as an undergraduate collegiate Software Engineering Capstone Project.
Licensed under the [MIT License](LICENSE).
