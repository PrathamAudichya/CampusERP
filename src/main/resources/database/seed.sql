-- =============================================================================
-- Campus ERP - Demonstration & Testing Seed Data
-- Academic Year: 2026-2027 | Currency: INR (Integer Paise)
-- Realistic Synthetic Indian Institutional Demonstration Records
-- All emails use synthetic domain @example.test per RFC 2606 / RFC 6761
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. users (Demonstration personnel accounts: 2 faculty, 3 students)
-- Administrator account must be provisioned via secure interactive first-run setup.
-- Demonstration accounts are seeded in uninitialized state (must_change_password=1);
-- an administrator must generate secure temporary passwords via User Management.
-- -----------------------------------------------------------------------------
INSERT OR IGNORE INTO users (id, username, password_hash, role, must_change_password, active) VALUES
(2, 'jdoe', '$2a$12$UNINITIALIZED_SECURE_HASH_REQUIRE_ADMIN_RESET_JDOE000000', 'FACULTY', 1, 1),
(3, 'rjohnson', '$2a$12$UNINITIALIZED_SECURE_HASH_REQUIRE_ADMIN_RESET_RJOHNSON00', 'FACULTY', 1, 1),
(4, 'ssmith', '$2a$12$UNINITIALIZED_SECURE_HASH_REQUIRE_ADMIN_RESET_SSMITH0000', 'STUDENT', 1, 1),
(5, 'ajones', '$2a$12$UNINITIALIZED_SECURE_HASH_REQUIRE_ADMIN_RESET_AJONES0000', 'STUDENT', 1, 1),
(6, 'mbrown', '$2a$12$UNINITIALIZED_SECURE_HASH_REQUIRE_ADMIN_RESET_MBROWN0000', 'STUDENT', 1, 1);

-- -----------------------------------------------------------------------------
-- 2. departments (Demonstration departments across institutional schools)
-- -----------------------------------------------------------------------------
INSERT OR IGNORE INTO departments (id, code, name, school, description, head_of_department, contact_email, contact_phone, active) VALUES
(1, 'CS', 'Computer Science and Engineering', 'School of Engineering', 'Core computing, systems, and algorithms', 'Dr. Rajesh Sharma', 'hod.cs@example.test', '+91 98765 00001', 1),
(2, 'EC', 'Electronics and Communication Engineering', 'School of Engineering', 'Signal processing, circuits, and communications', 'Dr. Priya Iyer', 'hod.ec@example.test', '+91 98765 00002', 1),
(3, 'ME', 'Mechanical Engineering', 'School of Engineering', 'Thermodynamics, robotics, and manufacturing', 'Dr. Arvind Rao', 'hod.me@example.test', '+91 98765 00003', 1),
(4, 'CE', 'Civil Engineering', 'School of Engineering', 'Structures, environmental, and geo-tech', 'Prof. Suresh Iyer', 'hod.ce@example.test', '+91 98765 00004', 1),
(5, 'EE', 'Electrical and Electronics Engineering', 'School of Engineering', 'Power systems and renewable energy', 'Dr. Karthik Reddy', 'hod.ee@example.test', '+91 98765 00005', 1),
(6, 'CH', 'Chemical Engineering', 'School of Engineering', 'Process engineering and biochemical technology', 'Dr. Anand Kumar', 'hod.ch@example.test', '+91 98765 00006', 1),
(7, 'BT', 'Biotechnology', 'School of Engineering', 'Genomics, bioinformatics, and bioprocesses', 'Dr. Meera Krishnan', 'hod.bt@example.test', '+91 98765 00007', 1),
(8, 'CA', 'Computer Applications', 'School of Computer Applications', 'Software development and enterprise applications', 'Prof. Ananya Menon', 'hod.ca@example.test', '+91 98765 00008', 1),
(9, 'MS', 'Management Studies', 'School of Management & Commerce', 'Business administration and executive leadership', 'Dr. Vikram Seth', 'hod.ms@example.test', '+91 98765 00009', 1),
(10, 'COM', 'Commerce', 'School of Management & Commerce', 'Accounting, taxation, and financial markets', 'Prof. Ramesh Gupta', 'hod.com@example.test', '+91 98765 00010', 1),
(11, 'ENG', 'English', 'School of Arts & Humanities', 'Literature, linguistics, and technical communication', 'Dr. Anita Desai', 'hod.eng@example.test', '+91 98765 00011', 1),
(12, 'ECO', 'Economics', 'School of Arts & Humanities', 'Econometrics, macroeconomics, and development policy', 'Dr. Amartya Sen', 'hod.eco@example.test', '+91 98765 00012', 1),
(13, 'PSY', 'Psychology', 'School of Arts & Humanities', 'Cognitive psychology, clinical counseling, and neuroscience', 'Dr. Shobha Rao', 'hod.psy@example.test', '+91 98765 00013', 1),
(14, 'HM', 'Hotel Management', 'School of Hospitality', 'Culinary arts, food beverage, and guest services', 'Chef Sanjeev Nair', 'hod.hm@example.test', '+91 98765 00014', 1),
(15, 'MED', 'Medical Sciences', 'School of Health Sciences', 'Clinical medicine, anatomy, and pharmacology', 'Dr. Rajiv Menon', 'hod.med@example.test', '+91 98765 00015', 1),
(16, 'NUR', 'Nursing', 'School of Health Sciences', 'Critical care, patient management, and public health', 'Prof. Mary Joseph', 'hod.nur@example.test', '+91 98765 00016', 1),
(17, 'PHARM', 'Pharmacy', 'School of Health Sciences', 'Pharmaceutics, drug chemistry, and toxicology', 'Dr. K. Srinivas', 'hod.pharm@example.test', '+91 98765 00017', 1);

