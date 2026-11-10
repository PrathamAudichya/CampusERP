package com.college.dao;

import com.college.config.DBConnection;
import com.college.exception.DataAccessException;
import com.college.model.Student;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the {@code students} table.
 *
 * <p>Handles student profile persistence, lookups by ID, student number, or user ID,
 * department joins for transient display properties, and active state management.</p>
 */
public class StudentDAO {

    private static final String BASE_SELECT =
        "SELECT s.*, d.name AS department_name, d.school AS school_name, " +
        "       ap.name AS program_name, ap.code AS program_code, ap.degree AS degree, " +
        "       sc.cohort_code AS cohort_code " +
        "FROM students s " +
        "LEFT JOIN departments d ON s.department_id = d.id " +
        "LEFT JOIN academic_programs ap ON s.program_id = ap.id " +
        "LEFT JOIN student_cohorts sc ON s.cohort_id = sc.id ";

    /**
     * Inserts a new student record.
     *
     * @param conn database connection
     * @param student student entity to insert
     * @return the persisted student with generated ID
     * @throws DataAccessException if a database error occurs
     */
    public Student insert(Connection conn, Student student) {
        String sqlFull = "INSERT INTO students (user_id, department_id, program_id, cohort_id, student_id, " +
                         "first_name, last_name, email, phone, date_of_birth, admission_date, " +
                         "admission_year, expected_graduation_year, semester, active) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sqlFull, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, student.getUserId());
            pstmt.setInt(2, student.getDepartmentId());
            if (student.getProgramId() != null && student.getProgramId() > 0) {
                pstmt.setInt(3, student.getProgramId());
            } else {
                pstmt.setNull(3, java.sql.Types.INTEGER);
            }
            if (student.getCohortId() != null && student.getCohortId() > 0) {
                pstmt.setInt(4, student.getCohortId());
            } else {
                pstmt.setNull(4, java.sql.Types.INTEGER);
            }
            pstmt.setString(5, student.getStudentId());
            pstmt.setString(6, student.getFirstName());
            pstmt.setString(7, student.getLastName());
            pstmt.setString(8, student.getEmail());
            pstmt.setString(9, student.getPhone());
            pstmt.setString(10, student.getDateOfBirth());
            pstmt.setString(11, student.getAdmissionDate());
            pstmt.setString(12, student.getAdmissionYear());
            pstmt.setString(13, student.getExpectedGraduationYear());
            pstmt.setString(14, student.getSemester());
            pstmt.setInt(15, student.isActive() ? 1 : 0);

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    student.setId(rs.getInt(1));
                }
            }
            return student;
        } catch (SQLException e) {
            String sqlLegacy = "INSERT INTO students (user_id, department_id, student_id, first_name, last_name, " +
                               "                      email, phone, date_of_birth, admission_date, semester, active) " +
                               "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlLegacy, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, student.getUserId());
                pstmt.setInt(2, student.getDepartmentId());
                pstmt.setString(3, student.getStudentId());
                pstmt.setString(4, student.getFirstName());
                pstmt.setString(5, student.getLastName());
                pstmt.setString(6, student.getEmail());
                pstmt.setString(7, student.getPhone());
                pstmt.setString(8, student.getDateOfBirth());
                pstmt.setString(9, student.getAdmissionDate());
                pstmt.setString(10, student.getSemester());
                pstmt.setInt(11, student.isActive() ? 1 : 0);

                pstmt.executeUpdate();

                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        student.setId(rs.getInt(1));
                    }
                }
                return student;
            } catch (SQLException ex) {
                throw new DataAccessException("Failed to insert student '" + student.getStudentId() + "': " + ex.getMessage(), ex);
            }
        }
    }

    /**
     * Convenience method to insert a student using a managed connection.
     *
     * @param student student entity to insert
     * @return the persisted student
     */
    public Student insert(Student student) {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, student);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a student by primary key ID, populating transient departmentName.
     *
     * @param conn database connection
     * @param id student record ID
     * @return student entity or {@code null} if not found
     */
    public Student findById(Connection conn, int id) {
        String sql = BASE_SELECT + "WHERE s.id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find student by ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find a student by ID using a managed connection.
     *
     * @param id student record ID
     * @return student entity or {@code null} if not found
     */
    public Student findById(int id) {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, id);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a student by unique student ID number (e.g., "STU2026001").
     *
     * @param conn database connection
     * @param studentId unique student ID string
     * @return student entity or {@code null} if not found
     */
    public Student findByStudentId(Connection conn, String studentId) {
        String sql = BASE_SELECT + "WHERE s.student_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, studentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find student by studentId '" + studentId + "': " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find a student by studentId using a managed connection.
     *
     * @param studentId unique student ID string
     * @return student entity or {@code null} if not found
     */
    public Student findByStudentId(String studentId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByStudentId(conn, studentId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a student by associated user ID.
     *
     * @param conn database connection
     * @param userId user account ID
     * @return student entity or {@code null} if not found
     */
    public Student findByUserId(Connection conn, int userId) {
        String sql = BASE_SELECT + "WHERE s.user_id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find student by userId " + userId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find a student by user ID using a managed connection.
     *
     * @param userId user account ID
     * @return student entity or {@code null} if not found
     */
    public Student findByUserId(int userId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByUserId(conn, userId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a student by email address.
     *
     * @param conn database connection
     * @param email student email
     * @return student entity or {@code null} if not found
     */
    public Student findByEmail(Connection conn, String email) {
        String sql = BASE_SELECT + "WHERE s.email = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find student by email '" + email + "': " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find a student by email using a managed connection.
     *
     * @param email student email
     * @return student entity or {@code null} if not found
     */
    public Student findByEmail(String email) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByEmail(conn, email);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all students.
     *
     * @param conn database connection
     * @return list of all students
     */
    public List<Student> findAll(Connection conn) {
        String sql = BASE_SELECT + "ORDER BY s.id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<Student> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve all students: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to retrieve all students using a managed connection.
     *
     * @return list of all students
     */
    public List<Student> findAll() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAll(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all active students.
     *
     * @param conn database connection
     * @return list of active students
     */
    public List<Student> findAllActive(Connection conn) {
        String sql = BASE_SELECT + "WHERE s.active = 1 ORDER BY s.id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<Student> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve active students: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to retrieve all active students using a managed connection.
     *
     * @return list of active students
     */
    public List<Student> findAllActive() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAllActive(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves students belonging to a department.
     *
     * @param conn database connection
     * @param departmentId department ID
     * @return list of students in the department
     */
    public List<Student> findByDepartment(Connection conn, int departmentId) {
        String sql = BASE_SELECT + "WHERE s.department_id = ? ORDER BY s.student_id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, departmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Student> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find students by department " + departmentId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find students by department using a managed connection.
     *
     * @param departmentId department ID
     * @return list of students in the department
     */
    public List<Student> findByDepartment(int departmentId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByDepartment(conn, departmentId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves students enrolled in a specific semester.
     *
     * @param conn database connection
     * @param semester semester name/number
     * @return list of students in the semester
     */
    public List<Student> findBySemester(Connection conn, String semester) {
        String sql = BASE_SELECT + "WHERE s.semester = ? ORDER BY s.student_id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, semester);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Student> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find students by semester " + semester + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find students by semester using a managed connection.
     *
     * @param semester semester name/number
     * @return list of students in the semester
     */
    public List<Student> findBySemester(String semester) {
        try (Connection conn = DBConnection.getConnection()) {
            return findBySemester(conn, semester);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Updates an existing student's editable fields.
     * Automatically sets {@code updated_at = CURRENT_TIMESTAMP}.
     *
     * @param conn database connection
     * @param student student entity
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean update(Connection conn, Student student) {
        String sqlFull = "UPDATE students SET department_id = ?, program_id = ?, cohort_id = ?, first_name = ?, last_name = ?, " +
                         "                    email = ?, phone = ?, date_of_birth = ?, admission_date = ?, " +
                         "                    admission_year = ?, expected_graduation_year = ?, semester = ?, " +
                         "                    active = ?, updated_at = CURRENT_TIMESTAMP " +
                         "WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sqlFull)) {
            pstmt.setInt(1, student.getDepartmentId());
            if (student.getProgramId() != null && student.getProgramId() > 0) {
                pstmt.setInt(2, student.getProgramId());
            } else {
                pstmt.setNull(2, java.sql.Types.INTEGER);
            }
            if (student.getCohortId() != null && student.getCohortId() > 0) {
                pstmt.setInt(3, student.getCohortId());
            } else {
                pstmt.setNull(3, java.sql.Types.INTEGER);
            }
            pstmt.setString(4, student.getFirstName());
            pstmt.setString(5, student.getLastName());
            pstmt.setString(6, student.getEmail());
            pstmt.setString(7, student.getPhone());
            pstmt.setString(8, student.getDateOfBirth());
            pstmt.setString(9, student.getAdmissionDate());
            pstmt.setString(10, student.getAdmissionYear());
            pstmt.setString(11, student.getExpectedGraduationYear());
            pstmt.setString(12, student.getSemester());
            pstmt.setInt(13, student.isActive() ? 1 : 0);
            pstmt.setInt(14, student.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            String sqlLegacy = "UPDATE students SET department_id = ?, first_name = ?, last_name = ?, " +
                               "                    email = ?, phone = ?, date_of_birth = ?, admission_date = ?, " +
                               "                    semester = ?, active = ?, updated_at = CURRENT_TIMESTAMP " +
                               "WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlLegacy)) {
                pstmt.setInt(1, student.getDepartmentId());
                pstmt.setString(2, student.getFirstName());
                pstmt.setString(3, student.getLastName());
                pstmt.setString(4, student.getEmail());
                pstmt.setString(5, student.getPhone());
                pstmt.setString(6, student.getDateOfBirth());
                pstmt.setString(7, student.getAdmissionDate());
                pstmt.setString(8, student.getSemester());
                pstmt.setInt(9, student.isActive() ? 1 : 0);
                pstmt.setInt(10, student.getId());
                return pstmt.executeUpdate() > 0;
            } catch (SQLException ex) {
                throw new DataAccessException("Failed to update student ID " + student.getId() + ": " + ex.getMessage(), ex);
            }
        }
    }

    public boolean update(Student student) {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, student);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public List<Student> findByCohortId(Connection conn, int cohortId) {
        String sql = BASE_SELECT + "WHERE s.cohort_id = ? ORDER BY s.student_id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, cohortId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Student> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find students for cohort " + cohortId + ": " + e.getMessage(), e);
        }
    }

    public List<Student> findByCohortId(int cohortId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByCohortId(conn, cohortId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public List<Student> findByProgramId(Connection conn, int programId) {
        String sql = BASE_SELECT + "WHERE s.program_id = ? ORDER BY s.semester, s.student_id";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, programId);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Student> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find students for program " + programId + ": " + e.getMessage(), e);
        }
    }

    public List<Student> findByProgramId(int programId) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByProgramId(conn, programId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public boolean updateProgression(Connection conn, int studentId, String newSemester) {
        String sql = "UPDATE students SET semester = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newSemester);
            pstmt.setInt(2, studentId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update progression for student " + studentId + ": " + e.getMessage(), e);
        }
    }

    public boolean updateProgression(int studentId, String newSemester) {
        try (Connection conn = DBConnection.getConnection()) {
            return updateProgression(conn, studentId, newSemester);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public boolean updateProgramAndCohort(Connection conn, int studentId, Integer programId, Integer cohortId,
                                          String admissionYear, String expectedGraduationYear) {
        String sql = "UPDATE students SET program_id = ?, cohort_id = ?, admission_year = ?, " +
                     "expected_graduation_year = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (programId != null && programId > 0) {
                pstmt.setInt(1, programId);
            } else {
                pstmt.setNull(1, java.sql.Types.INTEGER);
            }
            if (cohortId != null && cohortId > 0) {
                pstmt.setInt(2, cohortId);
            } else {
                pstmt.setNull(2, java.sql.Types.INTEGER);
            }
            pstmt.setString(3, admissionYear);
            pstmt.setString(4, expectedGraduationYear);
            pstmt.setInt(5, studentId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update program and cohort for student " + studentId + ": " + e.getMessage(), e);
        }
    }

    public boolean updateProgramAndCohort(int studentId, Integer programId, Integer cohortId,
                                          String admissionYear, String expectedGraduationYear) {
        try (Connection conn = DBConnection.getConnection()) {
            return updateProgramAndCohort(conn, studentId, programId, cohortId, admissionYear, expectedGraduationYear);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public boolean updateExpectedGraduationYear(Connection conn, int studentId, String expectedGraduationYear) {
        String sql = "UPDATE students SET expected_graduation_year = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, expectedGraduationYear);
            pstmt.setInt(2, studentId);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update expected graduation year for student " + studentId + ": " + e.getMessage(), e);
        }
    }

    public boolean updateExpectedGraduationYear(int studentId, String expectedGraduationYear) {
        try (Connection conn = DBConnection.getConnection()) {
            return updateExpectedGraduationYear(conn, studentId, expectedGraduationYear);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public boolean setActive(Connection conn, int id, boolean active) {
        String sql = "UPDATE students SET active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, active ? 1 : 0);
            pstmt.setInt(2, id);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to set active status for student ID " + id + ": " + e.getMessage(), e);
        }
    }

    public boolean setActive(int id, boolean active) {
        try (Connection conn = DBConnection.getConnection()) {
            return setActive(conn, id, active);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    private Student mapRow(ResultSet rs) throws SQLException {
        Student s = new Student();
        s.setId(rs.getInt("id"));
        s.setUserId(rs.getInt("user_id"));
        s.setDepartmentId(rs.getInt("department_id"));
        s.setStudentId(rs.getString("student_id"));
        s.setFirstName(rs.getString("first_name"));
        s.setLastName(rs.getString("last_name"));
        s.setEmail(rs.getString("email"));
        s.setPhone(rs.getString("phone"));
        s.setDateOfBirth(rs.getString("date_of_birth"));
        s.setAdmissionDate(rs.getString("admission_date"));
        s.setSemester(rs.getString("semester"));
        s.setActive(rs.getInt("active") == 1);
        s.setCreatedAt(rs.getString("created_at"));
        s.setUpdatedAt(rs.getString("updated_at"));

        try {
            s.setDepartmentName(rs.getString("department_name"));
        } catch (SQLException ignored) {}

        try {
            int pid = rs.getInt("program_id");
            if (!rs.wasNull() && pid > 0) s.setProgramId(pid);
        } catch (SQLException ignored) {}

        try {
            int cid = rs.getInt("cohort_id");
            if (!rs.wasNull() && cid > 0) s.setCohortId(cid);
        } catch (SQLException ignored) {}

        try {
            s.setAdmissionYear(rs.getString("admission_year"));
            s.setExpectedGraduationYear(rs.getString("expected_graduation_year"));
            s.setProgramName(rs.getString("program_name"));
            s.setProgramCode(rs.getString("program_code"));
            s.setDegree(rs.getString("degree"));
            s.setCohortCode(rs.getString("cohort_code"));
            s.setSchoolName(rs.getString("school_name"));
        } catch (SQLException ignored) {}

        return s;
    }
}

