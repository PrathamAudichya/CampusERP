package com.college.exception;

/**
 * Unchecked exception for authentication and authorization failures.
 * Thrown when login credentials are invalid, sessions expire,
 * or a user lacks the required role for an operation.
 *
 * Extends RuntimeException (unchecked) to avoid checked exception
 * compilation contradictions in service method signatures that use
 * catch (SQLException | RuntimeException ex) patterns.
 */
public class AuthenticationException extends RuntimeException {

    /**
     * Constructs an AuthenticationException with a descriptive message.
     *
     * @param message description of the authentication/authorization failure
     */
    public AuthenticationException(String message) {
        super(message);
    }

    /**
     * Constructs an AuthenticationException wrapping a root cause.
     *
     * @param message description of the authentication/authorization failure
     * @param cause   the underlying exception
     */
    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}

