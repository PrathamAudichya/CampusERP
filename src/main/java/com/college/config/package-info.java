/**
 * System configuration, database connectivity, connection pooling, and profile management.
 *
 * <p><b>Team Ownership:</b> Member 1 (Lead Architecture &amp; System Infrastructure)</p>
 *
 * <p>Key Responsibilities:
 * <ul>
 *   <li>{@link com.college.config.DatabaseProfile}: Central profile definition (STANDARD, DEMO, TEST, HISTORICAL).</li>
 *   <li>{@link com.college.config.DBConnection}: SQLite JDBC connection factory with WAL mode and PRAGMA enforcement.</li>
 *   <li>{@link com.college.config.DatabaseInitializer}: Schema creation, trigger loading, and versioned migrations V1–V11.</li>
 *   <li>{@link com.college.config.AppConfig}: Institutional constants (academic year, currency INR, password policies).</li>
 * </ul>
 * </p>
 */
package com.college.config;

