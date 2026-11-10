# College Management System (CMS) — v1.0.0

A desktop-based enterprise College Management System built with Java 17, Java Swing, and SQLite using pure JDBC.

---

## 1. Overview & Architectural Highlights

- **Desktop Presentation Layer:** Java Swing with system Look & Feel, background asynchronous data processing via `SwingWorker`, strict Event Dispatch Thread (EDT) compliance, and custom dialogs.
- **Service Layer Architecture:** Model → DAO → Service → SwingWorker → UI Panel with strict role-based authorization (`ADMIN`, `FACULTY`, `STUDENT`) and two-table atomic transactional operations.
- **Embedded Persistence:** Pure SQLite JDBC in Write-Ahead Logging (WAL) mode with `IMMEDIATE` transaction acquisition, positive foreign key enforcement (`PRAGMA foreign_keys = ON`), and immutable audit logging.
- **Financial Rigor:** Strict integer-paise accounting preventing arithmetic overflow, subtraction-first fee balance evaluation, refund ledger validation, and concurrent payment protection.
- **Security & Cryptography:** Salted BCrypt password hashing, mandatory forced-password-change workflow on initial login (SEC-08), and comprehensive audit trail logging.

---

## 2. System Requirements

- **Operating System:** Windows 10/11, Linux (X11/Wayland), or macOS.
- **Java Runtime:** Java 17 or later (JRE/JDK 17+).
  - *Note on Qualification:* Built with bytecode target `--release 17` and tested on Java runtime.
- **Disk Space:** ~50 MB for application JAR and initial SQLite database.
- **Display Resolution:** Minimum 1024x768 recommended for Swing dialogs and tabular reporting.

---

## 3. Quick Launch Instructions

Launch the standalone executable JAR from any terminal or command prompt:

```bash
java -jar CollegeManagementSystem.jar
```

To specify a custom database location:

```bash
java -Dcollege.db.path="C:\MyData\college.db" -jar CollegeManagementSystem.jar
```

On first launch, the system automatically creates the database file (defaulting to `data/college.db` in the working directory), executes the schema DDL, and loads default administrative seed accounts.

---

## 4. Accounts & Roles (Demonstration Seed Data)

Campus ERP enforces a zero-default-password credential policy. On first launch, the administrator account is provisioned interactively via CLI (`--setup-admin <username>`) or the GUI first-run setup wizard.

| Username | Credential Provisioning | Role | Note |
|---|---|:---:|---|
| *Administrator* | Interactive Setup Wizard / CLI | `ADMIN` | Initial master administrator |
| `jdoe` | Generated Temporary Password | `FACULTY` | Computer Science Faculty (must change password) |
| `rjohnson` | Generated Temporary Password | `FACULTY` | Electronics & Comm Faculty (must change password) |
| `ssmith` | Generated Temporary Password | `STUDENT` | Computer Science Student (must change password) |
| `ajones` | Generated Temporary Password | `STUDENT` | Electronics Student (must change password) |
| `mbrown` | Generated Temporary Password | `STUDENT` | Computer Science Student (must change password) |

---

## 5. Included Documentation in this Release

- [`INSTALLATION.md`](INSTALLATION.md): Detailed environment setup, prerequisite checks, and launch troubleshooting.
- [`USER_GUIDE.md`](USER_GUIDE.md): Operational instructions for Administrators, Faculty, and Students.
- [`DATABASE_BACKUP.md`](DATABASE_BACKUP.md): Safe live-backup procedures, restoration guides, and SQLite file locking notes.
- [`RELEASE_NOTES.md`](RELEASE_NOTES.md): Version history, verified capabilities, and technical qualifications.
- [`SHA256SUMS.txt`](SHA256SUMS.txt): Cryptographic verification checksums for release artifacts.

---

## 6. Binary Distribution Policy

Executable application binaries (`*.jar`) are excluded from Git version tracking per security and repository best practices. Formal release binaries are distributed exclusively through GitHub Releases following official tag creation and release candidate verification.

