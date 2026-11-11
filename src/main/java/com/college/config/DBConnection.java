package com.college.config;

import com.college.exception.DataAccessException;
import org.sqlite.SQLiteConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Connection factory for SQLite database connections.
 *
 * Configures every connection with:
 * - TransactionMode.IMMEDIATE (WAL exclusive writer lock acquired on setAutoCommit(false))
 * - JournalMode.WAL (concurrent readers do not block writer; writer does not block readers)
 * - PRAGMA foreign_keys = ON
 * - busyTimeout = 5000ms
 *
 * Safely verifies and idempotently ensures parent directories exist using a thread-safe
 * directory cache and directory validation (Files.isDirectory) before establishing connections.
 */
public class DBConnection {

    public static final String DEFAULT_DB_PATH = DatabaseProfile.STANDARD.resolveDefaultPath().toString();

    private static volatile DatabaseProfile activeProfile = DatabaseProfile.STANDARD;

    /**
     * Thread-safe cache of verified parent directory paths.
     * Avoids redundant filesystem checks while correctly supporting
     * multiple distinct database paths configured at runtime or during testing.
     */
    private static final Set<Path> verifiedDirectories = ConcurrentHashMap.newKeySet();

    private DBConnection() {
        // Utility class — prevent instantiation
    }

    /**
     * Decodes percent-encoded octets (%XX) in a URI path according to RFC 3986.
     *
     * Unlike URLDecoder.decode():
     * - Literal '+' characters are preserved as '+' (not converted to spaces).
     * - '%20' is decoded to space ' '.
     * - '%2B' and '%2b' are decoded to '+'.
     * - Multi-byte UTF-8 percent-encoded sequences are correctly decoded.
     * - Malformed percent-encodings (e.g. '%', '%2', '%ZZ') throw IllegalArgumentException.
     *
     * @param s the string to decode
     * @return the percent-decoded string
     * @throws IllegalArgumentException if malformed percent-encoding is encountered
     */
    public static String decodePercent(String s) {
        if (s == null || s.indexOf('%') < 0) {
            return s == null ? "" : s;
        }

        int len = s.length();
        ByteArrayOutputStream baos = new ByteArrayOutputStream(len);

        for (int i = 0; i < len; ) {
            int codePoint = s.codePointAt(i);
            int charCount = Character.charCount(codePoint);

            if (codePoint == '%') {
                if (i + 2 >= len) {
                    throw new IllegalArgumentException(
                        "Incomplete percent-encoding sequence at end of URI path: " + s);
                }
                char h1 = s.charAt(i + 1);
                char h2 = s.charAt(i + 2);
                int d1 = Character.digit(h1, 16);
                int d2 = Character.digit(h2, 16);
                if (d1 < 0 || d2 < 0) {
                    throw new IllegalArgumentException(
                        "Invalid hexadecimal digits in percent-encoding '%" + h1 + h2 + "': " + s);
                }
                int byteVal = (d1 << 4) | d2;
                baos.write(byteVal);
                i += 3;
            } else {
                byte[] bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
                baos.write(bytes, 0, bytes.length);
                i += charCount;
            }
        }

        return baos.toString(StandardCharsets.UTF_8);
    }

