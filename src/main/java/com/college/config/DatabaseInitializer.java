package com.college.config;

import com.college.exception.DataAccessException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrates database schema initialization, trigger creation, and seed data loading.
 *
 * Reads SQL scripts from the classpath resources directory:
 * - schema.sql: DDL for 13 tables, 38 indexes, and 4 triggers
 * - seed.sql: DML for default users and seed data
 *
 * Provides two initialization modes:
 * 1. initializeWithConnection(Connection) — uses an existing connection (for testing with shared-memory DBs)
 * 2. initialize() — creates a new connection via DBConnection and initializes the database
 *
 * Handles multi-statement DDL/DML execution with trigger-awareness (preserving BEGIN...END blocks)
 * and atomic transaction management with rollback.
 *
 * NOTE: SQL resource files (schema.sql, seed.sql) are not created until Phase 2.
 * This class is structurally complete but will not succeed at runtime until those
 * resources exist on the classpath.
 */
public class DatabaseInitializer {

    private static final String SCHEMA_RESOURCE = "/schema.sql";
    private static final String SEED_RESOURCE = "/seed.sql";

    private DatabaseInitializer() {
        // Utility class — prevent instantiation
    }

    /**
     * Initializes the database schema and seed data using the provided connection.
     * Executes schema.sql first (tables, indexes, triggers), then seed.sql (default data).
     *
     * Transaction Ownership & Safety Contract:
     * - The caller is expected to provide a connection with autoCommit=true so that DatabaseInitializer
     *   can establish and manage its own atomic transaction boundary.
     * - If the supplied connection already has autoCommit=false (indicating an active, caller-managed
     *   transaction), this method immediately rejects the connection with DataAccessException to avoid
     *   prematurely committing or rolling back caller-owned work.
     * - Manages the transaction boundary atomically: sets autoCommit to false, executes both scripts,
     *   commits upon success, rolls back upon expected failures (SQLException or RuntimeException, with
     *   suppressed exception handling), and restores autoCommit to true ONLY when rollback succeeds or on clean completion.
     * - Rollback Failure Safety Policy:
     *   In JDBC, invoking setAutoCommit(true) on a connection with an uncommitted transaction can trigger
     *   an implicit commit of dirty, unrolled-back state. If conn.rollback() throws an exception,
     *   DatabaseInitializer strictly adheres to fail-safe recovery: it NEVER calls conn.setAutoCommit(true).
     *   The original failure is preserved, the rollback failure is attached as a suppressed exception,
     *   and the connection is left in an uncommitted state (autoCommit=false).
     *   The caller retains lifecycle ownership and is responsible for closing/discarding the compromised connection.
     * - JVM Error Handling & Transaction Cleanup Policy:
     *   A dedicated catch (Error) block intercepts fatal JVM conditions during SQL execution.
     *   It attempts conn.rollback() prior to rethrowing the original Error. If rollback fails,
     *   the rollback failure is attached as suppressed to the original Error. The Error is never
     *   converted into DataAccessException. AutoCommit is restored to true ONLY if rollback succeeds;
     *   if rollback fails, autoCommit restoration is strictly bypassed.
     *   Connection lifecycle ownership is preserved; the connection is not closed.
     * - Auto-Commit Restoration Failure:
     *   If conn.rollback() succeeds but conn.setAutoCommit(true) fails, the restoration exception is attached
     *   as suppressed onto the root failure (or thrown as DataAccessException if execution succeeded).
     *   The rollback remains intact.
     * - Does NOT close the connection — caller retains full connection lifecycle ownership.
     *
     * @param conn an open JDBC connection to the target database with autoCommit=true
     * @throws IllegalArgumentException if conn is null
     * @throws DataAccessException if conn has autoCommit=false, if SQL resources cannot be found,
     *                             or if SQL execution/rollback/restoration fails
     */
    public static void initializeWithConnection(Connection conn) {
        initializeInternal(conn, true);
    }

    /**
     * Initializes only the database schema (tables, indexes, triggers) without loading demonstration seed data.
     * Suitable for clean production deployments.
     *
     * @param conn an open JDBC connection to the target database with autoCommit=true
     * @throws IllegalArgumentException if conn is null
     * @throws DataAccessException if execution fails
     */
    public static void initializeSchemaOnly(Connection conn) {
        initializeInternal(conn, false);
    }

