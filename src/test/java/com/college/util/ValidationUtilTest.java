package com.college.util;

import com.college.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * [T26] Input validation, strict ISO date parsing & decimal scale tests for {@link ValidationUtil}.
 */
public class ValidationUtilTest {

    @Test
    @DisplayName("Verify requireNonEmpty passes valid strings and rejects null or blank")
    void testRequireNonEmpty() {
        assertDoesNotThrow(() -> ValidationUtil.requireNonEmpty("Valid String", "field"));
        assertThrows(ValidationException.class, () -> ValidationUtil.requireNonEmpty(null, "field"));
        assertThrows(ValidationException.class, () -> ValidationUtil.requireNonEmpty("", "field"));
        assertThrows(ValidationException.class, () -> ValidationUtil.requireNonEmpty("   ", "field"));
    }

    @Test
    @DisplayName("Verify requireValidIsoDate strictly parses ISO YYYY-MM-DD and rejects invalid formats or days")
    void testRequireValidIsoDate() {
        LocalDate d = ValidationUtil.requireValidIsoDate("2026-08-15", "testDate");
        assertEquals(2026, d.getYear());
        assertEquals(8, d.getMonthValue());
        assertEquals(15, d.getDayOfMonth());

        // Invalid formats rejected
        assertThrows(ValidationException.class, () -> ValidationUtil.requireValidIsoDate("15-08-2026", "date"));
        assertThrows(ValidationException.class, () -> ValidationUtil.requireValidIsoDate("2026/08/15", "date"));
        assertThrows(ValidationException.class, () -> ValidationUtil.requireValidIsoDate("2026-8-15", "date"));
        assertThrows(ValidationException.class, () -> ValidationUtil.requireValidIsoDate("not-a-date", "date"));

        // Non-existent calendar days rejected (strict mode)
        assertThrows(ValidationException.class, () -> ValidationUtil.requireValidIsoDate("2026-02-30", "date"));
    }

    @Test
    @DisplayName("Verify validateMarks precision: accepts <= 2 decimal places and rejects > 2 places")
    void testValidateMarksDecimalScale() {
        // Valid decimal scales
        assertDoesNotThrow(() -> ValidationUtil.validateMarks(85.0, 100));
        assertDoesNotThrow(() -> ValidationUtil.validateMarks(85.5, 100));
        assertDoesNotThrow(() -> ValidationUtil.validateMarks(85.25, 100));
        assertDoesNotThrow(() -> ValidationUtil.validateMarks(0.0, 100));
        assertDoesNotThrow(() -> ValidationUtil.validateMarks(100.0, 100));

        // Invalid decimal scales (> 2 places)
        assertThrows(ValidationException.class, () -> ValidationUtil.validateMarks(85.123, 100));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateMarks(85.001, 100));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateMarks(99.999, 100));
    }

    @Test
    @DisplayName("Verify validateMarks rejects non-finite, negative, and over-limit marks")
    void testValidateMarksRangesAndFiniteness() {
        assertThrows(ValidationException.class, () -> ValidationUtil.validateMarks(Double.NaN, 100));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateMarks(Double.POSITIVE_INFINITY, 100));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateMarks(Double.NEGATIVE_INFINITY, 100));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateMarks(-0.01, 100));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateMarks(100.01, 100));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateMarks(50.0, 0), "Total marks must be positive");
    }

    @Test
    @DisplayName("Verify requirePositiveId and requirePositiveAmount")
    void testRequirePositiveNumbers() {
        assertDoesNotThrow(() -> ValidationUtil.requirePositiveId(1, "id"));
        assertThrows(ValidationException.class, () -> ValidationUtil.requirePositiveId(0, "id"));
        assertThrows(ValidationException.class, () -> ValidationUtil.requirePositiveId(-5, "id"));

        assertDoesNotThrow(() -> ValidationUtil.requirePositiveAmount(1L, "paise"));
        assertDoesNotThrow(() -> ValidationUtil.requirePositiveAmount(2500000L, "paise"));
        assertThrows(ValidationException.class, () -> ValidationUtil.requirePositiveAmount(0L, "paise"));
        assertThrows(ValidationException.class, () -> ValidationUtil.requirePositiveAmount(-100L, "paise"));
    }

    @Test
    @DisplayName("Verify validateStrongPassword enforces length, upper, lower, digit, and special characters")
    void testValidateStrongPassword() {
        // Valid strong passwords
        assertDoesNotThrow(() -> ValidationUtil.validateStrongPassword("Pass@word123"));
        assertDoesNotThrow(() -> ValidationUtil.validateStrongPassword("Adm!n#2026_Secure"));

        // Null or blank
        assertThrows(ValidationException.class, () -> ValidationUtil.validateStrongPassword(null));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateStrongPassword(""));
        assertThrows(ValidationException.class, () -> ValidationUtil.validateStrongPassword("   "));

        // Too short (< 8 chars)
        assertThrows(ValidationException.class, () -> ValidationUtil.validateStrongPassword("P@1a"));

        // Missing uppercase
        assertThrows(ValidationException.class, () -> ValidationUtil.validateStrongPassword("password@123"));

        // Missing lowercase
        assertThrows(ValidationException.class, () -> ValidationUtil.validateStrongPassword("PASSWORD@123"));

        // Missing digit
        assertThrows(ValidationException.class, () -> ValidationUtil.validateStrongPassword("Password@Special"));

        // Missing special character
        assertThrows(ValidationException.class, () -> ValidationUtil.validateStrongPassword("Password123"));
    }
}

