# College Management System — Database Backup & Recovery Procedures

**Database Engine:** SQLite 3 (Pure JDBC, WAL Mode)  
**Default Database Path:** `data/college.db`  
**Write-Ahead Log Files:** `data/college.db-wal`, `data/college.db-shm`

---

## 1. Important Warning Regarding SQLite File Backups

> [!WARNING]
> **DO NOT COPY OR BACK UP THE DATABASE WHILE THE APPLICATION IS RUNNING!**
> 
> The College Management System operates SQLite in **Write-Ahead Logging (WAL) Mode** (`PRAGMA journal_mode=WAL`).
> In WAL mode:
> - Recent transactions may reside inside the write-ahead log file (`college.db-wal`) rather than the primary database file (`college.db`).
> - Copying `college.db` alone while a writer or reader connection is open will produce a **corrupt or inconsistent snapshot**.
> - Furthermore, copying an active WAL file while another process writes to it can cause torn pages.

---

## 2. Standard Safe Offline Backup Procedure (Recommended)

1. **Stop Application:**
   Ensure all running instances of `CollegeManagementSystem.jar` are closed cleanly.
2. **Verify Process Termination:**
   Confirm no Java process is holding file locks on `data/college.db`.
3. **Copy All Database Files:**
   When the database closes cleanly, SQLite automatically checkpoints and flushes transactions. Copy the entire database directory:
   ```powershell
   # Windows PowerShell Example
   Copy-Item -Path "data\college.db*" -Destination "C:\Backups\college_backup_$(Get-Date -Format 'yyyyMMdd_HHmmss')\"
   ```
4. **Archive:**
   Compress the destination backup directory into a timestamped ZIP or TAR archive.

---

## 3. Online Backup Using SQLite CLI (Hot Backup)

If you require a live backup without stopping the desktop application, use the official SQLite Command Line Shell (`sqlite3`):

```bash
sqlite3 data/college.db ".backup 'C:/Backups/college_live_backup.db'"
```

The SQLite `.backup` API safely coordinates with active WAL reader and writer transactions to produce a consistent, self-contained snapshot with zero data loss.

---

## 4. Restoration Procedure

1. **Shut Down Application:**
   Stop the application completely before restoring.
2. **Archive Current State:**
   Move the damaged or existing `data/college.db`, `data/college.db-wal`, and `data/college.db-shm` into a quarantine folder (`data/corrupted_archive/`).
3. **Restore Backup:**
   Copy the backup file into the target database location:
   ```powershell
   Copy-Item -Path "C:\Backups\college_live_backup.db" -Destination "data\college.db"
   ```
4. **Verify Integrity:**
   You can verify database integrity prior to launching the system:
   ```bash
   sqlite3 data/college.db "PRAGMA integrity_check;"
   # Expected Output: ok
   ```
5. **Start Application:**
   Launch `java -jar CollegeManagementSystem.jar`. The application will detect the existing database, retain all data, and resume normal operations.