-- -----------------------------------------------------------------------------
-- 3. faculty (2 realistic Indian faculty members)
-- -----------------------------------------------------------------------------
INSERT OR IGNORE INTO faculty (id, user_id, department_id, faculty_id, first_name, last_name, email, phone, specialization, join_date, active) VALUES
(1, 2, 1, 'FAC2026001', 'Rajesh', 'Sharma', 'rajesh.sharma@example.test', '+91 98765 43210', 'Algorithms & Data Structures', '2026-01-15', 1),
(2, 3, 2, 'FAC2026002', 'Priya', 'Iyer', 'priya.iyer@example.test', '+91 98765 43211', 'Circuits & Embedded Systems', '2026-01-15', 1);

-- -----------------------------------------------------------------------------
-- 4. students (3 realistic Indian students)
-- -----------------------------------------------------------------------------
INSERT OR IGNORE INTO students (id, user_id, department_id, student_id, first_name, last_name, email, phone, date_of_birth, admission_date, semester, active) VALUES
(1, 4, 1, 'STU2026001', 'Aarav', 'Patel', 'aarav.patel@example.test', '+91 98765 01001', '2005-04-12', '2026-08-01', '1', 1),
(2, 5, 2, 'STU2026002', 'Diya', 'Sharma', 'diya.sharma@example.test', '+91 98765 01002', '2005-09-23', '2026-08-01', '1', 1),
(3, 6, 1, 'STU2026003', 'Prakhar', 'Raj', 'prakhar.raj@example.test', '+91 98765 01003', '2005-11-05', '2026-08-01', '1', 1);

