package net.mandomc.gameplay.vehicle;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleSkinsConfigTest {

    private static final Path VEHICLES_DIR = Path.of("src/main/resources/vehicles");

    @Test
    void xwingContainsPurpleSkinDefinition() throws IOException {
        Map<String, Object> root = loadYaml(VEHICLES_DIR.resolve("xwing.yml"));
        Map<String, Object> vehicle = getMap(root, "vehicle");
        Map<String, Object> skins = getMap(vehicle, "skins");
        Map<String, Object> options = getMap(skins, "options");

        assertEquals("default", skins.get("default"));
        assertTrue(options.containsKey("purple"));

        Map<String, Object> purple = getMap(options, "purple");
        assertEquals("purple_xwing", purple.get("model_key"));
        assertEquals("mandomc.vehicle.skin.xwing.purple", purple.get("permission"));

        Map<String, Object> item = getMap(purple, "item");
        assertNotNull(item.get("name"));
        assertNotNull(item.get("custom_model_data"));
    }

    @Test
    void allVehicleConfigsDeclareSkinsAndNoSpawnBones() throws IOException {
        Set<String> ids = Set.of("xwing", "tiefighter", "speederbike");
        for (String id : ids) {
            Map<String, Object> root = loadYaml(VEHICLES_DIR.resolve(id + ".yml"));
            Map<String, Object> vehicle = getMap(root, "vehicle");
            Map<String, Object> skins = getMap(vehicle, "skins");
            Map<String, Object> options = getMap(skins, "options");

            assertNotNull(skins.get("default"), id + " missing skins.default");
            assertFalse(options.isEmpty(), id + " missing skins options");

            String content = Files.readString(VEHICLES_DIR.resolve(id + ".yml"));
            assertFalse(content.contains("spawn_bones:"), id + " still contains unused spawn_bones");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadYaml(Path path) throws IOException {
        Object loaded = new Yaml().load(Files.readString(path));
        assertNotNull(loaded, path + " must not be empty");
        return (Map<String, Object>) loaded;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> source, String key) {
        Object value = source.get(key);
        assertNotNull(value, "Missing map key: " + key);
        return (Map<String, Object>) value;
    }
}