    private static void initializeInternal(Connection conn, boolean includeSeedData) {
        if (conn == null) {
            throw new IllegalArgumentException("Connection cannot be null.");
        }

        try {
            if (!conn.getAutoCommit()) {
                throw new DataAccessException(
                    "Cannot initialize database on a connection with an active, caller-managed transaction (autoCommit=false). " +
                    "Provide a connection with autoCommit=true so that DatabaseInitializer can manage its own atomic transaction boundary.");
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to inspect connection autoCommit status: " + e.getMessage(), e);
        }

        try {
            conn.setAutoCommit(false);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to begin initialization transaction: " + e.getMessage(), e);
        }

        Exception failure = null;
        Error errorFailure = null;
        boolean rollbackSucceeded = false;
        boolean success = false;

        try {
            applyAdditiveMigrations(conn);
            executeSqlResource(conn, SCHEMA_RESOURCE);
            applyAdditiveMigrations(conn);
            if (includeSeedData) {
                executeSqlResource(conn, SEED_RESOURCE);
            }
            conn.commit();
            success = true;
        } catch (SQLException | RuntimeException ex) {
            failure = ex;
            try {
                conn.rollback();
                rollbackSucceeded = true;
            } catch (SQLException | RuntimeException rollbackEx) {
                failure.addSuppressed(rollbackEx);
            } catch (Error rollbackErr) {
                // If a fatal JVM Error occurs during rollback, preserve the Error,
                // attach the original failure as suppressed, and ensure the Error propagates.
                rollbackErr.addSuppressed(failure);
                errorFailure = rollbackErr;
            }
        } catch (Error ex) {
            errorFailure = ex;
            try {
                conn.rollback();
                rollbackSucceeded = true;
            } catch (Throwable rollbackFailure) {
                errorFailure.addSuppressed(rollbackFailure);
            }
        } finally {
            // Restore autoCommit=true ONLY if commit or rollback has succeeded.
            // If rollback failed, rollbackSucceeded is false, so autoCommit restoration
            // is strictly bypassed, leaving the connection uncommitted and preventing
            // accidental commits of dirty state.
            if (success || rollbackSucceeded) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException restoreEx) {
                    if (errorFailure != null) {
                        errorFailure.addSuppressed(restoreEx);
                    } else if (failure != null) {
                        failure.addSuppressed(restoreEx);
                    } else {
                        failure = new DataAccessException(
                            "Failed to restore connection autoCommit to true: " + restoreEx.getMessage(), restoreEx);
                    }
                } catch (Error restoreErr) {
                    if (errorFailure != null) {
                        errorFailure.addSuppressed(restoreErr);
                    } else if (failure != null) {
                        failure.addSuppressed(restoreErr);
                    } else {
                        errorFailure = restoreErr;
                    }
                }
            }
        }

        if (errorFailure != null) {
            throw errorFailure;
        }

