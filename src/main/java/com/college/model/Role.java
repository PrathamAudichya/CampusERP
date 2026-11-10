package com.college.model;

/**
 * Enumeration of user roles for Role-Based Access Control (RBAC).
 * Maps directly to the CHECK constraint on users.role column:
 * CHECK(role IN ('ADMIN', 'FACULTY', 'STUDENT'))
 */
public enum Role {
    ADMIN,
    FACULTY,
    STUDENT
}

