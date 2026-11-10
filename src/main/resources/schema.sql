-- =============================================================================
-- College Management System - Database Schema
-- Version: 6.0 | Pure SQLite DDL
-- Objects: 14 Tables, 22 Foreign Keys, 15 UNIQUE Constraints, 44 Indexes, 8 Triggers
-- =============================================================================

PRAGMA foreign_keys = ON;

-- -----------------------------------------------------------------------------
-- Table 1: users
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL CHECK(role IN ('ADMIN', 'FACULTY', 'STUDENT')),
    must_change_password INTEGER NOT NULL DEFAULT 0 CHECK(must_change_password IN (0, 1)),
    active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0, 1)),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_active ON users(active);

-- -----------------------------------------------------------------------------
-- Table 2: departments
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS departments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    school TEXT NOT NULL DEFAULT 'School of Engineering',
    description TEXT NULL,
    head_of_department TEXT NULL,
    contact_email TEXT NULL,
    contact_phone TEXT NULL,
    active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0, 1)),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_departments_active ON departments(active);
CREATE INDEX IF NOT EXISTS idx_dept_school ON departments(school);

-- -----------------------------------------------------------------------------
-- Table 3: students
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS students (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL UNIQUE REFERENCES users(id),
    department_id INTEGER NOT NULL REFERENCES departments(id),
    program_id INTEGER NULL REFERENCES academic_programs(id),
    cohort_id INTEGER NULL REFERENCES student_cohorts(id),
    student_id TEXT NOT NULL UNIQUE,
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    phone TEXT NULL,
    date_of_birth TEXT NULL,
    admission_date TEXT NOT NULL DEFAULT CURRENT_DATE,
    admission_year TEXT NULL,
    expected_graduation_year TEXT NULL,
    semester TEXT NOT NULL,
    active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0, 1)),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_students_department ON students(department_id);
CREATE INDEX IF NOT EXISTS idx_students_program ON students(program_id);
CREATE INDEX IF NOT EXISTS idx_students_cohort ON students(cohort_id);
CREATE INDEX IF NOT EXISTS idx_students_semester ON students(semester);
CREATE INDEX IF NOT EXISTS idx_students_active ON students(active);

-- -----------------------------------------------------------------------------
-- Table 4: faculty
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS faculty (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL UNIQUE REFERENCES users(id),
    department_id INTEGER NOT NULL REFERENCES departments(id),
    faculty_id TEXT NOT NULL UNIQUE,
    first_name TEXT NOT NULL,
    last_name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    phone TEXT NULL,
    specialization TEXT NULL,
    join_date TEXT NOT NULL DEFAULT CURRENT_DATE,
    active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0, 1)),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_faculty_department ON faculty(department_id);
CREATE INDEX IF NOT EXISTS idx_faculty_active ON faculty(active);

-- -----------------------------------------------------------------------------
-- Table 5: courses
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS courses (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    department_id INTEGER NOT NULL REFERENCES departments(id),
    course_code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    credits INTEGER NOT NULL CHECK(credits > 0),
    semester TEXT NOT NULL,
    max_capacity INTEGER NULL CHECK(max_capacity IS NULL OR max_capacity > 0),
    active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0, 1)),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_courses_department ON courses(department_id);
CREATE INDEX IF NOT EXISTS idx_courses_semester ON courses(semester);
CREATE INDEX IF NOT EXISTS idx_courses_active ON courses(active);

-- -----------------------------------------------------------------------------
-- Table 6: course_assignments
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS course_assignments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    course_id INTEGER NOT NULL REFERENCES courses(id),
    faculty_id INTEGER NOT NULL REFERENCES faculty(id),
    academic_year TEXT NOT NULL,
    active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0, 1)),
    assigned_date TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(course_id, faculty_id, academic_year)
);

CREATE INDEX IF NOT EXISTS idx_ca_course ON course_assignments(course_id);
CREATE INDEX IF NOT EXISTS idx_ca_faculty ON course_assignments(faculty_id);
CREATE INDEX IF NOT EXISTS idx_ca_year ON course_assignments(academic_year);
CREATE INDEX IF NOT EXISTS idx_ca_active ON course_assignments(active);

-- -----------------------------------------------------------------------------
-- Table 7: enrollments
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS enrollments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id INTEGER NOT NULL REFERENCES students(id),
    course_id INTEGER NOT NULL REFERENCES courses(id),
    academic_year TEXT NOT NULL,
    enrolled_date TEXT NOT NULL DEFAULT CURRENT_DATE,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE', 'DROPPED', 'COMPLETED')),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(student_id, course_id, academic_year)
);