    /**
     * Extracts and normalizes the target filesystem path from a SQLite database path or URI.
     *
     * Adheres to the following SQLite URI and path policy:
     * - In-memory databases: ":memory:", "jdbc:sqlite::memory:", or query "mode=memory" return ":memory:".
     * - Relative paths: e.g. "data/college.db" -> returns normalized relative path.
     * - Windows absolute paths: "C:\data\college.db" or "C:/data/college.db" -> preserved.
     * - Unix absolute paths: "/var/data/college.db" -> preserved.
     * - File URIs:
     *     - "file:/var/data/college.db" -> "/var/data/college.db"
     *     - "file:///var/data/college.db" -> "/var/data/college.db"
     *     - "file:///C:/path/database.db" -> "C:/path/database.db"
     *     - "file:/C:/path/database.db" -> "C:/path/database.db"
     *     - "file:C:/path/database.db" -> "C:/path/database.db"
     * - UNC paths:
     *     - "\\server\share\db.db" -> preserved
     *     - "file://server/share/db.db" -> "\\server\share\db.db"
     * - Query parameters (e.g. ?mode=memory, ?cache=shared): parsed and stripped; never passed to Paths.get().
     * - Percent-encoded characters: decoded using RFC 3986 URI rules (preserving literal '+', decoding '%20' and '%2B').
     * - Foreign schemes: rejected with IllegalArgumentException (e.g. http://, ftp://).
     *
     * @param dbPath the raw database path or SQLite URI
     * @return the extracted normalized filesystem path, or ":memory:" for in-memory databases, or "" if blank
     * @throws IllegalArgumentException if an unsupported URI scheme or malformed URI is provided
     */
    public static String extractPathFromUri(String dbPath) {
        if (dbPath == null || dbPath.isBlank()) {
            return "";
        }

        String raw = dbPath.trim();
        if (raw.startsWith("jdbc:sqlite:")) {
            raw = raw.substring("jdbc:sqlite:".length()).trim();
        }

        if (raw.equals(":memory:") || raw.startsWith(":memory:")) {
            return ":memory:";
        }

        // Check for unsupported foreign schemes (e.g. http://, ftp://)
        int colonIdx = raw.indexOf(':');
        if (colonIdx > 0) {
            String potentialScheme = raw.substring(0, colonIdx).toLowerCase();
            // A single character before colon is a Windows drive letter (e.g. C:), not a URI scheme
            if (potentialScheme.length() > 1 && !potentialScheme.equals("file")) {
                throw new IllegalArgumentException(
                    "Unsupported SQLite URI scheme or malformed path: " + dbPath);
            }
        } else if (raw.contains("://")) {
            throw new IllegalArgumentException(
                "Unsupported SQLite URI scheme or malformed path: " + dbPath);
        }

        // Separate query parameters (?query)
        String pathPart;
        String queryPart = "";
        int qIdx = raw.indexOf('?');
        if (qIdx >= 0) {
            pathPart = raw.substring(0, qIdx);
            queryPart = raw.substring(qIdx + 1);
        } else {
            pathPart = raw;
        }

        // Check query parameters for in-memory mode
        if (queryPart.contains("mode=memory") || pathPart.contains(":memory:")) {
            return ":memory:";
        }

        if (pathPart.isBlank()) {
            return "";
        }

        // Percent-decode path using RFC 3986 URI rules (preserving literal '+', decoding '%20' and '%2B')
        String decoded;
        try {
            decoded = decodePercent(pathPart);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed percent-encoding in database path: " + dbPath + " (" + e.getMessage() + ")", e);
        }

        if (decoded.startsWith("file:")) {
            String body = decoded.substring("file:".length());
            if (body.isBlank()) {
                return "";
            }
            if (body.equals(":memory:")) {
                return ":memory:";
            }

            // Handle localhost authority (file://localhost/path -> /path)
            if (body.startsWith("//localhost/")) {
                body = body.substring("//localhost".length());
            }

            // Handle UNC paths (file://server/share/file.db -> \\server\share\file.db)
            if (body.startsWith("//") && !body.startsWith("///")) {
                return "\\\\" + body.substring(2).replace('/', '\\');
            }

            // Strip file:/// triple-slash -> /...
            if (body.startsWith("///")) {
                body = body.substring(2); // leaves single leading slash
            }

            // Strip leading slash before Windows drive letter (e.g. /C:/path -> C:/path)
            if (body.length() >= 3
                    && body.charAt(0) == '/'
                    && Character.isLetter(body.charAt(1))
                    && body.charAt(2) == ':') {
                body = body.substring(1);
            }

            return body;
        }

        return decoded;
    }

