package com.college.dao;

import com.college.config.DBConnection;
import com.college.exception.DataAccessException;
import com.college.model.Department;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the {@code departments} table.
 *
 * <p>Handles department persistence, lookups by code or ID, active status toggling,
 * and listing of academic departments.</p>
 */
public class DepartmentDAO {

    /**
     * Inserts a new department.
     *
     * @param conn database connection
     * @param dept department entity to insert
     * @return the persisted department with generated ID
     * @throws DataAccessException if a database error occurs
     */
    public Department insert(Connection conn, Department dept) {
        String sqlFull = "INSERT INTO departments (code, name, school, description, head_of_department, contact_email, contact_phone, active) " +
                         "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sqlFull, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, dept.getCode());
            pstmt.setString(2, dept.getName());
            pstmt.setString(3, dept.getSchool() != null ? dept.getSchool() : "School of Engineering");
            pstmt.setString(4, dept.getDescription());
            pstmt.setString(5, dept.getHeadOfDepartment());
            pstmt.setString(6, dept.getContactEmail());
            pstmt.setString(7, dept.getContactPhone());
            pstmt.setInt(8, dept.isActive() ? 1 : 0);

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    dept.setId(rs.getInt(1));
                }
            }
            return dept;
        } catch (SQLException e) {
            String sqlLegacy = "INSERT INTO departments (code, name, active) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlLegacy, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, dept.getCode());
                pstmt.setString(2, dept.getName());
                pstmt.setInt(3, dept.isActive() ? 1 : 0);
                pstmt.executeUpdate();
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        dept.setId(rs.getInt(1));
                    }
                }
                return dept;
            } catch (SQLException ex) {
                throw new DataAccessException("Failed to insert department '" + dept.getCode() + "': " + ex.getMessage(), ex);
            }
        }
    }

    /**
     * Convenience method to insert a department using a managed connection.
     *
     * @param dept department entity to insert
     * @return the persisted department
     */
    public Department insert(Department dept) {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, dept);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a department by primary key ID.
     *
     * @param conn database connection
     * @param id department ID
     * @return department entity or {@code null} if not found
     */
    public Department findById(Connection conn, int id) {
        String sql = "SELECT * FROM departments WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find department by ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find a department by ID using a managed connection.
     *
     * @param id department ID
     * @return department entity or {@code null} if not found
     */
    public Department findById(int id) {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, id);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a department by unique code (e.g. "CS").
     *
     * @param conn database connection
     * @param code department code
     * @return department entity or {@code null} if not found
     */
    public Department findByCode(Connection conn, String code) {
        String sql = "SELECT * FROM departments WHERE code = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, code);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find department by code '" + code + "': " + e.getMessage(), e);
        }
    }

    public Department findByCode(String code) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByCode(conn, code);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public List<Department> findAll(Connection conn) {
        String sql = "SELECT * FROM departments ORDER BY code";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<Department> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve all departments: " + e.getMessage(), e);
        }
    }

    public List<Department> findAll() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAll(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public List<Department> findAllActive(Connection conn) {
        String sql = "SELECT * FROM departments WHERE active = 1 ORDER BY code";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<Department> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve active departments: " + e.getMessage(), e);
        }
    }

    public List<Department> findAllActive() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAllActive(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public List<Department> findBySchool(Connection conn, String school) {
        String sql = "SELECT * FROM departments WHERE school = ? ORDER BY code";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, school);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<Department> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve departments by school: " + e.getMessage(), e);
        }
    }

    public List<Department> findBySchool(String school) {
        try (Connection conn = DBConnection.getConnection()) {
            return findBySchool(conn, school);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public List<String> findAllSchools(Connection conn) {
        String sql = "SELECT DISTINCT school FROM departments WHERE school IS NOT NULL AND TRIM(school) != '' ORDER BY school";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<String> list = new ArrayList<>();
            while (rs.next()) {
                list.add(rs.getString("school"));
            }
            return list;
        } catch (SQLException e) {
            return List.of("School of Engineering");
        }
    }

    public List<String> findAllSchools() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAllSchools(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public int countAssociatedPrograms(Connection conn, int deptId) {
        String sql = "SELECT COUNT(*) FROM academic_programs WHERE department_id = ? AND active = 1";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, deptId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    public int countAssociatedPrograms(int deptId) {
        try (Connection conn = DBConnection.getConnection()) {
            return countAssociatedPrograms(conn, deptId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public int countAssociatedFaculty(Connection conn, int deptId) {
        String sql = "SELECT COUNT(*) FROM faculty WHERE department_id = ? AND active = 1";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, deptId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    public int countAssociatedFaculty(int deptId) {
        try (Connection conn = DBConnection.getConnection()) {
            return countAssociatedFaculty(conn, deptId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public int countAssociatedStudents(Connection conn, int deptId) {
        String sql = "SELECT COUNT(*) FROM students WHERE department_id = ? AND active = 1";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, deptId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    public int countAssociatedStudents(int deptId) {
        try (Connection conn = DBConnection.getConnection()) {
            return countAssociatedStudents(conn, deptId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public int countAssociatedCourses(Connection conn, int deptId) {
        String sql = "SELECT COUNT(*) FROM courses WHERE department_id = ? AND active = 1";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, deptId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            return 0;
        }
    }

    public int countAssociatedCourses(int deptId) {
        try (Connection conn = DBConnection.getConnection()) {
            return countAssociatedCourses(conn, deptId);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public boolean canDeactivate(int deptId) {
        try (Connection conn = DBConnection.getConnection()) {
            return countAssociatedPrograms(conn, deptId) == 0 &&
                   countAssociatedFaculty(conn, deptId) == 0 &&
                   countAssociatedStudents(conn, deptId) == 0 &&
                   countAssociatedCourses(conn, deptId) == 0;
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public boolean update(Connection conn, Department dept) {
        String sqlFull = "UPDATE departments SET code = ?, name = ?, school = ?, description = ?, " +
                         "head_of_department = ?, contact_email = ?, contact_phone = ?, active = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sqlFull)) {
            pstmt.setString(1, dept.getCode());
            pstmt.setString(2, dept.getName());
            pstmt.setString(3, dept.getSchool() != null ? dept.getSchool() : "School of Engineering");
            pstmt.setString(4, dept.getDescription());
            pstmt.setString(5, dept.getHeadOfDepartment());
            pstmt.setString(6, dept.getContactEmail());
            pstmt.setString(7, dept.getContactPhone());
            pstmt.setInt(8, dept.isActive() ? 1 : 0);
            pstmt.setInt(9, dept.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            String sqlLegacy = "UPDATE departments SET code = ?, name = ?, active = ? WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlLegacy)) {
                pstmt.setString(1, dept.getCode());
                pstmt.setString(2, dept.getName());
                pstmt.setInt(3, dept.isActive() ? 1 : 0);
                pstmt.setInt(4, dept.getId());
                return pstmt.executeUpdate() > 0;
            } catch (SQLException ex) {
                throw new DataAccessException("Failed to update department ID " + dept.getId() + ": " + ex.getMessage(), ex);
            }
        }
    }

    public boolean update(Department dept) {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, dept);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    public boolean setActive(Connection conn, int id, boolean active) {
        String sql = "UPDATE departments SET active = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, active ? 1 : 0);
            pstmt.setInt(2, id);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to set active status for department ID " + id + ": " + e.getMessage(), e);
        }
    }

    public boolean setActive(int id, boolean active) {
        try (Connection conn = DBConnection.getConnection()) {
            return setActive(conn, id, active);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    private Department mapRow(ResultSet rs) throws SQLException {
        Department dept = new Department();
        dept.setId(rs.getInt("id"));
        dept.setCode(rs.getString("code"));
        dept.setName(rs.getString("name"));
        dept.setActive(rs.getInt("active") == 1);
        dept.setCreatedAt(rs.getString("created_at"));
        try {
            String sch = rs.getString("school");
            if (sch != null) dept.setSchool(sch);
            dept.setDescription(rs.getString("description"));
            dept.setHeadOfDepartment(rs.getString("head_of_department"));
            dept.setContactEmail(rs.getString("contact_email"));
            dept.setContactPhone(rs.getString("contact_phone"));
        } catch (SQLException ignored) {
            // Extended columns not present in result set
        }
        return dept;
    }
}