CREATE INDEX IF NOT EXISTS idx_enroll_student ON enrollments(student_id);
CREATE INDEX IF NOT EXISTS idx_enroll_course ON enrollments(course_id);
CREATE INDEX IF NOT EXISTS idx_enroll_year ON enrollments(academic_year);
CREATE INDEX IF NOT EXISTS idx_enroll_status ON enrollments(status);

-- -----------------------------------------------------------------------------
-- Table 8: attendance
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attendance (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    enrollment_id INTEGER NOT NULL REFERENCES enrollments(id),
    attendance_date TEXT NOT NULL,
    status TEXT NOT NULL CHECK(status IN ('PRESENT', 'ABSENT', 'LATE')),
    remarks TEXT NULL,
    session_code TEXT NULL,
    UNIQUE(enrollment_id, attendance_date)
);

CREATE INDEX IF NOT EXISTS idx_att_enrollment ON attendance(enrollment_id);
CREATE INDEX IF NOT EXISTS idx_att_date ON attendance(attendance_date);
CREATE INDEX IF NOT EXISTS idx_att_session_code ON attendance(session_code);

-- -----------------------------------------------------------------------------
-- Table 9: examinations
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS examinations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    course_id INTEGER NOT NULL REFERENCES courses(id),
    exam_name TEXT NOT NULL,
    exam_type TEXT NOT NULL CHECK(exam_type IN ('MIDTERM', 'FINAL', 'QUIZ', 'ASSIGNMENT')),
    exam_date TEXT NOT NULL,
    total_marks INTEGER NOT NULL CHECK(total_marks > 0),
    academic_year TEXT NOT NULL,
    active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0, 1)),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_exam_course ON examinations(course_id);
CREATE INDEX IF NOT EXISTS idx_exam_date ON examinations(exam_date);
CREATE INDEX IF NOT EXISTS idx_exam_year ON examinations(academic_year);
CREATE INDEX IF NOT EXISTS idx_exam_active ON examinations(active);

-- -----------------------------------------------------------------------------
-- Table 10: marks
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS marks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    enrollment_id INTEGER NOT NULL REFERENCES enrollments(id),
    examination_id INTEGER NOT NULL REFERENCES examinations(id),
    marks_obtained REAL NOT NULL CHECK(marks_obtained >= 0),
    grade TEXT NULL,
    remarks TEXT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(enrollment_id, examination_id)
);

CREATE INDEX IF NOT EXISTS idx_marks_enrollment ON marks(enrollment_id);
CREATE INDEX IF NOT EXISTS idx_marks_exam ON marks(examination_id);

-- -----------------------------------------------------------------------------
-- Table 11: fee_structures
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fee_structures (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    department_id INTEGER NOT NULL REFERENCES departments(id),
    semester TEXT NOT NULL,
    fee_type TEXT NOT NULL,
    amount_paise INTEGER NOT NULL CHECK(amount_paise > 0),
    academic_year TEXT NOT NULL,
    due_date TEXT NULL,
    active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0, 1)),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(department_id, semester, fee_type, academic_year)
);

CREATE INDEX IF NOT EXISTS idx_fs_department ON fee_structures(department_id);
CREATE INDEX IF NOT EXISTS idx_fs_year ON fee_structures(academic_year);
CREATE INDEX IF NOT EXISTS idx_fs_active ON fee_structures(active);

