package com.college.dao;

import com.college.config.DBConnection;
import com.college.exception.DataAccessException;
import com.college.model.FeeStructure;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for the {@code fee_structures} table.
 *
 * <p>Handles fee schedule definitions, department lookups, integer paise fee amounts,
 * and academic year / semester fee structure queries.</p>
 */
public class FeeStructureDAO {

    private static final String BASE_SELECT =
        "SELECT fs.id, fs.department_id, fs.semester, fs.fee_type, fs.amount_paise, " +
        "       fs.academic_year, fs.due_date, fs.active, fs.created_at, " +
        "       d.name AS department_name " +
        "FROM fee_structures fs " +
        "LEFT JOIN departments d ON fs.department_id = d.id ";

    /**
     * Inserts a new fee structure.
     *
     * @param conn database connection
     * @param fs fee structure entity to insert
     * @return persisted entity with generated ID
     * @throws DataAccessException if a database error occurs
     */
    public FeeStructure insert(Connection conn, FeeStructure fs) {
        String sql = "INSERT INTO fee_structures (department_id, semester, fee_type, amount_paise, academic_year, due_date, active) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setInt(1, fs.getDepartmentId());
            pstmt.setString(2, fs.getSemester());
            pstmt.setString(3, fs.getFeeType());
            pstmt.setLong(4, fs.getAmountPaise());
            pstmt.setString(5, fs.getAcademicYear());
            pstmt.setString(6, fs.getDueDate());
            pstmt.setInt(7, fs.isActive() ? 1 : 0);

            pstmt.executeUpdate();

            try (ResultSet rs = pstmt.getGeneratedKeys()) {
                if (rs.next()) {
                    fs.setId(rs.getInt(1));
                }
            }
            return fs;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert fee structure: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to insert a fee structure using a managed connection.
     *
     * @param fs fee structure entity to insert
     * @return persisted entity
     */
    public FeeStructure insert(FeeStructure fs) {
        try (Connection conn = DBConnection.getConnection()) {
            return insert(conn, fs);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a fee structure by primary key ID, populating departmentName.
     *
     * @param conn database connection
     * @param id fee structure ID
     * @return fee structure entity or {@code null} if not found
     */
    public FeeStructure findById(Connection conn, int id) {
        String sql = BASE_SELECT + "WHERE fs.id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find fee structure by ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find a fee structure by ID using a managed connection.
     *
     * @param id fee structure ID
     * @return fee structure entity or {@code null} if not found
     */
    public FeeStructure findById(int id) {
        try (Connection conn = DBConnection.getConnection()) {
            return findById(conn, id);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a fee structure by unique key (department_id, semester, fee_type, academic_year).
     *
     * @param conn database connection
     * @param departmentId department ID
     * @param semester semester name/number
     * @param feeType fee category (TUITION, LAB, LIBRARY, etc.)
     * @param academicYear academic year (e.g., "2026-2027")
     * @return fee structure entity or {@code null} if not found
     */
    public FeeStructure findByDeptSemesterTypeYear(Connection conn, int departmentId, String semester,
                                                  String feeType, String academicYear) {
        String sql = BASE_SELECT + "WHERE fs.department_id = ? AND fs.semester = ? AND fs.fee_type = ? AND fs.academic_year = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, departmentId);
            pstmt.setString(2, semester);
            pstmt.setString(3, feeType);
            pstmt.setString(4, academicYear);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
                return null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find fee structure: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find a fee structure by unique key using a managed connection.
     *
     * @param departmentId department ID
     * @param semester semester
     * @param feeType fee type
     * @param academicYear academic year
     * @return fee structure entity or {@code null} if not found
     */
    public FeeStructure findByDeptSemesterTypeYear(int departmentId, String semester,
                                                  String feeType, String academicYear) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByDeptSemesterTypeYear(conn, departmentId, semester, feeType, academicYear);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all fee structures for a department and academic year.
     *
     * @param conn database connection
     * @param departmentId department ID
     * @param academicYear academic year
     * @return list of fee structures
     */
    public List<FeeStructure> findByDepartmentAndYear(Connection conn, int departmentId, String academicYear) {
        String sql = BASE_SELECT + "WHERE fs.department_id = ? AND fs.academic_year = ? ORDER BY fs.semester, fs.fee_type";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, departmentId);
            pstmt.setString(2, academicYear);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<FeeStructure> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find fee structures for department " + departmentId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find fee structures by department and year using a managed connection.
     *
     * @param departmentId department ID
     * @param academicYear academic year
     * @return list of fee structures
     */
    public List<FeeStructure> findByDepartmentAndYear(int departmentId, String academicYear) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByDepartmentAndYear(conn, departmentId, academicYear);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves fee structures for a department, semester, and academic year.
     *
     * @param conn database connection
     * @param departmentId department ID
     * @param semester semester
     * @param academicYear academic year
     * @return list of fee structures
     */
    public List<FeeStructure> findByDepartmentSemesterYear(Connection conn, int departmentId,
                                                          String semester, String academicYear) {
        String sql = BASE_SELECT + "WHERE fs.department_id = ? AND fs.semester = ? AND fs.academic_year = ? " +
                     "ORDER BY fs.fee_type";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, departmentId);
            pstmt.setString(2, semester);
            pstmt.setString(3, academicYear);
            try (ResultSet rs = pstmt.executeQuery()) {
                List<FeeStructure> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find fee structures for department and semester: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to find fee structures by department, semester, and year using a managed connection.
     *
     * @param departmentId department ID
     * @param semester semester
     * @param academicYear academic year
     * @return list of fee structures
     */
    public List<FeeStructure> findByDepartmentSemesterYear(int departmentId, String semester, String academicYear) {
        try (Connection conn = DBConnection.getConnection()) {
            return findByDepartmentSemesterYear(conn, departmentId, semester, academicYear);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all fee structures.
     *
     * @param conn database connection
     * @return list of all fee structures
     */
    public List<FeeStructure> findAll(Connection conn) {
        String sql = BASE_SELECT + "ORDER BY fs.academic_year DESC, fs.department_id, fs.semester";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<FeeStructure> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve all fee structures: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to retrieve all fee structures using a managed connection.
     *
     * @return list of all fee structures
     */
    public List<FeeStructure> findAll() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAll(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all active fee structures.
     *
     * @param conn database connection
     * @return list of active fee structures
     */
    public List<FeeStructure> findAllActive(Connection conn) {
        String sql = BASE_SELECT + "WHERE fs.active = 1 ORDER BY fs.academic_year DESC, fs.department_id, fs.semester";
        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            List<FeeStructure> list = new ArrayList<>();
            while (rs.next()) {
                list.add(mapRow(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve active fee structures: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to retrieve active fee structures using a managed connection.
     *
     * @return list of active fee structures
     */
    public List<FeeStructure> findAllActive() {
        try (Connection conn = DBConnection.getConnection()) {
            return findAllActive(conn);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Updates an existing fee structure's details.
     *
     * @param conn database connection
     * @param fs fee structure entity
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean update(Connection conn, FeeStructure fs) {
        String sql = "UPDATE fee_structures SET department_id = ?, semester = ?, fee_type = ?, " +
                     "                          amount_paise = ?, academic_year = ?, due_date = ?, active = ? " +
                     "WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, fs.getDepartmentId());
            pstmt.setString(2, fs.getSemester());
            pstmt.setString(3, fs.getFeeType());
            pstmt.setLong(4, fs.getAmountPaise());
            pstmt.setString(5, fs.getAcademicYear());
            pstmt.setString(6, fs.getDueDate());
            pstmt.setInt(7, fs.isActive() ? 1 : 0);
            pstmt.setInt(8, fs.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update fee structure ID " + fs.getId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to update a fee structure using a managed connection.
     *
     * @param fs fee structure entity
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean update(FeeStructure fs) {
        try (Connection conn = DBConnection.getConnection()) {
            return update(conn, fs);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Sets the active status of a fee structure (soft delete / reactivate).
     *
     * @param conn database connection
     * @param id fee structure ID
     * @param active new active state
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean setActive(Connection conn, int id, boolean active) {
        String sql = "UPDATE fee_structures SET active = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, active ? 1 : 0);
            pstmt.setInt(2, id);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to set active status for fee structure ID " + id + ": " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method to set fee structure active status using a managed connection.
     *
     * @param id fee structure ID
     * @param active new active state
     * @return {@code true} if updated; {@code false} otherwise
     */
    public boolean setActive(int id, boolean active) {
        try (Connection conn = DBConnection.getConnection()) {
            return setActive(conn, id, active);
        } catch (SQLException e) {
            throw new DataAccessException("Database connection error: " + e.getMessage(), e);
        }
    }

    /**
     * Maps the current row of a ResultSet to a FeeStructure domain model.
     *
     * @param rs result set positioned at a valid row
     * @return populated FeeStructure model
     * @throws SQLException if column reading fails
     */
    private FeeStructure mapRow(ResultSet rs) throws SQLException {
        FeeStructure fs = new FeeStructure();
        fs.setId(rs.getInt("id"));
        fs.setDepartmentId(rs.getInt("department_id"));
        fs.setSemester(rs.getString("semester"));
        fs.setFeeType(rs.getString("fee_type"));
        fs.setAmountPaise(rs.getLong("amount_paise"));
        fs.setAcademicYear(rs.getString("academic_year"));
        fs.setDueDate(rs.getString("due_date"));
        fs.setActive(rs.getInt("active") == 1);
        fs.setCreatedAt(rs.getString("created_at"));
        fs.setDepartmentName(rs.getString("department_name"));
        return fs;
    }
}

