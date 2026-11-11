package com.college;

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

    @BeforeEach
    void setUp() {
        originalProp = System.getProperty("college.db.path");
    }

    @AfterEach
    void tearDown() {
        if (originalProp != null) {
            System.setProperty("college.db.path", originalProp);
        } else {
            System.clearProperty("college.db.path");
        }
    }

    @Test
    @DisplayName("CLI --demo explicitly selects data/college-demo.db")
    void testCliDemoArgument() {
        App.configureDatabaseFromArgs(new String[]{"--demo"});
        assertEquals("data/college-demo.db", System.getProperty("college.db.path"));
    }

    @Test
    @DisplayName("CLI --production explicitly selects data/college.db")
    void testCliProductionArgument() {
        App.configureDatabaseFromArgs(new String[]{"--production"});
        assertEquals("data/college.db", System.getProperty("college.db.path"));
    }

    @Test
    @DisplayName("CLI --prod shorthand explicitly selects data/college.db")
    void testCliProdShorthandArgument() {
        App.configureDatabaseFromArgs(new String[]{"--prod"});
        assertEquals("data/college.db", System.getProperty("college.db.path"));
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

