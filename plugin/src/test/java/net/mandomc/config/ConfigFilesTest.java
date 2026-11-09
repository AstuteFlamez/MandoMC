package net.mandomc.config;

import static org.junit.Assert.*;

import org.junit.Test;

import java.nio.file.*;
import java.util.Properties;

public class ConfigFilesTest {
    @Test
    public void movesOperatorSettingsExactlyOnceWithoutResettingThem() throws Exception {
        Path root = Files.createTempDirectory("config-migration");
        var mapping = new Properties();
        mapping.setProperty("combat.yml", "combat/combat.yml");
        String custom = "# My tuning\ntag-seconds: 29\n";
        Files.writeString(root.resolve("combat.yml"), custom);
        ConfigFiles.migrate(root, mapping);
        ConfigFiles.migrate(root, mapping);
        assertEquals(custom, Files.readString(root.resolve("combat/combat.yml")));
        assertFalse(Files.exists(root.resolve("combat.yml")));
    }

    @Test
    public void detectsAllConflictsBeforeMovingAnyFiles() throws Exception {
        Path root = Files.createTempDirectory("config-conflict");
        Files.createDirectories(root.resolve("travel"));
        Files.writeString(root.resolve("teleport.yml"), "old overrides");
        Files.writeString(root.resolve("travel/teleport.yml"), "new overrides");
        Files.writeString(root.resolve("warps.yml"), "do not move yet");
        var mapping = new Properties();
        mapping.setProperty("teleport.yml", "travel/teleport.yml");
        mapping.setProperty("warps.yml", "travel/warps.yml");
        assertThrows(java.io.IOException.class, () -> ConfigFiles.migrate(root, mapping));
        assertTrue(Files.exists(root.resolve("warps.yml")));
        assertEquals("old overrides", Files.readString(root.resolve("teleport.yml")));
        assertEquals("new overrides", Files.readString(root.resolve("travel/teleport.yml")));
    }

    @Test
    public void identicalOldAndNewCopiesAreConsolidated() throws Exception {
        Path root = Files.createTempDirectory("config-identical");
        Files.createDirectories(root.resolve("travel"));
        Files.writeString(root.resolve("teleport.yml"), "same");
        Files.writeString(root.resolve("travel/teleport.yml"), "same");
        var mapping = new Properties();
        mapping.setProperty("teleport.yml", "travel/teleport.yml");
        ConfigFiles.migrate(root, mapping);
        assertFalse(Files.exists(root.resolve("teleport.yml")));
        assertEquals("same", Files.readString(root.resolve("travel/teleport.yml")));
    }

    @Test
    public void everyMigrationTargetIsBundled() throws Exception {
        var paths = new Properties();
        paths.load(getClass().getResourceAsStream("/config-layout.properties"));
        for (Object path : paths.values())
            assertNotNull(path.toString(), getClass().getResource("/" + path));
    }
}