-- -----------------------------------------------------------------------------
-- 5. academic_programs (Institutional degree programs)
-- -----------------------------------------------------------------------------
INSERT OR IGNORE INTO academic_programs (id, department_id, name, code, degree, level, duration_years, total_semesters, description, credits_required, academic_regulation, is_research, active) VALUES
(1, 1, 'B.Tech in Computer Science and Engineering', 'BTECH-CSE', 'B.Tech', 'UNDERGRADUATE', 4, 8, 'Four-year undergraduate engineering degree in computer science', 160, 'R2026', 0, 1),
(2, 8, 'Bachelor of Computer Applications', 'BCA', 'BCA', 'UNDERGRADUATE', 3, 6, 'Three-year undergraduate degree in computer applications', 120, 'R2026', 0, 1),
(3, 1, 'M.Tech in Software Engineering', 'MTECH-SE', 'M.Tech', 'POSTGRADUATE', 2, 4, 'Two-year postgraduate masters in software engineering', 80, 'R2026', 0, 1),
(4, 2, 'B.Tech in Electronics & Communication', 'BTECH-ECE', 'B.Tech', 'UNDERGRADUATE', 4, 8, 'Four-year undergraduate engineering degree in electronics & communication', 160, 'R2026', 0, 1),
(5, 1, 'Doctor of Philosophy in Computer Science', 'PHD-CS', 'Ph.D.', 'RESEARCH', 5, 10, 'Five-year advanced doctoral research and thesis program', 180, 'R2026', 1, 1),
(6, 9, 'Bachelor of Business Administration', 'BBA', 'BBA', 'UNDERGRADUATE', 3, 6, 'Three-year undergraduate program in business administration', 120, 'R2026', 0, 1),
(7, 9, 'Master of Business Administration', 'MBA', 'MBA', 'POSTGRADUATE', 2, 4, 'Two-year postgraduate masters in management and business leadership', 90, 'R2026', 0, 1),
(8, 10, 'Bachelor of Commerce', 'B.COM', 'B.Com', 'UNDERGRADUATE', 3, 6, 'Three-year degree in accounting and finance', 120, 'R2026', 0, 1),
(9, 8, 'Master of Computer Applications', 'MCA', 'MCA', 'POSTGRADUATE', 2, 4, 'Two-year postgraduate masters in enterprise computing', 80, 'R2026', 0, 1),
(10, 15, 'Bachelor of Medicine and Bachelor of Surgery', 'MBBS', 'MBBS', 'UNDERGRADUATE', 5, 10, 'Five-year medical clinical degree program', 220, 'R2026', 0, 1),
(11, 14, 'Bachelor of Hotel Management', 'BHM', 'BHM', 'UNDERGRADUATE', 4, 8, 'Four-year degree in hospitality and hotel operations', 150, 'R2026', 0, 1);

-- -----------------------------------------------------------------------------
-- 5b. student_cohorts (Demonstration cohorts)
-- -----------------------------------------------------------------------------
INSERT OR IGNORE INTO student_cohorts (id, cohort_code, program_id, admission_year, expected_graduation_year, current_semester, status) VALUES
(1, 'BTECH-CSE-2026', 1, '2026-2027', '2030-2031', 1, 'ACTIVE'),
(2, 'BTECH-ECE-2026', 4, '2026-2027', '2030-2031', 1, 'ACTIVE'),
(3, 'BCA-2026', 2, '2026-2027', '2029-2030', 1, 'ACTIVE'),
(4, 'MBA-2026', 7, '2026-2027', '2028-2029', 1, 'ACTIVE');

-- -----------------------------------------------------------------------------
-- 6. courses (4 records)
-- -----------------------------------------------------------------------------
INSERT OR IGNORE INTO courses (id, department_id, course_code, name, credits, semester, max_capacity, active) VALUES
(1, 1, 'CS101', 'Data Structures', 4, '1', 60, 1),
(2, 1, 'CS201', 'Algorithms', 4, '2', 60, 1),
(3, 2, 'EC101', 'Circuit Theory', 3, '1', 60, 1),
(4, 3, 'ME101', 'Thermodynamics', 3, '1', 60, 1);

-- -----------------------------------------------------------------------------
-- 6b. programme_curriculum (Demonstration curriculum mappings)
-- -----------------------------------------------------------------------------
INSERT OR IGNORE INTO programme_curriculum (id, program_id, semester_number, course_id, course_category, is_mandatory, academic_regulation, active) VALUES
(1, 1, 1, 1, 'CORE', 1, 'R2026', 1),
(2, 1, 2, 2, 'CORE', 1, 'R2026', 1),
(3, 4, 1, 3, 'CORE', 1, 'R2026', 1);

-- -----------------------------------------------------------------------------
-- 7. course_assignments (3 records)
-- -----------------------------------------------------------------------------
INSERT OR IGNORE INTO course_assignments (id, course_id, faculty_id, academic_year, active) VALUES
(1, 1, 1, '2026-2027', 1),
(2, 2, 1, '2026-2027', 1),
(3, 3, 2, '2026-2027', 1);

