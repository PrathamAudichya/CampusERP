# Campus ERP — Local Database Directory & Profile Separation

This directory houses local runtime database baselines and metadata for Campus ERP.

## 1. Database Separation Architecture (Milestone 0.3)

Campus ERP strictly isolates data across four distinct profiles to guarantee that demonstration, test, and development activities never contaminate production records.

| Profile | Target File Path | Synthetic Seeds | Migrations | Description |
| :--- | :--- | :--- | :--- | :--- |
| **`STANDARD`** (Default) | `%LOCALAPPDATA%\CampusERP\database\campuserp.db` | **Prohibited** (Clean) | V1–V11 Applied | Standard institutional production database. Administrator account is created via interactive setup wizard or CLI. |
| **`DEMO`** | `%LOCALAPPDATA%\CampusERP\demo\campuserp-demo.db` | **Populated** (`seed.sql`) | V1–V11 Applied | Isolated demonstration environment populated with synthetic Indian academic records. |
| **`TEST`** | `%TEMP%\CampusERP-tests\` | Isolated per suite | V1–V11 Applied | Disposable SQLite instances created exclusively during automated JUnit test suite runs. |
| **`HISTORICAL`** | `data/college.db` | **Read-Only** | **Prohibited** | Protected Milestone 1 historical snapshot (SHA-256 `27F160E79FE67F0054503F42CEEEA79C2D0E929A030F3B5463F00C6DDF0B8346`). Never modified or migrated. |

## 2. Protected Historical Baseline (`data/college.db`)
- `data/college.db` is the verified Milestone 1 reference database.
- It is cryptographically locked with SHA-256 hash `27F160E79FE67F0054503F42CEEEA79C2D0E929A030F3B5463F00C6DDF0B8346`.
- The application will **refuse** to run migrations or seed scripts against this database.

## 3. Git Exclusion Policy
All `.db`, `.sqlite`, `.sqlite3`, `.db-wal`, `.db-shm`, and `.bak` files are strictly excluded from Git version control via `.gitignore`.
Runtime databases represent local state, personal information, and operational logs, which must never be committed to source repositories.

## 4. Inspection via DB Browser for SQLite
- **STANDARD Database:** Open `%LOCALAPPDATA%\CampusERP\database\campuserp.db`
- **DEMO Database:** Open `%LOCALAPPDATA%\CampusERP\demo\campuserp-demo.db`
- In DB Browser, press **F5** to reload committed transactions under SQLite WAL mode.
