package com.college.config;

/**
 * Application-wide configuration constants.
 * Provides default academic year, currency, and system-level settings.
 */
public final class AppConfig {

    private AppConfig() {
        // Utility class — prevent instantiation
    }

    /** Default academic year for seed data and new records. */
    public static final String DEFAULT_ACADEMIC_YEAR = "2026-2027";

    /** Currency code for display and reporting. */
    public static final String CURRENCY_CODE = "INR";

    /** Currency symbol for UI display. */
    public static final String CURRENCY_SYMBOL = "₹";

    /** Number of minor currency units (paise) per major unit (rupee). */
    public static final int PAISE_PER_RUPEE = 100;

    /** Application name displayed in UI title bars and reports. */
    public static final String APP_NAME = "Campus ERP";

    /** Institutional tagline. */
    public static final String APP_TAGLINE = "University Management Platform";

    /** Full window title. */
    public static final String APP_TITLE = "Campus ERP — University Management Platform";

    /** Application version. */
    public static final String APP_VERSION = "2.1.0";

    /** Default database file path (relative to working directory). */
    public static final String DEFAULT_DB_PATH = "data/college-demo.db";

    /** Default attendance eligibility threshold percentage (75.0%). */
    public static final double DEFAULT_ATTENDANCE_THRESHOLD_PERCENT = 75.0;

    /** Weight factor for LATE attendance status (0.5). */
    public static final double LATE_ATTENDANCE_WEIGHT = 0.5;

    /** Minimum password length requirement for strong password policy. */
    public static final int MIN_PASSWORD_LENGTH = 8;

    /** Default maximum student capacity per course section. */
    public static final int DEFAULT_MAX_COURSE_CAPACITY = 60;

    /** Maximum allowed decimal scale for examination marks (2 decimal places). */
    public static final int MAX_MARKS_DECIMAL_SCALE = 2;
}

