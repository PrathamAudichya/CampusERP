package com.college.config;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class ExistingDbMigrationTest {

    @Test
    public void testFullMigrationPipeline() throws Exception {
        File dbFile = new File("target/inspect-demo.db");
        if (!dbFile.exists()) {
            dbFile.getParentFile().mkdirs();
            try (Connection setup = DriverManager.getConnection("jdbc:sqlite:target/inspect-demo.db")) {
                try (Statement s = setup.createStatement()) {
                    s.execute("CREATE TABLE IF NOT EXISTS departments (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, code TEXT NOT NULL UNIQUE)");
                    s.execute("CREATE TABLE IF NOT EXISTS courses (id INTEGER PRIMARY KEY AUTOINCREMENT, department_id INTEGER, name TEXT, code TEXT)");
                    s.execute("CREATE TABLE IF NOT EXISTS faculty (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, department_id INTEGER)");
                    s.execute("CREATE TABLE IF NOT EXISTS students (id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, department_id INTEGER)");
                    s.execute("CREATE TABLE IF NOT EXISTS enrollments (id INTEGER PRIMARY KEY AUTOINCREMENT, student_id INTEGER, course_id INTEGER)");
                    s.execute("CREATE TABLE IF NOT EXISTS attendance (id INTEGER PRIMARY KEY AUTOINCREMENT, enrollment_id INTEGER, status TEXT, marked_at TEXT)");
                    s.execute("CREATE TABLE IF NOT EXISTS fee_payments (id INTEGER PRIMARY KEY AUTOINCREMENT, student_id INTEGER, amount REAL, status TEXT)");
                }
            }
        }
        assertTrue(dbFile.exists(), "inspect-demo.db must exist for migration testing");

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:target/inspect-demo.db")) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                // 1. schema_migrations
                stmt.execute("CREATE TABLE IF NOT EXISTS schema_migrations (" +
                        "version TEXT PRIMARY KEY, " +
                        "description TEXT NOT NULL, " +
                        "applied_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))" +
                        ")");

                // V2: attendance session_code
                boolean hasSessionCode = false;
                try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(attendance)")) {
                    while (rs.next()) {
                        if ("session_code".equalsIgnoreCase(rs.getString("name"))) {
                            hasSessionCode = true;
                            break;
                        }
                    }
                }
                if (!hasSessionCode) {
                    stmt.execute("ALTER TABLE attendance ADD COLUMN session_code TEXT NULL");
                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_att_session_code ON attendance(session_code)");
                }
                stmt.execute("INSERT OR IGNORE INTO schema_migrations (version, description) VALUES ('V2__attendance_session_code', 'Add session_code column to attendance')");

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

                // V7: institution_bank_details & fee_payments columns
                stmt.execute("CREATE TABLE IF NOT EXISTS institution_bank_details (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "beneficiary_name TEXT NOT NULL, " +
                        "bank_name TEXT NOT NULL, " +
                        "branch_name TEXT NOT NULL, " +
                        "account_number TEXT NOT NULL, " +
                        "ifsc_code TEXT NOT NULL, " +
                        "account_type TEXT NOT NULL DEFAULT 'Current', " +
                        "upi_id TEXT NOT NULL, " +
                        "instructions TEXT NOT NULL, " +
                        "updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP" +
                        ")");

                // Add payment columns to fee_payments if missing
                java.util.Set<String> feeCols = new java.util.HashSet<>();
                try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(fee_payments)")) {
                    while (rs.next()) {
                        feeCols.add(rs.getString("name").toLowerCase());
                    }
                }
                if (!feeCols.contains("gateway_order_id")) {
                    stmt.execute("ALTER TABLE fee_payments ADD COLUMN gateway_order_id TEXT NULL");
                }
                if (!feeCols.contains("gateway_payment_id")) {
                    stmt.execute("ALTER TABLE fee_payments ADD COLUMN gateway_payment_id TEXT NULL");
                }
                if (!feeCols.contains("gateway_signature")) {
                    stmt.execute("ALTER TABLE fee_payments ADD COLUMN gateway_signature TEXT NULL");
                }
                if (!feeCols.contains("gateway_name")) {
                    stmt.execute("ALTER TABLE fee_payments ADD COLUMN gateway_name TEXT NULL");
                }
                if (!feeCols.contains("receipt_number")) {
                    stmt.execute("ALTER TABLE fee_payments ADD COLUMN receipt_number TEXT NULL");
                }
                if (!feeCols.contains("utr_number")) {
                    stmt.execute("ALTER TABLE fee_payments ADD COLUMN utr_number TEXT NULL");
                }
                if (!feeCols.contains("verification_status")) {
                    stmt.execute("ALTER TABLE fee_payments ADD COLUMN verification_status TEXT NOT NULL DEFAULT 'VERIFIED'");
                }
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_fee_pay_receipt ON fee_payments(receipt_number)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_fee_pay_utr ON fee_payments(utr_number)");
                stmt.execute("INSERT OR IGNORE INTO schema_migrations (version, description) VALUES ('V7__bank_and_online_payments', 'Create bank details and payment columns')");

                conn.commit();
                System.out.println("All 7 migration steps applied and committed successfully!");
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}