-- -----------------------------------------------------------------------------
-- 8. enrollments (3 records)
-- -----------------------------------------------------------------------------
INSERT OR IGNORE INTO enrollments (id, student_id, course_id, academic_year, enrolled_date, status) VALUES
(1, 1, 1, '2026-2027', '2026-08-01', 'ACTIVE'),
(2, 3, 1, '2026-2027', '2026-08-01', 'ACTIVE'),
(3, 2, 3, '2026-2027', '2026-08-01', 'ACTIVE');

-- -----------------------------------------------------------------------------
-- 9. fee_structures (4 records: Amounts in integer paise)
-- -----------------------------------------------------------------------------
INSERT OR IGNORE INTO fee_structures (id, department_id, semester, fee_type, amount_paise, academic_year, due_date, active) VALUES
(1, 1, '1', 'Tuition', 2500000, '2026-2027', '2026-09-30', 1),
(2, 1, '1', 'Lab Fee', 500000, '2026-2027', '2026-09-30', 1),
(3, 2, '1', 'Tuition', 2400000, '2026-2027', '2026-09-30', 1),
(4, 2, '1', 'Lab Fee', 600000, '2026-2027', '2026-09-30', 1);

-- -----------------------------------------------------------------------------
-- 10. examinations (4 records)
-- -----------------------------------------------------------------------------
INSERT OR IGNORE INTO examinations (id, course_id, exam_name, exam_type, exam_date, total_marks, academic_year, active) VALUES
(1, 1, 'Midterm Exam', 'MIDTERM', '2026-10-15', 100, '2026-2027', 1),
(2, 1, 'Final Exam', 'FINAL', '2026-12-10', 100, '2026-2027', 1),
(3, 3, 'Midterm Exam', 'MIDTERM', '2026-10-16', 100, '2026-2027', 1),
(4, 3, 'Final Exam', 'FINAL', '2026-12-11', 100, '2026-2027', 1);

-- -----------------------------------------------------------------------------
-- 11. timetables (4 demonstration records: CS & EC Sem 1)
-- -----------------------------------------------------------------------------
INSERT OR IGNORE INTO timetables (id, course_id, faculty_id, department_id, semester, day_of_week, start_time, end_time, classroom, academic_year) VALUES
(1, 1, 1, 1, '1', 'MONDAY', '09:00', '10:30', 'LH-101', '2026-2027'),
(2, 1, 1, 1, '1', 'WEDNESDAY', '09:00', '10:30', 'LH-101', '2026-2027'),
(3, 3, 2, 2, '1', 'TUESDAY', '10:00', '11:30', 'LH-202', '2026-2027'),
(4, 3, 2, 2, '1', 'THURSDAY', '10:00', '11:30', 'LH-202', '2026-2027');

-- -----------------------------------------------------------------------------
-- 12. institution_bank_details (Institutional bank details)
-- -----------------------------------------------------------------------------
INSERT OR IGNORE INTO institution_bank_details (id, beneficiary_name, bank_name, branch_name, account_number, ifsc_code, account_type, upi_id, instructions) VALUES
(1, 'CAMPUS ERP INSTITUTIONAL TRUST', 'State Bank of India', 'University Campus Branch', '38920194829104', 'SBIN0001234', 'Current Account', 'campuserp@sbi', 'Please mention Student Registration Number in NEFT/IMPS/UPI transaction remarks. Retain UTR receipt for accounts verification.');

-- -----------------------------------------------------------------------------
-- 13. schema_migrations
-- -----------------------------------------------------------------------------
INSERT OR IGNORE INTO schema_migrations (version, description) VALUES
('V1__base_schema', 'Initial 13-table core schema'),
('V2__attendance_session_code', 'Add session_code column to attendance'),
('V3__timetables', 'Create timetables table'),
('V4__attendance_sessions', 'Create attendance sessions and checkin submissions tables'),
('V5__academic_programs', 'Create academic programs table'),
('V6__assessment_schemes', 'Create assessment schemes and marks tables'),
('V7__bank_details_and_online_payments', 'Create institution bank details and extended fee payment columns'),
('V8__attendance_automation_enhancements', 'Add attendance session audit fields and failed attempts tracking'),
('V9__academic_structure_and_programmes', 'Add institutional academic hierarchy, curriculum mapping, cohorts, and student progression');

