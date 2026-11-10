package com.college.exception;

/**
 * Unchecked exception wrapping SQL and data access failures.
 * Thrown by DAO classes when database operations fail.
 * Extends RuntimeException to avoid checked exception compilation contradictions
 * in service method signatures.
 */
public class DataAccessException extends RuntimeException {

    /**
     * Constructs a DataAccessException with a descriptive message.
     *
     * @param message description of the data access failure
     */
    public DataAccessException(String message) {
        super(message);
    }

    /**
     * Constructs a DataAccessException wrapping a root cause.
     *
     * @param message description of the data access failure
     * @param cause   the underlying exception (typically SQLException)
     */
    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}