        if (failure != null) {
            if (failure instanceof DataAccessException) {
                throw (DataAccessException) failure;
            }
            throw new DataAccessException("Database initialization failed: " + failure.getMessage(), failure);
        }
    }

    /**
     * Initializes only the database schema (tables, indexes, triggers) without loading demonstration seed data.
     * Uses a managed connection from DBConnection.
     *
     * @throws DataAccessException if connection fails or SQL execution fails
     */
    public static void initializeSchemaOnly() {
        try (Connection conn = DBConnection.getConnection()) {
            initializeSchemaOnly(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to initialize schema: " + e.getMessage(), e);
        }
    }

    /**
     * Initializes the database by creating a new connection via DBConnection
     * and executing the schema and seed SQL scripts in a transaction.
     *
     * The connection is automatically closed after initialization completes or fails.
     *
     * @throws DataAccessException if the connection cannot be established or SQL execution fails
     */
    public static void initialize() {
        try (Connection conn = DBConnection.getConnection()) {
            initializeWithConnection(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Failed to initialize database: " + e.getMessage(), e);
        }
    }

    /**
     * Reads a SQL resource file from the classpath and executes its contents
     * as discrete SQL statements, preserving multi-line trigger definitions.
     *
     * @param conn         the database connection to execute against
     * @param resourcePath the classpath resource path (e.g., "/schema.sql")
     * @throws DataAccessException if the resource is not found or SQL execution fails
     */
    private static void executeSqlResource(Connection conn, String resourcePath) {
        String sql = loadResourceAsString(resourcePath);
        if (sql.isBlank()) {
            return;
        }

        List<String> statements = splitSqlStatements(sql);
        try (Statement stmt = conn.createStatement()) {
            for (String statementSql : statements) {
                if (!statementSql.isEmpty()) {
                    stmt.execute(statementSql);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException(
                "Failed to execute SQL resource " + resourcePath + ": " + e.getMessage(), e);
        }
    }

    /**
     * Splits a SQL script into individual executable statements.
     *
     * Handles:
     * - Multi-line statements (e.g. CREATE TABLE, INSERT INTO)
     * - Multi-line trigger blocks with full keyword variant support:
     *   - CREATE TRIGGER ... BEGIN ... END;
     *   - CREATE TEMP TRIGGER ... BEGIN ... END;
     *   - CREATE TEMPORARY TRIGGER ... BEGIN ... END;
     *   - Optional IF NOT EXISTS clause
     *   - Line comments (-- ...) and block comments (/* ... * /) between declaration keywords
     * - Nested CASE ... END expressions inside trigger bodies (distinguishing CASE END from TRIGGER END)
     * - Semicolons inside trigger bodies (preserved until trigger END;)
     * - String literals ('...' with escaped '')
     * - Quoted identifiers ("...", `...`, [...])
     * - Comment preservation / token separation (preventing token concatenation across comments)
     *
     * @param sql the raw SQL script string
     * @return list of individual executable SQL statements
     */
    public static List<String> splitSqlStatements(String sql) {
        if (sql == null || sql.isBlank()) {
            return new ArrayList<>();
        }

        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        boolean inBacktick = false;
        boolean inBracket = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;

        ParserState state = new ParserState();
        StringBuilder currentWord = new StringBuilder();

        int len = sql.length();
        for (int i = 0; i < len; i++) {
            char c = sql.charAt(i);
            char next = (i + 1 < len) ? sql.charAt(i + 1) : '\0';

            // 1. Line comment handling
            if (inLineComment) {
                if (c == '\n' || c == '\r') {
                    inLineComment = false;
                    if (current.length() > 0 && current.charAt(current.length() - 1) != '\n') {
                        current.append('\n');
                    }
                }
                continue;
            }

            // 2. Block comment handling
            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    i++; // skip '/'
                    if (current.length() > 0 && !Character.isWhitespace(current.charAt(current.length() - 1))) {
                        current.append(' ');
                    }
                }
                continue;
            }

            // 3. String literal handling ('...' with escaped '')
            if (inSingleQuote) {
                current.append(c);
                if (c == '\'') {
                    if (next == '\'') {
                        current.append(next);
                        i++; // skip escaped quote
                    } else {
                        inSingleQuote = false;
                    }
                }
                continue;
            }

            // 4. Quoted identifier handling ("...", `...`, [...])
            if (inDoubleQuote) {
                current.append(c);
                if (c == '"') {
                    if (next == '"') {
                        current.append(next);
                        i++;
                    } else {
                        inDoubleQuote = false;
                    }
                }
                continue;
            }

            if (inBacktick) {
                current.append(c);
                if (c == '`') {
                    inBacktick = false;
                }
                continue;
            }

            if (inBracket) {
                current.append(c);
                if (c == ']') {
                    inBracket = false;
                }
                continue;
            }

            // 5. Detect comment start
            if (c == '-' && next == '-') {
                inLineComment = true;
                i++; // skip second '-'
                if (currentWord.length() > 0) {
                    state.processWord(currentWord.toString());
                    currentWord.setLength(0);
                }
                continue;
            }

            if (c == '/' && next == '*') {
                inBlockComment = true;
                i++; // skip '*'
                if (currentWord.length() > 0) {
                    state.processWord(currentWord.toString());
                    currentWord.setLength(0);
                }
                continue;
            }

            // 6. Detect quote start
            if (c == '\'') {
                inSingleQuote = true;
                if (currentWord.length() > 0) {
                    state.processWord(currentWord.toString());
                    currentWord.setLength(0);
                }
                current.append(c);
                continue;
            }

            if (c == '"') {
                inDoubleQuote = true;
                if (currentWord.length() > 0) {
                    state.processWord(currentWord.toString());
                    currentWord.setLength(0);
                }
                current.append(c);
                continue;
            }

            if (c == '`') {
                inBacktick = true;
                if (currentWord.length() > 0) {
                    state.processWord(currentWord.toString());
                    currentWord.setLength(0);
                }
                current.append(c);
                continue;
            }

            if (c == '[') {
                inBracket = true;
                if (currentWord.length() > 0) {
                    state.processWord(currentWord.toString());
                    currentWord.setLength(0);
                }
                current.append(c);
                continue;
            }

            // 7. Tokenize words outside comments and quotes
            if (Character.isLetterOrDigit(c) || c == '_') {
                currentWord.append(c);
                current.append(c);
            } else {
                if (currentWord.length() > 0) {
                    state.processWord(currentWord.toString());
                    currentWord.setLength(0);
                }

                current.append(c);

                // 8. Delimiter check ';'
                if (c == ';') {
                    if (state.canTerminateStatement()) {
                        String stmt = current.toString().trim();
                        if (!stmt.isEmpty()) {
                            statements.add(stmt);
                        }
                        current.setLength(0);
                        state.reset();
                    }
                }
            }
        }

        // Handle trailing word or statement if any
        if (currentWord.length() > 0) {
            state.processWord(currentWord.toString());
            currentWord.setLength(0);
        }

        if (current.length() > 0) {
            String trailing = current.toString().trim();
            if (!trailing.isEmpty() && !trailing.equals(";")) {
                statements.add(trailing);
            }
        }

        return statements;
    }

    /**
     * Parser state container for statement splitting, keyword scanning, and trigger block nesting.
     */
    private static class ParserState {
        int triggerState = 0; // 0=NONE, 1=SAW_CREATE, 2=SAW_CREATE_TEMP, 3=IN_TRIGGER
        boolean triggerBodyEntered = false;
        int triggerBeginDepth = 0;
        int caseDepth = 0;

        void processWord(String word) {
            String upper = word.toUpperCase();
            if (triggerState == 0) {
                if (upper.equals("CREATE")) {
                    triggerState = 1;
                }
            } else if (triggerState == 1) {
                if (upper.equals("TEMP") || upper.equals("TEMPORARY")) {
                    triggerState = 2;
                } else if (upper.equals("TRIGGER")) {
                    triggerState = 3;
                } else {
                    triggerState = 0;
                }
            } else if (triggerState == 2) {
                if (upper.equals("TRIGGER")) {
                    triggerState = 3;
                } else {
                    triggerState = 0;
                }
            } else if (triggerState == 3) {
                if (upper.equals("CASE")) {
                    caseDepth++;
                } else if (upper.equals("BEGIN")) {
                    triggerBodyEntered = true;
                    triggerBeginDepth++;
                } else if (upper.equals("END")) {
                    if (caseDepth > 0) {
                        caseDepth--;
                    } else if (triggerBeginDepth > 0) {
                        triggerBeginDepth--;
                    }
                }
            }
        }

        boolean canTerminateStatement() {
            return triggerState != 3 || (triggerBodyEntered && triggerBeginDepth == 0);
        }

        void reset() {
            triggerState = 0;
            triggerBodyEntered = false;
            triggerBeginDepth = 0;
            caseDepth = 0;
        }
    }

    /**
     * Applies idempotent forward-only schema migrations to preserve historical databases
     * while guaranteeing newly added columns and tables are present.
     *
     * @param conn active JDBC connection within the transaction
     * @throws SQLException if a database error occurs
     */
    private static void applyAdditiveMigrations(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // 0. schema_migrations table
            stmt.execute("CREATE TABLE IF NOT EXISTS schema_migrations (" +
                    "version TEXT PRIMARY KEY, " +
                    "description TEXT NOT NULL, " +
                    "applied_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))" +
                    ")");

            // V1: Base Schema marker
            boolean hasUsers = false;
            try (java.sql.ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='users'")) {
                if (rs.next()) hasUsers = true;
            }
            if (hasUsers) {
                stmt.execute("INSERT OR IGNORE INTO schema_migrations (version, description) VALUES ('V1__base_schema', 'Initial 13-table core schema')");
            }

            // V2: attendance session_code column
            boolean hasAttendance = false;
            boolean hasSessionCode = false;
            try (java.sql.ResultSet rs = stmt.executeQuery("PRAGMA table_info(attendance)")) {
                while (rs.next()) {
                    hasAttendance = true;
                    if ("session_code".equalsIgnoreCase(rs.getString("name"))) {
                        hasSessionCode = true;
                    }
                }
            }
            if (hasAttendance && !hasSessionCode) {
                stmt.execute("ALTER TABLE attendance ADD COLUMN session_code TEXT NULL");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_att_session_code ON attendance(session_code)");
            }
            if (hasAttendance) {
                stmt.execute("INSERT OR IGNORE INTO schema_migrations (version, description) VALUES ('V2__attendance_session_code', 'Add session_code column to attendance')");
            }

            // V3: timetables
            stmt.execute("CREATE TABLE IF NOT EXISTS timetables (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "course_id INTEGER NOT NULL REFERENCES courses(id), " +
                    "faculty_id INTEGER NOT NULL REFERENCES faculty(id), " +
                    "department_id INTEGER NOT NULL REFERENCES departments(id), " +
                    "semester TEXT NOT NULL, " +
                    "day_of_week TEXT NOT NULL CHECK(day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')), " +
                    "start_time TEXT NOT NULL, " +
                    "end_time TEXT NOT NULL, " +
                    "classroom TEXT NOT NULL, " +
                    "academic_year TEXT NOT NULL, " +
                    "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "CHECK(start_time < end_time)" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_timetables_course ON timetables(course_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_timetables_faculty ON timetables(faculty_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_timetables_dept_sem ON timetables(department_id, semester)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_timetables_day ON timetables(day_of_week)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_timetables_year ON timetables(academic_year)");
            stmt.execute("INSERT OR IGNORE INTO schema_migrations (version, description) VALUES ('V3__timetables', 'Create timetables table')");

            // V4: attendance_sessions & attendance_submissions
            stmt.execute("CREATE TABLE IF NOT EXISTS attendance_sessions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "course_id INTEGER NOT NULL REFERENCES courses(id), " +
                    "faculty_id INTEGER NOT NULL REFERENCES faculty(id), " +
                    "session_date TEXT NOT NULL, " +
                    "session_code TEXT NOT NULL, " +
                    "academic_year TEXT NOT NULL, " +
                    "expires_at TEXT NOT NULL, " +
                    "status TEXT NOT NULL CHECK(status IN ('OPEN', 'CLOSED', 'FINALIZED')), " +
                    "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_att_sess_course ON attendance_sessions(course_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_att_sess_faculty ON attendance_sessions(faculty_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_att_sess_code ON attendance_sessions(session_code, status)");

            stmt.execute("CREATE TABLE IF NOT EXISTS attendance_submissions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "session_id INTEGER NOT NULL REFERENCES attendance_sessions(id) ON DELETE CASCADE, " +
                    "student_id INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE, " +
                    "enrollment_id INTEGER NOT NULL REFERENCES enrollments(id) ON DELETE CASCADE, " +
                    "submitted_code TEXT NOT NULL, " +
                    "status TEXT NOT NULL CHECK(status IN ('ACCEPTED', 'INVALID_CODE', 'EXPIRED', 'ALREADY_SUBMITTED', 'REJECTED')), " +
                    "submission_time TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE(session_id, student_id)" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_att_sub_sess ON attendance_submissions(session_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_att_sub_stu ON attendance_submissions(student_id)");
            stmt.execute("INSERT OR IGNORE INTO schema_migrations (version, description) VALUES ('V4__attendance_sessions', 'Create attendance sessions and checkin submissions tables')");

            // V5: academic_programs
            stmt.execute("CREATE TABLE IF NOT EXISTS academic_programs (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "department_id INTEGER NOT NULL REFERENCES departments(id), " +
                    "name TEXT NOT NULL, " +
                    "code TEXT NOT NULL UNIQUE, " +
                    "degree TEXT NOT NULL, " +
                    "level TEXT NOT NULL CHECK(level IN ('UNDERGRADUATE', 'POSTGRADUATE', 'RESEARCH', 'DIPLOMA')), " +
                    "duration_years INTEGER NOT NULL DEFAULT 4, " +
                    "total_semesters INTEGER NOT NULL DEFAULT 8, " +
                    "is_research INTEGER NOT NULL DEFAULT 0 CHECK(is_research IN (0, 1)), " +
                    "active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0, 1)), " +
                    "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_programs_dept ON academic_programs(department_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_programs_code ON academic_programs(code)");
            stmt.execute("INSERT OR IGNORE INTO schema_migrations (version, description) VALUES ('V5__academic_programs', 'Create academic programs table')");

            // V6: assessment_schemes, assessment_items, student_assessment_marks
            stmt.execute("CREATE TABLE IF NOT EXISTS assessment_schemes (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "course_id INTEGER NOT NULL REFERENCES courses(id), " +
                    "academic_year TEXT NOT NULL, " +
                    "has_end_sem_exam INTEGER NOT NULL DEFAULT 1 CHECK(has_end_sem_exam IN (0, 1)), " +
                    "internal_max_marks REAL NOT NULL DEFAULT 50.0, " +
                    "end_sem_raw_max REAL NOT NULL DEFAULT 100.0, " +
                    "end_sem_weight_marks REAL NOT NULL DEFAULT 50.0, " +
                    "is_published INTEGER NOT NULL DEFAULT 0 CHECK(is_published IN (0, 1)), " +
                    "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE(course_id, academic_year)" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ass_scheme_course ON assessment_schemes(course_id, academic_year)");

            stmt.execute("CREATE TABLE IF NOT EXISTS assessment_items (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "scheme_id INTEGER NOT NULL REFERENCES assessment_schemes(id) ON DELETE CASCADE, " +
                    "name TEXT NOT NULL, " +
                    "max_marks REAL NOT NULL DEFAULT 20.0, " +
                    "weight REAL NOT NULL DEFAULT 1.0, " +
                    "conducted INTEGER NOT NULL DEFAULT 0 CHECK(conducted IN (0, 1)), " +
                    "scheduled_date TEXT NULL, " +
                    "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_ass_items_scheme ON assessment_items(scheme_id)");

            stmt.execute("CREATE TABLE IF NOT EXISTS student_assessment_marks (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "assessment_item_id INTEGER NOT NULL REFERENCES assessment_items(id) ON DELETE CASCADE, " +
                    "student_id INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE, " +
                    "marks_obtained REAL NOT NULL, " +
                    "is_absent INTEGER NOT NULL DEFAULT 0 CHECK(is_absent IN (0, 1)), " +
                    "is_exempt INTEGER NOT NULL DEFAULT 0 CHECK(is_exempt IN (0, 1)), " +
                    "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE(assessment_item_id, student_id)" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_stu_ass_marks_item ON student_assessment_marks(assessment_item_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_stu_ass_marks_stu ON student_assessment_marks(student_id)");
            stmt.execute("INSERT OR IGNORE INTO schema_migrations (version, description) VALUES ('V6__assessment_schemes', 'Create assessment schemes and marks tables')");

            // V7: institution_bank_details & fee_payments column extensions
            stmt.execute("CREATE TABLE IF NOT EXISTS institution_bank_details (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "beneficiary_name TEXT NOT NULL, " +
                    "bank_name TEXT NOT NULL, " +
                    "branch_name TEXT NOT NULL, " +
                    "account_number TEXT NOT NULL, " +
                    "ifsc_code TEXT NOT NULL, " +
                    "account_type TEXT NOT NULL DEFAULT 'Current Account', " +
                    "upi_id TEXT NOT NULL, " +
                    "instructions TEXT NOT NULL, " +
                    "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ")");

            boolean hasFeePayments = false;
            java.util.Set<String> feeCols = new java.util.HashSet<>();
            try (java.sql.ResultSet rs = stmt.executeQuery("PRAGMA table_info(fee_payments)")) {
                while (rs.next()) {
                    hasFeePayments = true;
                    feeCols.add(rs.getString("name").toLowerCase());
                }
            }
            if (hasFeePayments) {
                if (!feeCols.contains("gateway_order_id")) stmt.execute("ALTER TABLE fee_payments ADD COLUMN gateway_order_id TEXT NULL");
                if (!feeCols.contains("gateway_payment_id")) stmt.execute("ALTER TABLE fee_payments ADD COLUMN gateway_payment_id TEXT NULL");
                if (!feeCols.contains("gateway_signature")) stmt.execute("ALTER TABLE fee_payments ADD COLUMN gateway_signature TEXT NULL");
                if (!feeCols.contains("gateway_name")) stmt.execute("ALTER TABLE fee_payments ADD COLUMN gateway_name TEXT NULL");
                if (!feeCols.contains("receipt_number")) stmt.execute("ALTER TABLE fee_payments ADD COLUMN receipt_number TEXT NULL");
                if (!feeCols.contains("utr_number")) stmt.execute("ALTER TABLE fee_payments ADD COLUMN utr_number TEXT NULL");
                if (!feeCols.contains("verification_status")) stmt.execute("ALTER TABLE fee_payments ADD COLUMN verification_status TEXT NOT NULL DEFAULT 'PENDING'");

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_fee_receipt ON fee_payments(receipt_number)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_fee_gateway_pay ON fee_payments(gateway_payment_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_fee_utr ON fee_payments(utr_number)");
            }
            stmt.execute("INSERT OR IGNORE INTO schema_migrations (version, description) VALUES ('V7__bank_details_and_online_payments', 'Create institution bank details and extended fee payment columns')");

            // V8: attendance_automation_enhancements
            boolean hasAttSessions = false;
            java.util.Set<String> attSessionCols = new java.util.HashSet<>();
            try (java.sql.ResultSet rs = stmt.executeQuery("PRAGMA table_info(attendance_sessions)")) {
                while (rs.next()) {
                    hasAttSessions = true;
                    attSessionCols.add(rs.getString("name").toLowerCase());
                }
            }
            if (hasAttSessions) {
                if (!attSessionCols.contains("session_number")) {
                    stmt.execute("ALTER TABLE attendance_sessions ADD COLUMN session_number TEXT NOT NULL DEFAULT '1'");
                }
                if (!attSessionCols.contains("finalized_at")) {
                    stmt.execute("ALTER TABLE attendance_sessions ADD COLUMN finalized_at TEXT NULL");
                }
                if (!attSessionCols.contains("finalized_by")) {
                    stmt.execute("ALTER TABLE attendance_sessions ADD COLUMN finalized_by INTEGER NULL REFERENCES users(id)");
                }
            }

            stmt.execute("CREATE TABLE IF NOT EXISTS attendance_failed_attempts (" +
                    "session_id INTEGER NOT NULL REFERENCES attendance_sessions(id) ON DELETE CASCADE, " +
                    "student_id INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE, " +
                    "attempt_count INTEGER NOT NULL DEFAULT 1, " +
                    "last_attempt_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "PRIMARY KEY(session_id, student_id)" +
                    ")");

            stmt.execute("INSERT OR IGNORE INTO schema_migrations (version, description) VALUES " +
                    "('V8__attendance_automation_enhancements', 'Add attendance session audit fields and failed attempts tracking')");

            // V9: academic_structure_and_programmes
            // 1. departments column extensions
            boolean hasDepts = false;
            java.util.Set<String> deptCols = new java.util.HashSet<>();
            try (java.sql.ResultSet rs = stmt.executeQuery("PRAGMA table_info(departments)")) {
                while (rs.next()) {
                    hasDepts = true;
                    deptCols.add(rs.getString("name").toLowerCase());
                }
            }
            if (hasDepts) {
                if (!deptCols.contains("school")) {
                    stmt.execute("ALTER TABLE departments ADD COLUMN school TEXT NOT NULL DEFAULT 'School of Engineering'");
                }
                if (!deptCols.contains("description")) {
                    stmt.execute("ALTER TABLE departments ADD COLUMN description TEXT NULL");
                }
                if (!deptCols.contains("head_of_department")) {
                    stmt.execute("ALTER TABLE departments ADD COLUMN head_of_department TEXT NULL");
                }
                if (!deptCols.contains("contact_email")) {
                    stmt.execute("ALTER TABLE departments ADD COLUMN contact_email TEXT NULL");
                }
                if (!deptCols.contains("contact_phone")) {
                    stmt.execute("ALTER TABLE departments ADD COLUMN contact_phone TEXT NULL");
                }
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_dept_school ON departments(school)");
            }

            // 2. academic_programs column extensions & indexes
            boolean hasPrograms = false;
            java.util.Set<String> progCols = new java.util.HashSet<>();
            try (java.sql.ResultSet rs = stmt.executeQuery("PRAGMA table_info(academic_programs)")) {
                while (rs.next()) {
                    hasPrograms = true;
                    progCols.add(rs.getString("name").toLowerCase());
                }
            }
            if (hasPrograms) {
                if (!progCols.contains("description")) {
                    stmt.execute("ALTER TABLE academic_programs ADD COLUMN description TEXT NULL");
                }
                if (!progCols.contains("credits_required")) {
                    stmt.execute("ALTER TABLE academic_programs ADD COLUMN credits_required INTEGER NOT NULL DEFAULT 160");
                }
                if (!progCols.contains("academic_regulation")) {
                    stmt.execute("ALTER TABLE academic_programs ADD COLUMN academic_regulation TEXT NOT NULL DEFAULT 'R2026'");
                }
            }

            // 3. programme_semesters
            stmt.execute("CREATE TABLE IF NOT EXISTS programme_semesters (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "program_id INTEGER NOT NULL REFERENCES academic_programs(id) ON DELETE CASCADE, " +
                    "semester_number INTEGER NOT NULL, " +
                    "semester_label TEXT NOT NULL, " +
                    "term_type TEXT NOT NULL DEFAULT 'SEMESTER' CHECK(term_type IN ('SEMESTER', 'COURSEWORK', 'RESEARCH_TERM', 'THESIS')), " +
                    "credits_target INTEGER NOT NULL DEFAULT 20, " +
                    "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE(program_id, semester_number)" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_prog_sem_prog ON programme_semesters(program_id)");

            // 4. programme_curriculum
            stmt.execute("CREATE TABLE IF NOT EXISTS programme_curriculum (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "program_id INTEGER NOT NULL REFERENCES academic_programs(id) ON DELETE CASCADE, " +
                    "semester_number INTEGER NOT NULL, " +
                    "course_id INTEGER NOT NULL REFERENCES courses(id) ON DELETE CASCADE, " +
                    "course_category TEXT NOT NULL CHECK(course_category IN ('CORE', 'ELECTIVE', 'OPEN_ELECTIVE', 'LABORATORY', 'PROJECT', 'INTERNSHIP', 'SEMINAR', 'RESEARCH')), " +
                    "is_mandatory INTEGER NOT NULL DEFAULT 1 CHECK(is_mandatory IN (0, 1)), " +
                    "academic_regulation TEXT NOT NULL DEFAULT 'R2026', " +
                    "prerequisite_course_id INTEGER NULL REFERENCES courses(id), " +
                    "active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0, 1)), " +
                    "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "UNIQUE(program_id, semester_number, course_id, academic_regulation)" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_prog_curr_prog ON programme_curriculum(program_id, semester_number)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_prog_curr_course ON programme_curriculum(course_id)");

            // 5. student_cohorts
            stmt.execute("CREATE TABLE IF NOT EXISTS student_cohorts (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "cohort_code TEXT NOT NULL UNIQUE, " +
                    "program_id INTEGER NOT NULL REFERENCES academic_programs(id) ON DELETE CASCADE, " +
                    "admission_year TEXT NOT NULL, " +
                    "expected_graduation_year TEXT NOT NULL, " +
                    "current_semester INTEGER NOT NULL DEFAULT 1, " +
                    "status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE', 'GRADUATED', 'ARCHIVED')), " +
                    "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_cohorts_prog ON student_cohorts(program_id)");

            // 6. students column extensions
            boolean hasStudents = false;
            java.util.Set<String> stuCols = new java.util.HashSet<>();
            try (java.sql.ResultSet rs = stmt.executeQuery("PRAGMA table_info(students)")) {
                while (rs.next()) {
                    hasStudents = true;
                    stuCols.add(rs.getString("name").toLowerCase());
                }
            }
            if (hasStudents) {
                if (!stuCols.contains("program_id")) {
                    stmt.execute("ALTER TABLE students ADD COLUMN program_id INTEGER NULL REFERENCES academic_programs(id)");
                }
                if (!stuCols.contains("cohort_id")) {
                    stmt.execute("ALTER TABLE students ADD COLUMN cohort_id INTEGER NULL REFERENCES student_cohorts(id)");
                }
                if (!stuCols.contains("admission_year")) {
                    stmt.execute("ALTER TABLE students ADD COLUMN admission_year TEXT NULL");
                }
                if (!stuCols.contains("expected_graduation_year")) {
                    stmt.execute("ALTER TABLE students ADD COLUMN expected_graduation_year TEXT NULL");
                }
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_students_program ON students(program_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_students_cohort ON students(cohort_id)");
            }

            // 7. student_progression_history
            stmt.execute("CREATE TABLE IF NOT EXISTS student_progression_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "student_id INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE, " +
                    "previous_semester INTEGER NOT NULL, " +
                    "new_semester INTEGER NOT NULL, " +
                    "academic_year TEXT NOT NULL, " +
                    "action TEXT NOT NULL CHECK(action IN ('PROMOTED', 'HELD', 'REPEATED', 'LATERAL_ENTRY')), " +
                    "performed_by INTEGER NOT NULL REFERENCES users(id), " +
                    "reason TEXT NOT NULL, " +
                    "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                    ")");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_prog_hist_stu ON student_progression_history(student_id)");

            stmt.execute("INSERT OR IGNORE INTO schema_migrations (version, description) VALUES " +
                    "('V9__academic_structure_and_programmes', 'Add institutional academic hierarchy, curriculum mapping, cohorts, and student progression')");

            // V10: milestone4_stabilization
            boolean hasProgHist = false;
            try (java.sql.ResultSet rs = stmt.executeQuery("PRAGMA table_info(student_progression_history)")) {
                if (rs.next()) {
                    hasProgHist = true;
                }
            }
            if (hasProgHist) {
                // 1. History immutability triggers
                stmt.execute("CREATE TRIGGER IF NOT EXISTS trg_prevent_progression_history_del " +
                        "BEFORE DELETE ON student_progression_history " +
                        "BEGIN " +
                        "    SELECT RAISE(ABORT, 'Progression history records are immutable and cannot be deleted.'); " +
                        "END");

                stmt.execute("CREATE TRIGGER IF NOT EXISTS trg_prevent_progression_history_upd " +
                        "BEFORE UPDATE ON student_progression_history " +
                        "BEGIN " +
                        "    SELECT RAISE(ABORT, 'Progression history records are immutable and cannot be updated.'); " +
                        "END");
            }

            if (hasStudents) {
                // 2. Student cohort & program consistency triggers
                stmt.execute("CREATE TRIGGER IF NOT EXISTS trg_student_cohort_prog_ins " +
                        "BEFORE INSERT ON students " +
                        "FOR EACH ROW " +
                        "WHEN NEW.cohort_id IS NOT NULL AND NEW.program_id IS NOT NULL " +
                        "BEGIN " +
                        "    SELECT RAISE(ABORT, 'Student cohort does not belong to the selected programme') " +
                        "    WHERE NOT EXISTS ( " +
                        "        SELECT 1 FROM student_cohorts WHERE id = NEW.cohort_id AND program_id = NEW.program_id " +
                        "    ); " +
                        "END");

                stmt.execute("CREATE TRIGGER IF NOT EXISTS trg_student_cohort_prog_upd " +
                        "BEFORE UPDATE OF cohort_id, program_id ON students " +
                        "FOR EACH ROW " +
                        "WHEN NEW.cohort_id IS NOT NULL AND NEW.program_id IS NOT NULL " +
                        "BEGIN " +
                        "    SELECT RAISE(ABORT, 'Student cohort does not belong to the selected programme') " +
                        "    WHERE NOT EXISTS ( " +
                        "        SELECT 1 FROM student_cohorts WHERE id = NEW.cohort_id AND program_id = NEW.program_id " +
                        "    ); " +
                        "END");
            }

            boolean hasDepartments = false;
            try (java.sql.ResultSet rs = stmt.executeQuery("PRAGMA table_info(departments)")) {
                if (rs.next()) {
                    hasDepartments = true;
                }
            }
            if (hasDepartments) {
                // 3. Historical department school normalization with name verification
                stmt.execute("UPDATE departments SET school = 'School of Computer Applications' " +
                        "WHERE code = 'CA' AND (LOWER(name) LIKE '%computer%' OR LOWER(name) LIKE '%application%') " +
                        "AND (school IS NULL OR school = 'School of Engineering')");

                stmt.execute("UPDATE departments SET school = 'School of Management & Commerce' " +
                        "WHERE code IN ('MS', 'COM') AND (LOWER(name) LIKE '%management%' OR LOWER(name) LIKE '%commerce%' OR LOWER(name) LIKE '%business%') " +
                        "AND (school IS NULL OR school = 'School of Engineering')");

                stmt.execute("UPDATE departments SET school = 'School of Arts & Humanities' " +
                        "WHERE (code IN ('ECO', 'PSY') OR (code = 'ENG' AND (LOWER(name) LIKE '%english%' OR LOWER(name) LIKE '%humanities%' OR LOWER(name) LIKE '%literature%'))) " +
                        "AND (school IS NULL OR school = 'School of Engineering')");

                stmt.execute("UPDATE departments SET school = 'School of Hospitality' " +
                        "WHERE code = 'HM' AND (LOWER(name) LIKE '%hotel%' OR LOWER(name) LIKE '%hospitality%' OR LOWER(name) LIKE '%culinary%') " +
                        "AND (school IS NULL OR school = 'School of Engineering')");

                stmt.execute("UPDATE departments SET school = 'School of Health Sciences' " +
                        "WHERE code IN ('MED', 'NUR', 'PHARM') AND (LOWER(name) LIKE '%med%' OR LOWER(name) LIKE '%nurs%' OR LOWER(name) LIKE '%pharm%' OR LOWER(name) LIKE '%health%') " +
                        "AND (school IS NULL OR school = 'School of Engineering')");
            }

            if (hasUsers) {
                stmt.execute("INSERT OR IGNORE INTO schema_migrations (version, description) VALUES " +
                        "('V10__milestone4_stabilization', 'Add progression immutability triggers, cohort-programme consistency triggers, and normalize historical department schools')");
            }

            // V11: progression_history_fk_rebuild
            // Safely rebuild student_progression_history if legacy database retains ON DELETE CASCADE
            boolean needsRebuild = false;
            try (java.sql.ResultSet rs = stmt.executeQuery("PRAGMA foreign_key_list(student_progression_history)")) {
                while (rs.next()) {
                    String refTable = rs.getString("table");
                    String onDelete = rs.getString("on_delete");
                    if ("students".equalsIgnoreCase(refTable) && "CASCADE".equalsIgnoreCase(onDelete)) {
                        needsRebuild = true;
                        break;
                    }
                }
            }
            if (needsRebuild) {
                stmt.execute("DROP TRIGGER IF EXISTS trg_prevent_progression_history_del");
                stmt.execute("DROP TRIGGER IF EXISTS trg_prevent_progression_history_upd");

                stmt.execute("CREATE TABLE student_progression_history_new (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "student_id INTEGER NOT NULL REFERENCES students(id) ON DELETE RESTRICT, " +
                        "previous_semester INTEGER NOT NULL, " +
                        "new_semester INTEGER NOT NULL, " +
                        "academic_year TEXT NOT NULL, " +
                        "action TEXT NOT NULL CHECK(action IN ('PROMOTED', 'HELD', 'REPEATED', 'LATERAL_ENTRY')), " +
                        "performed_by INTEGER NOT NULL REFERENCES users(id), " +
                        "reason TEXT NOT NULL, " +
                        "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                        ")");

                stmt.execute("INSERT INTO student_progression_history_new (id, student_id, previous_semester, new_semester, academic_year, action, performed_by, reason, created_at) " +
                        "SELECT id, student_id, previous_semester, new_semester, academic_year, action, performed_by, reason, created_at FROM student_progression_history");

                stmt.execute("DROP TABLE student_progression_history");
                stmt.execute("ALTER TABLE student_progression_history_new RENAME TO student_progression_history");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_prog_hist_stu ON student_progression_history(student_id)");

                stmt.execute("CREATE TRIGGER IF NOT EXISTS trg_prevent_progression_history_del " +
                        "BEFORE DELETE ON student_progression_history " +
                        "BEGIN " +
                        "    SELECT RAISE(ABORT, 'Progression history records are immutable and cannot be deleted.'); " +
                        "END");

                stmt.execute("CREATE TRIGGER IF NOT EXISTS trg_prevent_progression_history_upd " +
                        "BEFORE UPDATE ON student_progression_history " +
                        "BEGIN " +
                        "    SELECT RAISE(ABORT, 'Progression history records are immutable and cannot be updated.'); " +
                        "END");
            }

            if (hasUsers) {
                stmt.execute("INSERT OR IGNORE INTO schema_migrations (version, description) VALUES " +
                        "('V11__progression_history_fk_rebuild', 'Rebuild student_progression_history to enforce ON DELETE RESTRICT on students foreign key')");
            }
        }
    }

    /**
     * Loads a classpath resource file as a UTF-8 string.
     *
     * @param resourcePath the classpath resource path
     * @return the file contents as a string
     * @throws DataAccessException if the resource cannot be found or read
     */
    private static String loadResourceAsString(String resourcePath) {
        InputStream inputStream = DatabaseInitializer.class.getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new DataAccessException(
                "SQL resource not found on classpath: " + resourcePath
                + ". Ensure the resource exists in src/main/resources/.");
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new DataAccessException(
                "Failed to read SQL resource " + resourcePath + ": " + e.getMessage(), e);
        }
    }
}

