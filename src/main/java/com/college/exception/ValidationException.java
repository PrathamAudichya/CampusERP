package com.college.exception;

/**
 * Unchecked exception for business rule validation failures.
 * Thrown when user input or entity state violates application constraints
 * (e.g., invalid date format, marks exceeding maximum, duplicate records).
 */
public class ValidationException extends RuntimeException {

    /**
     * Constructs a ValidationException with a descriptive message.
     *
     * @param message description of the validation failure
     */
    public ValidationException(String message) {
        super(message);
    }

    /**
     * Constructs a ValidationException wrapping a root cause.
     *
     * @param message description of the validation failure
     * @param cause   the underlying exception
     */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

