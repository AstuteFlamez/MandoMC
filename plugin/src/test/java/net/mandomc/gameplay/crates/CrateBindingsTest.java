package net.mandomc.gameplay.crates;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class CrateBindingsTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private Path directory;
    @Before public void prepareDirectory() { directory = temporary.getRoot().toPath(); }
    private YamlConfiguration defaults() {
        var yaml = new YamlConfiguration();
        yaml.set("kyber.blocks", List.of(Map.of("world", "minecraft:spawnworld", "x", 1, "y", 90, "z", 2)));
        yaml.set("vote.blocks", List.of());
        return yaml;
    }

    @Test public void importsExistingLocationsWithoutChangingManagedConfiguration() throws Exception {
        var defaults = defaults();
        String original = defaults.saveToString();
        var store = CrateBindings.load(directory.resolve("data/crate-blocks.yml"), defaults);
        assertEquals(1, store.maps("kyber").size());
        assertEquals(original, defaults.saveToString());
        assertFalse(Files.exists(directory.resolve("data/crate-blocks.yml")));
    }

    @Test public void inGamePlacementSurvivesAChangedSourceConfiguration() throws Exception {
        Path file = directory.resolve("data/crate-blocks.yml");
        var original = CrateBindings.load(file, defaults());
        var point = new CrateBindings.Point("minecraft:spawnworld", 12, 96, -8);
        original.bind("vote", point).save();
        var changed = defaults(); changed.set("vote.blocks", List.of()); changed.set("kyber.blocks", List.of());
        var restored = CrateBindings.load(file, changed);
        assertEquals(List.of(point.map()), restored.maps("vote"));
        assertEquals(1, restored.maps("kyber").size());
        assertTrue(original.maps("vote").isEmpty());
    }

    @Test public void repeatedPlacementDoesNotDuplicateAndCannotClaimAnotherCratesContainer() throws Exception {
        var original = CrateBindings.load(directory.resolve("bindings.yml"), defaults());
        var point = new CrateBindings.Point("minecraft:spawnworld", 12, 96, -8);
        var next = original.bind("vote", point).bind("vote", point);
        assertEquals(1, next.maps("vote").size());
        assertThrows(IllegalArgumentException.class, () -> next.bind("kyber", point));
    }

    @Test public void brokenExistingStateFailsInsteadOfResettingToDefaults() throws Exception {
        Path file = directory.resolve("bindings.yml");
        Files.writeString(file, "schema-version: 9\ncrates: {}\n");
        assertThrows(IllegalArgumentException.class, () -> CrateBindings.load(file, defaults()));
        assertTrue(Files.readString(file).contains("schema-version: 9"));
    }

    @Test public void malformedCoordinatesAndWorldsAreRejected() throws Exception {
        var config = defaults();
        config.set("vote.blocks", List.of(Map.of("world", "minecraft:spawnworld", "x", 1.25, "y", 90, "z", 2)));
        assertThrows(IllegalArgumentException.class, () -> CrateBindings.load(directory.resolve("none.yml"), config));
        assertThrows(IllegalArgumentException.class, () -> new CrateBindings.Point("../world", 1, 90, 2));
    }
}
