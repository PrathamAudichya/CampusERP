package com.college.util;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Pattern;

/**
 * Institutional Username generator and validator according to institutional ERP policy.
 *
 * Requirements:
 * - Exactly two uppercase alphabetic characters followed by twelve numeric digits.
 * - Total length: 14 characters.
 * - Regex: ^[A-Z]{2}[0-9]{12}$
 * - Example: AB123456789012
 * - Uses SecureRandom with global uniqueness verification in users table.
 */
public class UsernameUtil {

    public static final String USERNAME_REGEX = "^[A-Z]{2}[0-9]{12}$";
    private static final Pattern PATTERN = Pattern.compile(USERNAME_REGEX);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_COLLISION_RETRIES = 50;

    private UsernameUtil() {
        // Utility class
    }

    /**
     * Validates whether a given username complies with the institutional format:
     * exactly 2 uppercase letters followed by 12 digits (14 chars total).
     *
     * @param username the username to validate
     * @return true if compliant, false otherwise
     */
    public static boolean isValidInstitutionalUsername(String username) {
        if (username == null) {
            return false;
        }
        return PATTERN.matcher(username.trim()).matches();
    }

    /**
     * Generates a raw candidate username matching ^[A-Z]{2}[0-9]{12}$ without database check.
     *
     * @return 14-character institutional username string
     */
    public static String generateCandidateUsername() {
        char c1 = (char) ('A' + RANDOM.nextInt(26));
        char c2 = (char) ('A' + RANDOM.nextInt(26));
        StringBuilder sb = new StringBuilder(14);
        sb.append(c1).append(c2);
        for (int i = 0; i < 12; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * Generates a guaranteed globally unique institutional username by querying the database.
     * Retries safely on collision up to MAX_COLLISION_RETRIES.
     *
     * @param conn active JDBC connection
     * @return unique 14-character institutional username
     * @throws SQLException if a database error occurs or maximum retries are exceeded
     */
    public static String generateUniqueUsername(Connection conn) throws SQLException {
        if (conn == null) {
            throw new IllegalArgumentException("Database connection cannot be null.");
        }

        String sql = "SELECT 1 FROM users WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int attempt = 0; attempt < MAX_COLLISION_RETRIES; attempt++) {
                String candidate = generateCandidateUsername();
                ps.setString(1, candidate);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return candidate;
                    }
                }
            }
        }
        throw new SQLException("Failed to generate a unique institutional username after " + MAX_COLLISION_RETRIES + " attempts.");
    }

    /**
     * Generates a guaranteed globally unique institutional username using the default database connection.
     *
     * @return unique 14-character institutional username
     * @throws SQLException if a database error occurs
     */
    public static String generateUniqueUsername() throws SQLException {
        try (Connection conn = com.college.config.DBConnection.getConnection()) {
            return generateUniqueUsername(conn);
        }
    }
}