    /**
     * Safely verifies that the parent directory of the database file exists and is indeed a directory.
     * Uses a thread-safe Set cache to avoid redundant filesystem calls while correctly handling
     * multiple distinct database paths configured at runtime or during testing.
     *
     * Correctly handles:
     * - Standard relative paths (e.g., "data/college.db")
     * - Standard absolute paths (e.g., "C:/data/college.db", "/var/data/college.db")
     * - In-memory databases (e.g., ":memory:", "jdbc:sqlite::memory:")
     * - Named shared-memory URIs (e.g., "jdbc:sqlite:file:testdb_123?mode=memory&cache=shared")
     * - File URIs with query parameters (e.g., "jdbc:sqlite:file:data/college.db?cache=shared")
     * - Percent-encoded file URIs (e.g., "file:///C:/my%20data/college.db")
     * - UNC network share paths (e.g., "\\server\share\db.db", "file://server/share/db.db")
     *
     * Query parameters and URI schemes are never passed directly to Paths.get().
     *
     * @param dbPath the database file path or URI to verify the parent directory for
     * @throws IllegalArgumentException if an unsupported URI scheme is supplied
     * @throws DataAccessException if the parent path exists but is not a directory,
     *                             or if directory creation fails
     */
    public static synchronized void ensureDirectoryExists(String dbPath) {
        if (dbPath == null || dbPath.isBlank()) {
            return;
        }

        String path = extractPathFromUri(dbPath);
        if (path == null || path.isBlank() || path.equals(":memory:")) {
            return;
        }

        Path dbFilePath;
        try {
            dbFilePath = Paths.get(path).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new DataAccessException("Invalid SQLite database path syntax: " + dbPath, e);
        }

        Path parentDir = dbFilePath.getParent();
        if (parentDir != null && verifiedDirectories.add(parentDir)) {
            if (Files.exists(parentDir)) {
                if (!Files.isDirectory(parentDir)) {
                    verifiedDirectories.remove(parentDir);
                    throw new DataAccessException(
                        "Database parent path exists but is not a directory: " + parentDir);
                }
            } else {
                try {
                    Files.createDirectories(parentDir);
                } catch (IOException e) {
                    verifiedDirectories.remove(parentDir);
                    throw new DataAccessException(
                        "Failed to create database directory: " + parentDir, e);
                }
            }
        }
    }

    /**
     * Gets the currently active database profile.
     *
     * @return active {@link DatabaseProfile}
     */
    public static DatabaseProfile getActiveProfile() {
        return activeProfile;
    }

    /**
     * Sets the active database profile.
     *
     * @param profile database profile to activate (must not be null)
     */
    public static void setActiveProfile(DatabaseProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("DatabaseProfile cannot be null");
        }
        activeProfile = profile;
    }

    /**
     * Resolves the active database file path.
     * Evaluates in precedence order:
     * <ol>
     *   <li>System property {@code college.db.path} (if set and non-blank)</li>
     *   <li>System property {@code college.db} (legacy compatibility override, if set and non-blank)</li>
     *   <li>{@code activeProfile.resolveDefaultPath().toString()}</li>
     * </ol>
     *
     * @return the resolved active database path string
     */
    public static String getActiveDatabasePath() {
        String prop = System.getProperty("college.db.path");
        if (prop != null && !prop.trim().isEmpty()) {
            return prop.trim();
        }
        String legacyProp = System.getProperty("college.db");
        if (legacyProp != null && !legacyProp.trim().isEmpty()) {
            return legacyProp.trim();
        }
        return activeProfile.resolveDefaultPath().toString();
    }

    /**
     * Creates a new SQLite connection using the active database path.
     *
     * The database path is resolved via {@link #getActiveDatabasePath()}.
     *
     * @return a new JDBC Connection with IMMEDIATE transaction mode, WAL journal, FK enforcement,
     *         and 5000ms busy timeout
     * @throws SQLException if the connection cannot be established
     */
    public static Connection getConnection() throws SQLException {
        String dbPath = getActiveDatabasePath();
        ensureDirectoryExists(dbPath);
        String url = dbPath.startsWith("jdbc:sqlite:") ? dbPath : "jdbc:sqlite:" + dbPath;

        SQLiteConfig config = createConfig();
        return DriverManager.getConnection(url, config.toProperties());
    }

    /**
     * Creates a new SQLite connection using a custom database path or JDBC URL.
     *
     * @param customPath the path to the SQLite database file or full jdbc:sqlite: URL
     * @return a new JDBC Connection with IMMEDIATE transaction mode, WAL journal, FK enforcement,
     *         and 5000ms busy timeout
     * @throws SQLException if the connection cannot be established
     */
    public static Connection getConnection(String customPath) throws SQLException {
        if (customPath == null || customPath.isBlank()) {
            throw new IllegalArgumentException("Database path cannot be null or empty.");
        }
        ensureDirectoryExists(customPath);
        String url = customPath.startsWith("jdbc:sqlite:") ? customPath : "jdbc:sqlite:" + customPath;

        SQLiteConfig config = createConfig();
        return DriverManager.getConnection(url, config.toProperties());
    }

    /**
     * Creates the standard SQLiteConfig used by all connections.
     *
     * @return configured SQLiteConfig instance
     */
    private static SQLiteConfig createConfig() {
        SQLiteConfig config = new SQLiteConfig();
        config.enforceForeignKeys(true);
        config.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setBusyTimeout(5000);
        return config;
    }

    /**
     * Resets the verified directories cache.
     * Intended for testing purposes only to allow re-verification of directories.
     */
    static void resetDirectoryCache() {
        verifiedDirectories.clear();
    }
}

