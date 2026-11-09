package net.mandomc.gameplay.explosions;

import static org.junit.Assert.*;

import org.junit.Test;

public class BlastSettingsTest {
    @Test
    public void acceptedBlastBoundsAreBundled() {
        var c =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(
                                getClass().getResourceAsStream("/worlds/explosions.yml")));
        var settings = BlastSettings.read(c);
        assertEquals(4, settings.radius());
        assertEquals(64, settings.maxBlocks());
        assertEquals(60, settings.restoreSeconds());
    }

    @Test
    public void invalidSettingsCannotCreateUnboundedWork() {
        assertThrows(IllegalArgumentException.class, () -> new BlastSettings(0, 64, 60, 4096));
        assertThrows(IllegalArgumentException.class, () -> new BlastSettings(4, 100000, 60, 4096));
        assertThrows(IllegalArgumentException.class, () -> new BlastSettings(4, 64, -1, 4096));
        assertThrows(IllegalArgumentException.class, () -> new BlastSettings(4, 64, 60, 1));
    }
}
