package com.college.service;

/**
 * Cross-process test helper for verifying SQLite database-level locking
 * when two distinct OS-level JVM processes attempt initial admin provisioning.
 */
public class ConcurrentProcessAdminProvisioner {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: ConcurrentProcessAdminProvisioner <dbPath> <username> <password>");
            System.exit(2);
        }

        String dbPath = args[0];
        String username = args[1];
        String password = args[2];

        System.setProperty("college.db.path", dbPath);

        try {
            AuthService authService = new AuthService();
            authService.provisionInitialAdmin(username, password);
            System.out.println("PROVISION_SUCCESS:" + username);
            System.exit(0);
        } catch (IllegalStateException e) {
            System.err.println("PROVISION_REJECTED_ALREADY_EXISTS:" + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("PROVISION_FAILED:" + e.getClass().getName() + ":" + e.getMessage());
            System.exit(1);
        }
    }
}

