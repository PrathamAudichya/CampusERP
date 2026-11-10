package com.college.util;

import com.college.exception.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Utility class for input validation, strict ISO date parsing,
 * and BigDecimal-based marks scale validation.
 *
 * Applied consistently across all modules:
 * - Attendance dates, examination dates, admission dates, dates of birth
 * - Faculty joining dates, fee structure due dates, payment dates
 * - Marks obtained precision and range validation
 */
public final class ValidationUtil {

    /** Strict ISO-8601 local date formatter (YYYY-MM-DD). */
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    private ValidationUtil() {
        // Utility class — prevent instantiation
    }

    /**
     * Validates that a string is non-null and non-blank.
     *
     * @param value     the string to validate
     * @param fieldName the field name for error messaging
     * @throws ValidationException if the value is null or blank
     */
    public static void requireNonEmpty(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(fieldName + " cannot be null or empty.");
        }
    }

    /**
     * Parses and validates a date string against strict ISO YYYY-MM-DD format.
     * Rejects lenient parsing, partial dates, and non-standard formats.
     *
     * @param dateStr   the date string to parse
     * @param fieldName the field name for error messaging
     * @return the parsed LocalDate
     * @throws ValidationException if the string is null, blank, or not valid ISO format
     */
    public static LocalDate requireValidIsoDate(String dateStr, String fieldName) {
        requireNonEmpty(dateStr, fieldName);
        try {
            return LocalDate.parse(dateStr.trim(), ISO_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new ValidationException(String.format(
                "Invalid date format for %s: '%s'. Expected strict ISO YYYY-MM-DD.", fieldName, dateStr));
        }
    }

    /**
     * Validates marks obtained against examination constraints.
     * Enforces the two-decimal-place precision rule using exact BigDecimal scale analysis.
     *
     * Rejects:
     * - NaN, +Infinity, -Infinity (non-finite values)
     * - Negative values
     * - Values exceeding totalMarks
     * - Values with more than 2 decimal places (e.g., 85.123)
     *
     * @param marksObtained the marks value to validate
     * @param totalMarks    the maximum marks for the examination
     * @throws ValidationException if marks violate any constraint
     */
    public static void validateMarks(double marksObtained, int totalMarks) {
        if (totalMarks <= 0) {
            throw new ValidationException(
                "Examination total marks must be strictly positive. Received: " + totalMarks);
        }
        if (!Double.isFinite(marksObtained)) {
            throw new ValidationException(
                "Marks obtained must be a finite numeric value. Received: " + marksObtained);
        }
        if (marksObtained < 0.0) {
            throw new ValidationException("Marks obtained cannot be negative: " + marksObtained);
        }
        BigDecimal marksBd = BigDecimal.valueOf(marksObtained).stripTrailingZeros();
        if (marksBd.scale() > 2) {
            throw new ValidationException(String.format(
                "Marks obtained cannot have more than 2 decimal places: %s", marksObtained));
        }
        if (marksObtained > totalMarks) {
            throw new ValidationException(String.format(
                "Marks obtained (%.2f) exceeds examination maximum marks (%d).",
                marksObtained, totalMarks));
        }
    }

    /**
     * Validates that an integer ID is positive.
     *
     * @param id        the ID value to validate
     * @param fieldName the field name for error messaging
     * @throws ValidationException if the ID is not positive
     */
    public static void requirePositiveId(int id, String fieldName) {
        if (id <= 0) {
            throw new ValidationException(fieldName + " must be a positive integer. Received: " + id);
        }
    }

    /**
     * Validates that a long monetary value is strictly positive.
     *
     * @param amountPaise the amount in paise to validate
     * @param fieldName   the field name for error messaging
     * @throws ValidationException if the amount is not positive
     */
    public static void requirePositiveAmount(long amountPaise, String fieldName) {
        if (amountPaise <= 0L) {
            throw new ValidationException(fieldName + " must be strictly positive. Received: " + amountPaise);
        }
    }

    /**
     * Validates password strength against authoritative enterprise security policies.
     *
     * Required criteria:
     * - Minimum 8 characters in length
     * - At least one uppercase letter [A-Z]
     * - At least one lowercase letter [a-z]
     * - At least one numeric digit [0-9]
     * - At least one special / punctuation character
     *
     * @param password plaintext password to validate
     * @throws ValidationException if password fails any complexity rule
     */
    public static void validateStrongPassword(String password) {
        requireNonEmpty(password, "Password");
        if (password.length() < 8) {
            throw new ValidationException("Password must be at least 8 characters long.");
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            } else if (!Character.isWhitespace(c)) {
                hasSpecial = true;
            }
        }

        if (!hasUpper || !hasLower || !hasDigit || !hasSpecial) {
            throw new ValidationException(
                "Password must contain at least one uppercase letter, one lowercase letter, one digit, and one special character.");
        }
    }
}

