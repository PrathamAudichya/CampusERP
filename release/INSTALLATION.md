# Campus ERP v2.0.9 — Installation & Deployment Guide

**Version:** 2.0.9 (User Directory, Credential Management, Attendance & Timetable Candidate)  
**Target Platform:** Java 17+ Standard Edition (OpenJDK / Oracle JDK)  
**Artifact:** `release/CampusERP-v2.0.9-candidate.jar`  
**Artifact Size:** 13,611,834 bytes  
**SHA-256 Digest:** `aae810dfd2b48f2340b446045a1a935464c686445154b021e4f2b3161d9972e5`  
**Architecture:** Desktop Java Swing Client with FlatLaf 3.6, Embedded SQLite 3.54.1, Pure JDBC, Responsive Directory Architecture, Timetable Conflict Engine, and Warm Institutional Brand Design System  

---

## 1. Prerequisites

### 1.1 Verify Java Installation
Open a command prompt (Windows PowerShell / Command Prompt) or terminal (Linux / macOS) and verify that Java 17 or higher is installed:

```bash
java -version
```

**Expected Output:**
Java version 17 or higher (e.g., `openjdk version "17.0.x"` or `java version "21.0.x"` / `26.0.x`).  
If `java` is not recognized, install OpenJDK 17 or higher and ensure the `bin/` directory is added to your system `PATH`.

---

## 2. Windows Quick Start (Application Launcher)

For Windows environments, the simplest and safest way to launch Campus ERP is via `run-campus-erp.bat`:

1. Double-click `run-campus-erp.bat` in the project root directory, or run from the command line:
   ```cmd
   run-campus-erp.bat
   ```
2. The launcher automatically:
   - Verifies the availability of Java 17+ on your system PATH.
   - Verifies the presence of `release\CampusERP-v2.0.9-candidate.jar`.
   - Displays an interactive menu for database selection:
     - **1. Launch Demo Database [DEFAULT]:** Connects to isolated demonstration database at `%LOCALAPPDATA%\CampusERP\demo\college-demo.db`.
     - **2. Launch Production Database:** Requires typed confirmation `OPEN PRODUCTION DATABASE` before connecting to `data\college.db`.
     - **3. Exit:** Closes cleanly.
   - Enforces UTF-8 console output and quotes all system paths containing spaces.

---

## 3. Deployment Procedures & Operational Modes

Campus ERP supports three distinct operational database modes:

```
Operational Database Modes
 ├── Mode A: Clean Institutional Production (Schema only, zero demo accounts, interactive admin setup)
 ├── Mode B: Demonstration & Training (Automatic schema + seed.sql academic fixtures, GUI admin wizard)
 └── Mode C: Existing Production Database (Connect to existing populated database without re-initialization)
```

> [!CAUTION]
> **Critical Database Safety Rules:**
> 1. The target path specified in `-Dcollege.db.path` for a clean install MUST refer to an **explicitly NEW database file** (e.g. `data/college-clean-install.db`).
> 2. **NEVER** use the path of an existing populated production database (such as `data/college.db`) for clean initialization experiments.
> 3. `--init-schema-only` is an initialization command for unpopulated databases; it is **NOT** a database reset, migration, or wipe utility.
> 4. The clean initialization result of 13 tables, 38 indexes, 4 triggers, and 0 users applies strictly to newly created, empty database files.
> 5. Existing production data is NOT automatically migrated; existing database migrations and offline backups must be managed separately.
> 6. The JVM system property `-Dcollege.db.path` MUST appear before `-jar`.

---

### Mode A: Clean Production Deployment (Zero Demo Accounts)

For institutional production deployment where demonstration accounts and test fixtures must not exist in the database:

1. **Initialize Clean Database Schema on a NEW Database File:**
   Executes DDL statements (`schema.sql`) creating all 13 tables, 38 indexes, and 4 triggers with exactly 0 initial users and 0 demo records:
   ```bash
   java "-Dcollege.db.path=data/college-clean-install.db" -jar release/CampusERP-v2.0.9-candidate.jar --init-schema-only
   ```
   *Expected Output:*
   ```text
   ============================================================
   CAMPUS ERP v2.0.9 - SCHEMA INITIALIZATION
   ============================================================
   Database path: data/college-clean-install.db
   [SUCCESS] Clean database schema initialized successfully.
   [INFO] Tables created: 14, Indexes created: 40.
   [INFO] Zero demonstration accounts or seed data inserted.
   ============================================================
   ```