-- -----------------------------------------------------------------------------
-- Table 12: fee_payments
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fee_payments (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id INTEGER NOT NULL REFERENCES students(id),
    fee_structure_id INTEGER NOT NULL REFERENCES fee_structures(id),
    amount_paid_paise INTEGER NOT NULL CHECK(amount_paid_paise > 0),
    payment_date TEXT NOT NULL DEFAULT CURRENT_DATE,
    payment_method TEXT NOT NULL,
    transaction_ref TEXT NULL UNIQUE,
    status TEXT NOT NULL DEFAULT 'COMPLETED' CHECK(status IN ('COMPLETED', 'PENDING', 'FAILED', 'REFUNDED')),
    original_payment_id INTEGER NULL REFERENCES fee_payments(id),
    gateway_order_id TEXT NULL,
    gateway_payment_id TEXT NULL,
    gateway_signature TEXT NULL,
    gateway_name TEXT NULL,
    receipt_number TEXT NULL,
    utr_number TEXT NULL,
    verification_status TEXT NOT NULL DEFAULT 'PENDING',
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK((status = 'REFUNDED' AND original_payment_id IS NOT NULL) OR (status IN ('COMPLETED', 'PENDING', 'FAILED') AND original_payment_id IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_fp_student ON fee_payments(student_id);
CREATE INDEX IF NOT EXISTS idx_fp_structure ON fee_payments(fee_structure_id);
CREATE INDEX IF NOT EXISTS idx_fp_status ON fee_payments(status);
CREATE INDEX IF NOT EXISTS idx_fp_original ON fee_payments(original_payment_id);
CREATE INDEX IF NOT EXISTS idx_fee_receipt ON fee_payments(receipt_number);
CREATE INDEX IF NOT EXISTS idx_fee_gateway_pay ON fee_payments(gateway_payment_id);
CREATE INDEX IF NOT EXISTS idx_fee_utr ON fee_payments(utr_number);

-- -----------------------------------------------------------------------------
-- Table 13: audit_log
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id),
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id INTEGER NULL,
    old_value TEXT NULL,
    new_value TEXT NULL,
    timestamp TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_user ON audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON audit_log(timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_action ON audit_log(action);

-- -----------------------------------------------------------------------------
-- Table 14: timetables
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS timetables (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    course_id INTEGER NOT NULL REFERENCES courses(id),
    faculty_id INTEGER NOT NULL REFERENCES faculty(id),
    department_id INTEGER NOT NULL REFERENCES departments(id),
    semester TEXT NOT NULL,
    day_of_week TEXT NOT NULL CHECK(day_of_week IN ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    start_time TEXT NOT NULL,
    end_time TEXT NOT NULL,
    classroom TEXT NOT NULL,
    academic_year TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK(start_time < end_time)
);

CREATE INDEX IF NOT EXISTS idx_timetables_course ON timetables(course_id);
CREATE INDEX IF NOT EXISTS idx_timetables_faculty ON timetables(faculty_id);
CREATE INDEX IF NOT EXISTS idx_timetables_dept_sem ON timetables(department_id, semester);
CREATE INDEX IF NOT EXISTS idx_timetables_day ON timetables(day_of_week);
CREATE INDEX IF NOT EXISTS idx_timetables_year ON timetables(academic_year);

-- -----------------------------------------------------------------------------
-- Table 15: attendance_sessions
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attendance_sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    course_id INTEGER NOT NULL REFERENCES courses(id),
    faculty_id INTEGER NOT NULL REFERENCES faculty(id),
    session_date TEXT NOT NULL,
    session_code TEXT NOT NULL,
    academic_year TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    status TEXT NOT NULL CHECK(status IN ('OPEN', 'CLOSED', 'FINALIZED')),
    session_number TEXT NOT NULL DEFAULT '1',
    finalized_at TEXT NULL,
    finalized_by INTEGER NULL REFERENCES users(id),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_att_sess_course ON attendance_sessions(course_id);
CREATE INDEX IF NOT EXISTS idx_att_sess_faculty ON attendance_sessions(faculty_id);
CREATE INDEX IF NOT EXISTS idx_att_sess_code ON attendance_sessions(session_code, status);

-- -----------------------------------------------------------------------------
-- Table 16: attendance_submissions
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attendance_submissions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id INTEGER NOT NULL REFERENCES attendance_sessions(id) ON DELETE CASCADE,
    student_id INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    enrollment_id INTEGER NOT NULL REFERENCES enrollments(id) ON DELETE CASCADE,
    submitted_code TEXT NOT NULL,
    status TEXT NOT NULL CHECK(status IN ('ACCEPTED', 'INVALID_CODE', 'EXPIRED', 'ALREADY_SUBMITTED', 'REJECTED')),
    submission_time TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(session_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_att_sub_sess ON attendance_submissions(session_id);
CREATE INDEX IF NOT EXISTS idx_att_sub_stu ON attendance_submissions(student_id);

-- -----------------------------------------------------------------------------
-- Table 17: attendance_failed_attempts
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attendance_failed_attempts (
    session_id INTEGER NOT NULL REFERENCES attendance_sessions(id) ON DELETE CASCADE,
    student_id INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    attempt_count INTEGER NOT NULL DEFAULT 1,
    last_attempt_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(session_id, student_id)
);

-- -----------------------------------------------------------------------------
-- Table 17: academic_programs
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS academic_programs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    department_id INTEGER NOT NULL REFERENCES departments(id),
    name TEXT NOT NULL,
    code TEXT NOT NULL UNIQUE,
    degree TEXT NOT NULL,
    level TEXT NOT NULL CHECK(level IN ('UNDERGRADUATE', 'POSTGRADUATE', 'RESEARCH', 'DIPLOMA')),
    duration_years INTEGER NOT NULL DEFAULT 4,
    total_semesters INTEGER NOT NULL DEFAULT 8,
    description TEXT NULL,
    credits_required INTEGER NOT NULL DEFAULT 160,
    academic_regulation TEXT NOT NULL DEFAULT 'R2026',
    is_research INTEGER NOT NULL DEFAULT 0 CHECK(is_research IN (0, 1)),
    active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0, 1)),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_programs_dept ON academic_programs(department_id);
CREATE INDEX IF NOT EXISTS idx_programs_code ON academic_programs(code);

-- -----------------------------------------------------------------------------
-- Table 18: programme_semesters
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS programme_semesters (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    program_id INTEGER NOT NULL REFERENCES academic_programs(id) ON DELETE CASCADE,
    semester_number INTEGER NOT NULL,
    semester_label TEXT NOT NULL,
    term_type TEXT NOT NULL DEFAULT 'SEMESTER' CHECK(term_type IN ('SEMESTER', 'COURSEWORK', 'RESEARCH_TERM', 'THESIS')),
    credits_target INTEGER NOT NULL DEFAULT 20,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(program_id, semester_number)
);

CREATE INDEX IF NOT EXISTS idx_prog_sem_prog ON programme_semesters(program_id);

-- -----------------------------------------------------------------------------
-- Table 19: programme_curriculum
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS programme_curriculum (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    program_id INTEGER NOT NULL REFERENCES academic_programs(id) ON DELETE CASCADE,
    semester_number INTEGER NOT NULL,
    course_id INTEGER NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    course_category TEXT NOT NULL CHECK(course_category IN ('CORE', 'ELECTIVE', 'OPEN_ELECTIVE', 'LABORATORY', 'PROJECT', 'INTERNSHIP', 'SEMINAR', 'RESEARCH')),
    is_mandatory INTEGER NOT NULL DEFAULT 1 CHECK(is_mandatory IN (0, 1)),
    academic_regulation TEXT NOT NULL DEFAULT 'R2026',
    prerequisite_course_id INTEGER NULL REFERENCES courses(id),
    active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0, 1)),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(program_id, semester_number, course_id, academic_regulation)
);

CREATE INDEX IF NOT EXISTS idx_prog_curr_prog ON programme_curriculum(program_id, semester_number);
CREATE INDEX IF NOT EXISTS idx_prog_curr_course ON programme_curriculum(course_id);

-- -----------------------------------------------------------------------------
-- Table 20: student_cohorts
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS student_cohorts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cohort_code TEXT NOT NULL UNIQUE,
    program_id INTEGER NOT NULL REFERENCES academic_programs(id) ON DELETE CASCADE,
    admission_year TEXT NOT NULL,
    expected_graduation_year TEXT NOT NULL,
    current_semester INTEGER NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE', 'GRADUATED', 'ARCHIVED')),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_cohorts_prog ON student_cohorts(program_id);

-- -----------------------------------------------------------------------------
-- Table 21: student_progression_history
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS student_progression_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id INTEGER NOT NULL REFERENCES students(id) ON DELETE RESTRICT,
    previous_semester INTEGER NOT NULL,
    new_semester INTEGER NOT NULL,
    academic_year TEXT NOT NULL,
    action TEXT NOT NULL CHECK(action IN ('PROMOTED', 'HELD', 'REPEATED', 'LATERAL_ENTRY')),
    performed_by INTEGER NOT NULL REFERENCES users(id),
    reason TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_prog_hist_stu ON student_progression_history(student_id);

-- -----------------------------------------------------------------------------
-- Table 18: assessment_schemes
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS assessment_schemes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    course_id INTEGER NOT NULL REFERENCES courses(id),
    academic_year TEXT NOT NULL,
    has_end_sem_exam INTEGER NOT NULL DEFAULT 1 CHECK(has_end_sem_exam IN (0, 1)),
    internal_max_marks REAL NOT NULL DEFAULT 50.0,
    end_sem_raw_max REAL NOT NULL DEFAULT 100.0,
    end_sem_weight_marks REAL NOT NULL DEFAULT 50.0,
    is_published INTEGER NOT NULL DEFAULT 0 CHECK(is_published IN (0, 1)),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(course_id, academic_year)
);

