package com.college;

import com.college.config.AppConfig;
import com.college.config.DBConnection;
import com.college.config.DatabaseInitializer;
import com.college.config.DatabaseProfile;
import com.college.service.AuthService;
import com.college.ui.LoginFrame;
import com.college.ui.theme.CampusTheme;

import javax.swing.*;

/**
 * Main application entry point for Campus ERP (College Management System).
 *
 * Configures system look and feel, verifies database initialization,
 * supports headless administration workflows, and launches the presentation layer
 * (LoginFrame) on the Swing Event Dispatch Thread (EDT).
 */
public class App {

    /**
     * Application entry point.
     *
     * @param args command-line arguments:
     *             --db-mode=standard|demo : Select active database profile
     *             --setup-admin &lt;username&gt; : Secure interactive initial administrator setup
     *             --init-schema-only : Initializes database schema only without demo seed records
     */
    public static void main(String[] args) {
        // CLI database mode and target path processing
        configureDatabaseFromArgs(args);

        // Headless CLI argument processing
        boolean initSchemaOnly = false;
        boolean setupAdmin = false;
        String setupAdminUser = null;

        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if ("--init-schema-only".equalsIgnoreCase(args[i])) {
                    initSchemaOnly = true;
                } else if ("--setup-admin".equalsIgnoreCase(args[i])) {
                    setupAdmin = true;
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        setupAdminUser = args[++i];
                    }
                }
            }
        }

        if (setupAdmin) {
            String[] adminArgs = setupAdminUser != null ? new String[]{"--setup-admin", setupAdminUser} : new String[]{"--setup-admin"};
            int exitCode = handleSetupAdminCli(adminArgs, System.console());
            System.exit(exitCode);
            return;
        }

        if (initSchemaOnly) {
            try {
                DatabaseInitializer.initializeSchemaOnly();
                System.out.println("Clean database schema initialized successfully (no demo seed data).");
                System.exit(0);
            } catch (Exception e) {
                System.err.println("Failed to initialize database schema: " + e.getMessage());
                System.exit(1);
            }
            return;
        }

        DatabaseProfile activeProfile = DBConnection.getActiveProfile();
        String activeDbPath = DBConnection.getActiveDatabasePath();

        System.out.println("================================================================================");
        System.out.println(AppConfig.APP_NAME + " v" + AppConfig.APP_VERSION);
        System.out.println("[PROFILE: " + activeProfile.getUiBadge() + "] Path: " + activeDbPath);
        System.out.println("[DESCRIPTION] " + activeProfile.getDescription());
        if (activeProfile == DatabaseProfile.DEMO) {
            System.out.println("[SAFETY] Demo database is completely isolated from standard institutional records.");
        }
        System.out.println("================================================================================");
        System.out.println("Initializing database and presentation layer...");

        // Inspect and log attached database file path
        try (java.sql.Connection conn = DBConnection.getConnection();
             java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("PRAGMA database_list")) {
            while (rs.next()) {
                System.out.println("Attached Database [" + rs.getString("name") + "]: " + rs.getString("file"));
            }
        } catch (Exception e) {
            System.err.println("Notice: Could not query PRAGMA database_list: " + e.getMessage());
        }

        // Configure global Campus ERP institutional theme and look-and-feel
        CampusTheme.installGlobalTheme();

        // Initialize database schema and versioned migrations
        try {
            DatabaseInitializer.initialize(activeProfile);
            System.out.println("Database schema and migrations verified successfully for " + activeProfile.name() + " profile.");
        } catch (Exception e) {
            System.err.println("CRITICAL: Database initialization / migration failed: " + e.getMessage());
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,
                    "Campus ERP Fatal Error:\nDatabase initialization or schema migration failed.\n\n" + e.getMessage(),
                    "Database Migration Failure",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }

        // Launch UI on Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        });
    }

    /**
     * Handles CLI administrator provisioning without exposing passwords in process arguments.
     * Enforces interactive non-echoing console input, confirmation, complexity validation, and char[] zeroing.
     *
     * @param args    CLI arguments
     * @param console system console instance
     * @return 0 on success, non-zero on failure
     */
    public static int handleSetupAdminCli(String[] args, java.io.Console console) {
        if (args == null || args.length < 1 || !"--setup-admin".equalsIgnoreCase(args[0])) {
            return 1;
        }

        // Strictly reject passwords passed as command-line arguments to prevent process-table/history exposure
        if (args.length > 2) {
            System.err.println("Error: Supplying passwords via command-line arguments is strictly prohibited for security.");
            System.err.println("Usage: java -jar CampusERP.jar --setup-admin <username>");
            return 1;
        }

        if (args.length < 2) {
            System.err.println("Usage: java -jar CampusERP.jar --setup-admin <username>");
            return 1;
        }

        String username = args[1].trim();
        if (username.isEmpty()) {
            System.err.println("Error: Username cannot be blank.");
            return 1;
        }

        if (console == null) {
            System.err.println("Error: Secure interactive console is unavailable. Cannot securely prompt for password without echoing.");
            System.err.println("To provision an initial administrator, please run this command from an interactive terminal,");
            System.err.println("or launch the application without arguments to use the graphical first-run administrator setup.");
            return 1;
        }

        char[] passChars = console.readPassword("Enter initial administrator password for '%s': ", username);
        char[] confirmChars = console.readPassword("Confirm administrator password: ");

        try {
            if (passChars == null || passChars.length == 0) {
                System.err.println("Error: Password cannot be empty.");
                return 1;
            }
            if (confirmChars == null || !java.util.Arrays.equals(passChars, confirmChars)) {
                System.err.println("Error: Passwords do not match.");
                return 1;
            }

            String rawPassword = new String(passChars);
            try {
                DatabaseInitializer.initialize();
                AuthService authService = new AuthService();
                authService.provisionInitialAdmin(username, rawPassword);
                System.out.println("Initial administrator account '" + username + "' provisioned successfully.");
                return 0;
            } catch (Exception e) {
                System.err.println("Failed to provision initial administrator: " + e.getMessage());
                return 1;
            }
        } finally {
            if (passChars != null) {
                java.util.Arrays.fill(passChars, ' ');
            }
            if (confirmChars != null) {
                java.util.Arrays.fill(confirmChars, ' ');
            }
        }
    }

    /**
     * Parses command-line arguments to explicitly configure the database target path and profile.
     * Supports --db-mode=standard|demo, --demo, --standard, and --db &lt;path&gt;.
     * Misspelled or unknown modes will terminate with an error message and exit code 1.
     *
     * @param args Command-line arguments passed to the application
     */
    public static void configureDatabaseFromArgs(String[] args) {
        if (args == null) {
            return;
        }

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg == null) continue;

            if (arg.startsWith("--db-mode=")) {
                String mode = arg.substring("--db-mode=".length()).trim();
                try {
                    DatabaseProfile profile = DatabaseProfile.fromModeString(mode);
                    DBConnection.setActiveProfile(profile);
                    System.setProperty("college.db.path", profile.resolveDefaultPath().toString());
                } catch (IllegalArgumentException e) {
                    System.err.println("Error: " + e.getMessage());
                    System.err.println("Usage: java -jar CampusERP.jar [--db-mode=standard|demo] [--db <path>]");
                    System.exit(1);
                }
            } else if ("--db-mode".equalsIgnoreCase(arg) && i + 1 < args.length) {
                String mode = args[++i].trim();
                try {
                    DatabaseProfile profile = DatabaseProfile.fromModeString(mode);
                    DBConnection.setActiveProfile(profile);
                    System.setProperty("college.db.path", profile.resolveDefaultPath().toString());
                } catch (IllegalArgumentException e) {
                    System.err.println("Error: " + e.getMessage());
                    System.err.println("Usage: java -jar CampusERP.jar [--db-mode=standard|demo] [--db <path>]");
                    System.exit(1);
                }
            } else if ("--demo".equalsIgnoreCase(arg)) {
                DBConnection.setActiveProfile(DatabaseProfile.DEMO);
                System.setProperty("college.db.path", DatabaseProfile.DEMO.resolveDefaultPath().toString());
            } else if ("--standard".equalsIgnoreCase(arg) || "--production".equalsIgnoreCase(arg) || "--prod".equalsIgnoreCase(arg)) {
                DBConnection.setActiveProfile(DatabaseProfile.STANDARD);
                System.setProperty("college.db.path", DatabaseProfile.STANDARD.resolveDefaultPath().toString());
            } else if ("--db".equalsIgnoreCase(arg) && i + 1 < args.length) {
                String customPath = args[++i];
                System.setProperty("college.db.path", customPath);
            }
        }
    }
}