2. **Provision Primary Administrator Account (Separate Step):**
   Execute the secure headless provisioning command (no password in command-line arguments):
   ```bash
   java "-Dcollege.db.path=data/college-clean-install.db" -jar release/CampusERP-v2.0.9-candidate.jar --setup-admin admin
   ```
   The application will securely prompt for the administrator password and password confirmation without echoing characters to the terminal:
   ```text
   Enter password for admin: [MASKED]
   Confirm password: [MASKED]
   [SUCCESS] Initial administrator 'admin' provisioned successfully.
   ```
   > [!IMPORTANT]
   > Supplying passwords via command-line arguments (e.g. `--setup-admin <user> <pass>`) is strictly prohibited and rejected with exit code 1 to prevent credential leakage in process listings (`ps -ef`) and terminal history.

3. **Launch Campus ERP Desktop Application:**
   ```bash
   java "-Dcollege.db.path=data/college-clean-install.db" -jar release/CampusERP-v2.0.9-candidate.jar
   ```

---

### Mode B: Demonstration / Evaluation Deployment

For local testing, student evaluation, or training:

1. **Launch Directly Against an Empty Database Path:**
   ```bash
   java "-Dcollege.db.path=data/campus-erp-demo.db" -jar release/CampusERP-v2.0.9-candidate.jar
   ```
   - When launched against an absent or empty database without `--init-schema-only`, the system automatically provisions the schema and populates demonstration academic records (`seed.sql`).
   - Demonstration accounts: Student (`ajones`), Faculty (`jdoe`). Initial temporary passwords must be provisioned or reset by the administrator via User Management, requiring mandatory password update upon first login.
   - **No Default Admin Secret:** The demonstration seed data contains no default administrator account.

2. **First-Run Administrator Setup Wizard (GUI):**
   - On the first GUI launch, the application detects that no administrator account exists.
   - A modal setup wizard will prompt you to enter the administrator username, full name, email address, and a strong password.
   - Once saved, the application safely returns to the unified login screen without auto-authenticating into the admin dashboard.

---

### Mode C: Connecting to an Existing Production Database

To connect the application to an existing populated database:

```bash
java "-Dcollege.db.path=data/college.db" -jar release/CampusERP-v2.0.9-candidate.jar
```
- The application automatically connects to the configured database, validates existing tables, detects active accounts, and presents the unified Login screen.
- Existing user accounts and administrator credentials remain completely untouched.

---

## 4. Database Location & Customization

Campus ERP uses an embedded SQLite database engine (bundled version: **SQLite 3.54.1**).

- **Default Location:** `data/college.db` (relative to the current execution directory). The application automatically creates the `data/` directory if needed.
- **Custom Location:** Set the JVM system property `-Dcollege.db.path` to any absolute or relative path:
   ```bash
   # Windows Example
   java "-Dcollege.db.path=C:\ERP-Data\college.db" -jar release/CampusERP-v2.0.9-candidate.jar

   # Linux / macOS Example
   java "-Dcollege.db.path=/var/lib/campus-erp/college.db" -jar release/CampusERP-v2.0.9-candidate.jar
   ```

---

## 5. Password Policy Requirements

All user passwords must satisfy institutional complexity standards:
- Minimum length: **8 characters**
- At least one uppercase letter: `[A-Z]`
- At least one lowercase letter: `[a-z]`
- At least one numeric digit: `[0-9]`
- At least one special character: `[!@#$%^&*()_+\-=\[\]{}|;:,.<>?]`

Passwords are encrypted with salted BCrypt (Work Factor 12, 4096 rounds). Plaintext passwords and hashes are never exposed in user objects or session models.

---

## 6. Troubleshooting & FAQ

### Issue: "Supplying passwords via command-line arguments is strictly prohibited"
- **Cause:** A password argument was appended to the `--setup-admin` command.
- **Solution:** Execute `java -jar ... --setup-admin <username>` with only the username. The utility will prompt interactively for the password with masked input.

### Issue: "No interactive console available"
- **Cause:** The `--setup-admin` CLI command was run in a non-interactive CI/CD pipe or automated subshell where `java.io.Console` is unavailable.
- **Solution:** Run the command in an interactive terminal (PowerShell, Command Prompt, bash) or launch the application in GUI mode to use the graphical setup dialog.

