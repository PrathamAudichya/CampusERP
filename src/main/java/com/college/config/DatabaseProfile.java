package com.college.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Enumeration of database profiles in Campus ERP.
 *
 * <p>Enforces strict physical separation between runtime databases:
 * <ul>
 *   <li>{@link #STANDARD}: Default institutional database stored under
 *       {@code %LOCALAPPDATA%\CampusERP\database\campuserp.db}. Contains clean schema and
 *       V1–V11 migrations. No synthetic demo seeds.</li>
 *   <li>{@link #DEMO}: Synthetic demonstration database stored under
 *       {@code %LOCALAPPDATA%\CampusERP\demo\campuserp-demo.db}. Populated with demo records.</li>
 *   <li>{@link #TEST}: Isolated disposable databases for JUnit test suites.</li>
 *   <li>{@link #HISTORICAL}: Protected Milestone 1 baseline at {@code data/college.db}.
 *       Read-only historical reference; migrations and writes are prohibited.</li>
 * </ul>
 * </p>
 */
public enum DatabaseProfile {

    STANDARD(
            "standard",
            "STANDARD DATABASE",
            "Production institutional records",
            true,   // allowsMigrations
            false,  // allowsSyntheticSeed
            true    // allowsWrites
    ),

    DEMO(
            "demo",
            "DEMO DATABASE",
            "Synthetic demonstration records for feature preview",
            true,   // allowsMigrations
            true,   // allowsSyntheticSeed
            true    // allowsWrites
    ),

    TEST(
            "test",
            "TEST DATABASE",
            "Disposable test fixture",
            true,   // allowsMigrations
            true,   // allowsSyntheticSeed
            true    // allowsWrites
    ),

    HISTORICAL(
            "historical",
            "HISTORICAL BASELINE",
            "Protected Milestone 1 historical snapshot (SHA-256 locked)",
            false,  // allowsMigrations
            false,  // allowsSyntheticSeed
            false   // allowsWrites
    );

    private final String modeName;
    private final String uiBadge;
    private final String description;
    private final boolean allowsMigrations;
    private final boolean allowsSyntheticSeed;
    private final boolean allowsWrites;

    DatabaseProfile(String modeName, String uiBadge, String description,
                    boolean allowsMigrations, boolean allowsSyntheticSeed, boolean allowsWrites) {
        this.modeName = modeName;
        this.uiBadge = uiBadge;
        this.description = description;
        this.allowsMigrations = allowsMigrations;
        this.allowsSyntheticSeed = allowsSyntheticSeed;
        this.allowsWrites = allowsWrites;
    }

    public String getModeName() {
        return modeName;
    }

    public String getUiBadge() {
        return uiBadge;
    }

    public String getDescription() {
        return description;
    }

    public boolean allowsMigrations() {
        return allowsMigrations;
    }

    public boolean allowsSyntheticSeed() {
        return allowsSyntheticSeed;
    }

    public boolean allowsWrites() {
        return allowsWrites;
    }

    /**
     * Resolves the canonical default file system path for this profile.
     *
     * @return absolute, normalized {@link Path}
     */
    public Path resolveDefaultPath() {
        switch (this) {
            case STANDARD: {
                String localAppData = System.getenv("LOCALAPPDATA");
                if (localAppData != null && !localAppData.trim().isEmpty()) {
                    return Paths.get(localAppData, "CampusERP", "database", "campuserp.db").toAbsolutePath().normalize();
                }
                String userHome = System.getProperty("user.home", ".");
                return Paths.get(userHome, ".campuserp", "database", "campuserp.db").toAbsolutePath().normalize();
            }
            case DEMO: {
                String localAppData = System.getenv("LOCALAPPDATA");
                if (localAppData != null && !localAppData.trim().isEmpty()) {
                    return Paths.get(localAppData, "CampusERP", "demo", "campuserp-demo.db").toAbsolutePath().normalize();
                }
                String userHome = System.getProperty("user.home", ".");
                return Paths.get(userHome, ".campuserp", "demo", "campuserp-demo.db").toAbsolutePath().normalize();
            }
            case TEST: {
                String tmpDir = System.getProperty("java.io.tmpdir", ".");
                return Paths.get(tmpDir, "CampusERP-tests", "test-college.db").toAbsolutePath().normalize();
            }
            case HISTORICAL:
            default:
                return Paths.get("data", "college.db").toAbsolutePath().normalize();
        }
    }

    /**
     * Parses a string representation of the database mode into a {@link DatabaseProfile}.
     *
     * @param mode CLI or property mode string (e.g., "standard", "demo")
     * @return matching {@link DatabaseProfile}
     * @throws IllegalArgumentException if the mode string is null, empty, or unrecognized
     */
    public static DatabaseProfile fromModeString(String mode) {
        if (mode == null || mode.trim().isEmpty()) {
            throw new IllegalArgumentException("Database mode cannot be null or empty. Valid modes are: standard, demo");
        }
        String normalized = mode.trim().toLowerCase(Locale.ROOT);
        switch (normalized) {
            case "standard":
            case "std":
            case "prod":
            case "production":
                return STANDARD;
            case "demo":
            case "sample":
            case "preview":
                return DEMO;
            case "test":
                return TEST;
            case "historical":
            case "baseline":
                return HISTORICAL;
            default:
                throw new IllegalArgumentException(
                        "Unknown database mode '" + mode + "'. Valid options are: 'standard' or 'demo'. " +
                        "Example: java -jar CampusERP.jar --db-mode=standard"
                );
        }
    }
}
