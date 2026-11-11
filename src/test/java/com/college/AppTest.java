package com.college;

import com.college.config.DBConnection;
import com.college.config.DatabaseProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying CLI database target selection and property configuration in {@link App}.
 */
public class AppTest {

    private String originalProp;
    private DatabaseProfile originalProfile;

    @BeforeEach
    void setUp() {
        originalProp = System.getProperty("college.db.path");
        originalProfile = DBConnection.getActiveProfile();
    }

    @AfterEach
    void tearDown() {
        if (originalProp != null) {
            System.setProperty("college.db.path", originalProp);
        } else {
            System.clearProperty("college.db.path");
        }
        DBConnection.setActiveProfile(originalProfile);
    }

    @Test
    @DisplayName("CLI --db-mode=standard explicitly selects STANDARD profile and path")
    void testCliDbModeStandard() {
        App.configureDatabaseFromArgs(new String[]{"--db-mode=standard"});
        assertEquals(DatabaseProfile.STANDARD, DBConnection.getActiveProfile());
        assertEquals(DatabaseProfile.STANDARD.resolveDefaultPath().toString(), System.getProperty("college.db.path"));
    }

    @Test
    @DisplayName("CLI --db-mode=demo explicitly selects DEMO profile and path")
    void testCliDbModeDemo() {
        App.configureDatabaseFromArgs(new String[]{"--db-mode=demo"});
        assertEquals(DatabaseProfile.DEMO, DBConnection.getActiveProfile());
        assertEquals(DatabaseProfile.DEMO.resolveDefaultPath().toString(), System.getProperty("college.db.path"));
    }

    @Test
    @DisplayName("CLI --demo explicitly selects DEMO profile")
    void testCliDemoArgument() {
        App.configureDatabaseFromArgs(new String[]{"--demo"});
        assertEquals(DatabaseProfile.DEMO, DBConnection.getActiveProfile());
        assertEquals(DatabaseProfile.DEMO.resolveDefaultPath().toString(), System.getProperty("college.db.path"));
    }

    @Test
    @DisplayName("CLI --standard explicitly selects STANDARD profile")
    void testCliStandardArgument() {
        App.configureDatabaseFromArgs(new String[]{"--standard"});
        assertEquals(DatabaseProfile.STANDARD, DBConnection.getActiveProfile());
        assertEquals(DatabaseProfile.STANDARD.resolveDefaultPath().toString(), System.getProperty("college.db.path"));
    }

    @Test
    @DisplayName("CLI --db <path> explicitly configures custom database file path")
    void testCliCustomDbPathArgument() {
        App.configureDatabaseFromArgs(new String[]{"--db", "target/test-custom.db"});
        assertEquals("target/test-custom.db", System.getProperty("college.db.path"));
    }

    @Test
    @DisplayName("Null or empty arguments do not modify college.db.path")
    void testCliEmptyArguments() {
        System.setProperty("college.db.path", "custom-prior-path.db");
        App.configureDatabaseFromArgs(new String[]{});
        assertEquals("custom-prior-path.db", System.getProperty("college.db.path"));

        App.configureDatabaseFromArgs(null);
        assertEquals("custom-prior-path.db", System.getProperty("college.db.path"));
    }
}
