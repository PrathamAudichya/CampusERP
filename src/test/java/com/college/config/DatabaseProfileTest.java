package com.college.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DatabaseProfile Enum & Configuration Tests")
class DatabaseProfileTest {

    @Test
    @DisplayName("STANDARD profile has correct permissions and properties")
    void testStandardProfileProperties() {
        DatabaseProfile profile = DatabaseProfile.STANDARD;
        assertEquals("standard", profile.getModeName());
        assertEquals("STANDARD DATABASE", profile.getUiBadge());
        assertTrue(profile.allowsMigrations(), "STANDARD must allow forward-only schema migrations");
        assertFalse(profile.allowsSyntheticSeed(), "STANDARD must NEVER populate synthetic demonstration seeds");
        assertTrue(profile.allowsWrites(), "STANDARD must allow writes");

        Path path = profile.resolveDefaultPath();
        assertNotNull(path);
        assertTrue(path.isAbsolute());
        assertTrue(path.toString().endsWith("campuserp.db"));
    }

    @Test
    @DisplayName("DEMO profile has correct permissions and properties")
    void testDemoProfileProperties() {
        DatabaseProfile profile = DatabaseProfile.DEMO;
        assertEquals("demo", profile.getModeName());
        assertEquals("DEMO DATABASE", profile.getUiBadge());
        assertTrue(profile.allowsMigrations());
        assertTrue(profile.allowsSyntheticSeed(), "DEMO must allow demonstration seed data");
        assertTrue(profile.allowsWrites());

        Path path = profile.resolveDefaultPath();
        assertNotNull(path);
        assertTrue(path.isAbsolute());
        assertTrue(path.toString().endsWith("campuserp-demo.db"));
    }

    @Test
    @DisplayName("HISTORICAL baseline profile is strictly protected against migrations and writes")
    void testHistoricalProfileProperties() {
        DatabaseProfile profile = DatabaseProfile.HISTORICAL;
        assertFalse(profile.allowsMigrations(), "HISTORICAL must NOT allow migrations");
        assertFalse(profile.allowsSyntheticSeed(), "HISTORICAL must NOT allow seed scripts");
        assertFalse(profile.allowsWrites(), "HISTORICAL is read-only reference baseline");

        Path path = profile.resolveDefaultPath();
        assertNotNull(path);
        assertTrue(path.toString().replace('\\', '/').endsWith("data/college.db"));
    }

    @Test
    @DisplayName("fromModeString parses standard mode variants")
    void testFromModeStringStandard() {
        String[] variants = {"standard", "STANDARD", "Std", "production", "prod"};
        for (String mode : variants) {
            assertEquals(DatabaseProfile.STANDARD, DatabaseProfile.fromModeString(mode),
                    "Failed to parse standard variant: " + mode);
        }
    }

    @Test
    @DisplayName("fromModeString parses demo mode variants")
    void testFromModeStringDemo() {
        String[] variants = {"demo", "DEMO", "Sample", "preview"};
        for (String mode : variants) {
            assertEquals(DatabaseProfile.DEMO, DatabaseProfile.fromModeString(mode),
                    "Failed to parse demo variant: " + mode);
        }
    }

    @Test
    @DisplayName("fromModeString rejects invalid database modes with descriptive exception")
    void testFromModeStringInvalid() {
        String[] invalidModes = {"unknown", "invalid", "foo", "staging", "dev"};
        for (String mode : invalidModes) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> DatabaseProfile.fromModeString(mode),
                    "Expected exception for invalid mode: " + mode);
            assertTrue(ex.getMessage().contains("Unknown database mode"));
            assertTrue(ex.getMessage().contains("Valid options are: 'standard' or 'demo'"));
        }
    }

    @Test
    @DisplayName("fromModeString rejects null or blank input")
    void testFromModeStringNullOrBlank() {
        assertThrows(IllegalArgumentException.class, () -> DatabaseProfile.fromModeString(null));
        assertThrows(IllegalArgumentException.class, () -> DatabaseProfile.fromModeString("   "));
    }
}