CREATE INDEX IF NOT EXISTS idx_ass_scheme_course ON assessment_schemes(course_id, academic_year);

-- -----------------------------------------------------------------------------
-- Table 19: assessment_items
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS assessment_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scheme_id INTEGER NOT NULL REFERENCES assessment_schemes(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    max_marks REAL NOT NULL DEFAULT 20.0,
    weight REAL NOT NULL DEFAULT 1.0,
    conducted INTEGER NOT NULL DEFAULT 0 CHECK(conducted IN (0, 1)),
    scheduled_date TEXT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ass_items_scheme ON assessment_items(scheme_id);

-- -----------------------------------------------------------------------------
-- Table 20: student_assessment_marks
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS student_assessment_marks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    assessment_item_id INTEGER NOT NULL REFERENCES assessment_items(id) ON DELETE CASCADE,
    student_id INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE,
    marks_obtained REAL NOT NULL,
    is_absent INTEGER NOT NULL DEFAULT 0 CHECK(is_absent IN (0, 1)),
    is_exempt INTEGER NOT NULL DEFAULT 0 CHECK(is_exempt IN (0, 1)),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(assessment_item_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_stu_ass_marks_item ON student_assessment_marks(assessment_item_id);
CREATE INDEX IF NOT EXISTS idx_stu_ass_marks_stu ON student_assessment_marks(student_id);

-- -----------------------------------------------------------------------------
-- Table 21: institution_bank_details
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS institution_bank_details (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    beneficiary_name TEXT NOT NULL,
    bank_name TEXT NOT NULL,
    branch_name TEXT NOT NULL,
    account_number TEXT NOT NULL,
    ifsc_code TEXT NOT NULL,
    account_type TEXT NOT NULL DEFAULT 'Current Account',
    upi_id TEXT NOT NULL,
    instructions TEXT NOT NULL,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- -----------------------------------------------------------------------------
-- Table 22: schema_migrations
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS schema_migrations (
    version TEXT PRIMARY KEY,
    description TEXT NOT NULL,
    applied_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime'))
);

-- =============================================================================
-- Triggers: Audit Immutability & Financial Ledger Integrity
-- =============================================================================

-- Trigger 1: Prohibit any UPDATE statement on audit_log
CREATE TRIGGER IF NOT EXISTS trg_audit_log_no_update
BEFORE UPDATE ON audit_log
BEGIN
    SELECT RAISE(FAIL, 'Updates to audit_log are prohibited.');
END;

-- Trigger 2: Prohibit any DELETE statement on audit_log
CREATE TRIGGER IF NOT EXISTS trg_audit_log_no_delete
BEFORE DELETE ON audit_log
BEGIN
    SELECT RAISE(FAIL, 'Deletions from audit_log are prohibited.');
END;

-- Trigger 3: Enforce refund referential integrity at the database engine level (INSERT)
CREATE TRIGGER IF NOT EXISTS trg_fee_payments_refund_integrity
BEFORE INSERT ON fee_payments
FOR EACH ROW
WHEN NEW.original_payment_id IS NOT NULL
BEGIN
    SELECT CASE
        WHEN (SELECT status FROM fee_payments WHERE id = NEW.original_payment_id) != 'COMPLETED'
        THEN RAISE(ABORT, 'Refund can only be issued against a COMPLETED payment.')
        WHEN (SELECT original_payment_id FROM fee_payments WHERE id = NEW.original_payment_id) IS NOT NULL
        THEN RAISE(ABORT, 'Cannot issue a refund against another refund.')
        WHEN (SELECT student_id FROM fee_payments WHERE id = NEW.original_payment_id) != NEW.student_id
        THEN RAISE(ABORT, 'Refund student_id must match original payment student_id.')
        WHEN (SELECT fee_structure_id FROM fee_payments WHERE id = NEW.original_payment_id) != NEW.fee_structure_id
        THEN RAISE(ABORT, 'Refund fee_structure_id must match original payment fee_structure_id.')
        WHEN NEW.status != 'REFUNDED'
        THEN RAISE(ABORT, 'A transaction with an original_payment_id must have status REFUNDED.')
    END;
END;

-- Trigger 4: Prohibit invalid UPDATE operations on fee_payments to enforce complete ledger immutability & refund integrity
CREATE TRIGGER IF NOT EXISTS trg_fee_payments_immutable_update
BEFORE UPDATE ON fee_payments
FOR EACH ROW
BEGIN
    SELECT CASE
        -- 1. Refund rows (OLD.status = 'REFUNDED') are completely and permanently immutable across all columns
        WHEN OLD.status = 'REFUNDED'
        THEN RAISE(ABORT, 'Refund transaction records are completely immutable across all columns.')

        -- 2. Core financial transaction identity, amounts, methods, references, and timestamps are immutable
        WHEN OLD.id IS NOT NEW.id
        THEN RAISE(ABORT, 'Cannot update id on existing fee payment: field is immutable.')
        WHEN OLD.student_id IS NOT NEW.student_id
        THEN RAISE(ABORT, 'Cannot update student_id on existing fee payment: field is immutable.')
        WHEN OLD.fee_structure_id IS NOT NEW.fee_structure_id
        THEN RAISE(ABORT, 'Cannot update fee_structure_id on existing fee payment: field is immutable.')
        WHEN OLD.amount_paid_paise IS NOT NEW.amount_paid_paise
        THEN RAISE(ABORT, 'Cannot update amount_paid_paise on existing fee payment: field is immutable.')
        WHEN OLD.payment_date IS NOT NEW.payment_date
        THEN RAISE(ABORT, 'Cannot update payment_date on existing fee payment: field is immutable.')
        WHEN OLD.payment_method IS NOT NEW.payment_method
        THEN RAISE(ABORT, 'Cannot update payment_method on existing fee payment: field is immutable.')
        WHEN OLD.transaction_ref IS NOT NEW.transaction_ref
        THEN RAISE(ABORT, 'Cannot update transaction_ref on existing fee payment: field is immutable.')
        WHEN OLD.original_payment_id IS NOT NEW.original_payment_id
        THEN RAISE(ABORT, 'Cannot modify original_payment_id relationship: field is immutable.')
        WHEN OLD.created_at IS NOT NEW.created_at
        THEN RAISE(ABORT, 'Cannot update created_at on existing fee payment: field is immutable.')

        -- 3. An original payment with associated refunds cannot have its status altered under any circumstance
        WHEN OLD.status IS NOT NEW.status AND EXISTS (SELECT 1 FROM fee_payments WHERE original_payment_id = OLD.id)
        THEN RAISE(ABORT, 'Cannot alter status of a fee payment that has associated refunds.')

        -- 4. Constrained status transitions for original payments (strictly only PENDING -> COMPLETED or PENDING -> FAILED)
        WHEN OLD.status IS NOT NEW.status AND NOT (OLD.status = 'PENDING' AND NEW.status IN ('COMPLETED', 'FAILED'))
        THEN RAISE(ABORT, 'Invalid payment status transition: only PENDING -> COMPLETED or PENDING -> FAILED is permitted.')
    END;
END;

-- =============================================================================
-- Triggers: Academic Progression Immutability & Cohort Consistency
-- =============================================================================

-- Trigger 5: Prohibit any DELETE statement on student_progression_history
CREATE TRIGGER IF NOT EXISTS trg_prevent_progression_history_del
BEFORE DELETE ON student_progression_history
BEGIN
    SELECT RAISE(ABORT, 'Progression history records are immutable and cannot be deleted.');
END;

-- Trigger 6: Prohibit any UPDATE statement on student_progression_history
CREATE TRIGGER IF NOT EXISTS trg_prevent_progression_history_upd
BEFORE UPDATE ON student_progression_history
BEGIN
    SELECT RAISE(ABORT, 'Progression history records are immutable and cannot be updated.');
END;

-- Trigger 7: Verify student cohort matches programme on INSERT
CREATE TRIGGER IF NOT EXISTS trg_student_cohort_prog_ins
BEFORE INSERT ON students
FOR EACH ROW
WHEN NEW.cohort_id IS NOT NULL AND NEW.program_id IS NOT NULL
BEGIN
    SELECT RAISE(ABORT, 'Student cohort does not belong to the selected programme')
    WHERE NOT EXISTS (
        SELECT 1 FROM student_cohorts WHERE id = NEW.cohort_id AND program_id = NEW.program_id
    );
END;

-- Trigger 8: Verify student cohort matches programme on UPDATE
CREATE TRIGGER IF NOT EXISTS trg_student_cohort_prog_upd
BEFORE UPDATE OF cohort_id, program_id ON students
FOR EACH ROW
WHEN NEW.cohort_id IS NOT NULL AND NEW.program_id IS NOT NULL
BEGIN
    SELECT RAISE(ABORT, 'Student cohort does not belong to the selected programme')
    WHERE NOT EXISTS (
        SELECT 1 FROM student_cohorts WHERE id = NEW.cohort_id AND program_id = NEW.program_id
    );
END;


