package net.mandomc.gameplay.abilities;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class AbilitiesConfigPresenceTest {
    private static final Path JEDI_YML = Path.of("src/main/resources/abilities/classes/jedi.yml");
    private static final Path SETTINGS_YML = Path.of("src/main/resources/abilities/settings.yml");

    @Test
    void minimalRunnableAbilitiesExist() throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) new Yaml().load(Files.readString(JEDI_YML));
        assertNotNull(root, "jedi.yml must exist");
        @SuppressWarnings("unchecked")
        Map<String, Object> abilities = (Map<String, Object>) root.get("abilities");
        assertNotNull(abilities, "jedi.yml missing abilities section");

        assertTrue(abilities.containsKey("jedi_force_jump"));
        assertTrue(abilities.containsKey("jedi_force_push"));
    }

    @Test
    void settingsFileExists() throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) new Yaml().load(Files.readString(SETTINGS_YML));
        assertNotNull(root, "settings.yml must exist");
        assertNotNull(root.get("settings"), "settings.yml must contain settings node");
    }
}