### Issue: "No suitable driver found for jdbc:sqlite:..."
- **Cause:** Running an unshaded or stripped JAR.
- **Solution:** Ensure you are running the official shaded executable JAR (`CampusERP-v2.0.9-candidate.jar` or `target/CollegeManagementSystem.jar`), which packages the SQLite JDBC driver and native binaries.

### Issue: "Failed to create database directory"
- **Cause:** Insufficient write permissions in the target directory.
- **Solution:** Ensure the operating system user account has read and write permissions to the database directory specified in `-Dcollege.db.path`.

### Issue: "A restricted method in java.lang.System has been called"
- **Cause:** Standard Java 21+ / 24+ / 26+ JVM warning regarding JNI loading in native libraries by `SQLiteJDBCLoader`.
- **Solution:** This warning is informational and does not affect application functionality. You may optionally suppress it on newer JDKs using `--enable-native-access=ALL-UNNAMED`.

---

## 7. Cryptographic Ledger & Release Artifacts

| Component | Filesystem Path | SHA-256 Digest | Status |
| :--- | :--- | :--- | :--- |
| **Release Candidate v2.0.9 (Directory, Credentials, Attendance, Timetable)** | `release/CampusERP-v2.0.9-candidate.jar` | `aae810dfd2b48f2340b446045a1a935464c686445154b021e4f2b3161d9972e5` | **ACTIVE CANDIDATE** |
| **Release Candidate v2.0.8 (Functional Automation)** | `release/CampusERP-v2.0.8-functional-improvement-candidate.jar` | `fcbba48a0f1e9feea729a143c5c2d7812cc27649a66d0c20982e7e3a21e14aba` | **HISTORICAL (PRESERVED)** |
| **Release Candidate v2.0.7 (Directory UI)** | `release/CampusERP-v2.0.7-directory-ui-candidate.jar` | `da358b3e83caf49e51fc3e9245fba81da05e7cce84216d36d21e1add5ef371fd` | **HISTORICAL (PRESERVED)** |
| **Release Candidate v2.0.6 (Credentials)** | `release/CampusERP-v2.0.6-student-credentials-candidate.jar` | `c8ed0a2d048201c2684a07ea117014055430ed3f90641ccd45495dc40f11e5d0` | **HISTORICAL (PRESERVED)** |
| **Release Candidate v2.0.5 (Brand & UI)** | `release/CampusERP-v2.0.5-ui-refinement-candidate.jar` | `cf9160aaff04c2f0ce3ce649ae5af77902ebe389498c89cd87aa775f1f4dd392` | **HISTORICAL (PRESERVED)** |
| **Release Candidate v2.0.4 (UI)** | `release/CampusERP-v2.0.4-ui-candidate.jar` | `f2e40cd25189fbdb1bbf529490b0a4d81be25eff1458580660078cfd51154456` | **HISTORICAL (PRESERVED)** |
| **Hardened Candidate v2.0.3** | `release/CampusERP-v2.0.3-candidate.jar` | `2eadc73174cf6840321fbafe9b0ade7f36927dc1afab5717911d36b254bc73a9` | **HISTORICAL (PRESERVED)** |
| **Hardened Candidate v2.0.2** | `release/CampusERP-v2.0.2-candidate.jar` | `db7d1266b15f357202a8bd38b748727b5ec662f0cb7b9a390b362fe098eb543d` | **HISTORICAL (PRESERVED)** |
| **Hardened Candidate v2.0.1** | `release/CampusERP-v2.0.1-candidate.jar` | `4d864c7f76045ab4a704c61d5d9ee2dbe8109f48806b890f48e86c748f0fbd4a` | **HISTORICAL (PRESERVED)** |
| **Accepted Candidate v2.0** | `release/CampusERP-v2.0-candidate.jar` | `2ad92f59c5ce0310722b9c3e6f4651e0c6d52b2f7f5d0958583cb076a085530c` | **HISTORICAL (PRESERVED)** |
| **Baseline Release JAR** | `release/CollegeManagementSystem.jar` | `502e5dd0b86638e1ff6ce9fe39867bc3ce9c8324b39543ddfe85f09366702fb5` | **HISTORICAL (PRESERVED)** |
| **Live Production DB** | `data/college.db` | `27f160e79fe67f0054503f42ceeea79c2d0e929a030f3b5463f00c6ddf0b8346` | **PRESERVED** |
| **Baseline Backup DB** | `backups/v2_0_baseline_backup/data/college.db` | `27f160e79fe67f0054503f42ceeea79c2d0e929a030f3b5463f00c6ddf0b8346` | **PRESERVED** |
