package com.college.ui;

import com.college.exception.AuthenticationException;
import com.college.exception.DataAccessException;
import com.college.exception.ValidationException;
import com.college.ui.util.SwingUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test suite verifying UI utility behaviors, safe error sanitization,
 * and user-facing dialog message protection.
 */
public class SwingUtilsTest {

    @Test
    @DisplayName("ValidationException messages must be displayed directly without technical sanitization")
    void testValidationExceptionMessagePreserved() {
        ValidationException ex = new ValidationException("Username must be between 3 and 50 characters.");
        String msg = SwingUtils.getSafeUserErrorMessage(ex, "Failed to update account.");
        assertEquals("Username must be between 3 and 50 characters.", msg);
    }

    @Test
    @DisplayName("AuthenticationException messages must be displayed directly without technical sanitization")
    void testAuthenticationExceptionMessagePreserved() {
        AuthenticationException ex = new AuthenticationException("Current password does not match.");
        String msg = SwingUtils.getSafeUserErrorMessage(ex, "Authentication failed.");
        assertEquals("Current password does not match.", msg);
    }

    @Test
    @DisplayName("DataAccessException with SQL syntax or schema details must be sanitized")
    void testDataAccessExceptionSanitized() {
        SQLException sqlEx = new SQLException("no such table: timetables (code 1 SQLITE_ERROR)");
        DataAccessException daEx = new DataAccessException("Failed to query database: " + sqlEx.getMessage(), sqlEx);

        String msg = SwingUtils.getSafeUserErrorMessage(daEx, "Unable to load timetable records");
        assertEquals("Unable to load timetable records: A database error occurred. Please contact your system administrator.", msg);
        assertFalse(msg.contains("no such table"), "Sanitized message must not reveal raw SQL schema info");
        assertFalse(msg.contains("SQLITE_ERROR"), "Sanitized message must not reveal internal database codes");
    }

    @Test
    @DisplayName("Null or empty exception message must fall back to safe context message")
    void testNullOrEmptyExceptionSanitized() {
        RuntimeException ex = new RuntimeException((String) null);
        String msg = SwingUtils.getSafeUserErrorMessage(ex, "Operation failed");
        assertEquals("Operation failed: An unexpected error occurred. Please try again or check application logs.", msg);
    }

    @Test
    @DisplayName("IllegalArgumentException with business message is displayed cleanly")
    void testIllegalArgumentExceptionBusinessMessage() {
        IllegalArgumentException ex = new IllegalArgumentException("Course code cannot be blank.");
        String msg = SwingUtils.getSafeUserErrorMessage(ex, "Operation failed.");
        assertEquals("Course code cannot be blank.", msg);
    }
}

