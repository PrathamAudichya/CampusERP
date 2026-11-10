# Campus ERP — Local Database Directory

This directory is designated for local runtime SQLite database files.

## 1. Local Database Storage Policy
- **Embedded Database:** Campus ERP uses an embedded SQLite database engine. By default, runtime database files (such as `college.db`, `college-demo.db`, and SQLite write-ahead log files `*-wal` / `*-shm`) reside in this directory.
- **Git Exclusion:** All `.db`, `.sqlite`, `.sqlite3`, `.db-wal`, and `.db-shm` files are strictly excluded from Git version control via `.gitignore`. The database represents mutable local state, personal data, and operational runtime logs, which must never be committed to source repositories.

## 2. Automatic Database Initialization
When Campus ERP is launched against an absent or empty database file:
1. `DBConnection` automatically creates the SQLite database file with WAL journaling (`journal_mode = WAL`) and synchronous mode (`synchronous = NORMAL`).
2. `DatabaseInitializer` executes `schema.sql` (14 relational tables, 8 integrity triggers, and 44 performance indexes) within an immediate transaction.
3. Additive forward-only migrations (V1 through V11) are automatically validated and applied.
4. On first launch, the administrator account is provisioned via the interactive CLI (`--setup-admin <username>`) or first-run GUI setup wizard.

## 3. Developer & Testing Databases
- To run against an isolated development or demonstration database without affecting production data:
  ```bash
  java "-Dcollege.db.path=data/college-dev.db" -jar target/college-management-system-1.0.0.jar
  ```
- Automated JUnit test suites automatically execute against in-memory or ephemeral disposable databases (`target/test-dbs/`) and clean up upon completion.
