package net.mandomc.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class PluginYmlConsistencyTest {

    private static final Path PLUGIN_YML_PATH = Path.of("src/main/resources/plugin.yml");

    @Test
    void commandKeysMatchRuntimeWiring() throws IOException {
        Map<String, Object> root = loadPluginYml();
        Map<String, Object> commands = getMap(root, "commands");

        Set<String> expected = Set.of(
                "shop", "link", "discord", "bounty", "lottery",
                "key", "get", "give", "drop", "recipes",
                "mmcreload", "test", "event", "warps", "parkourfinish"
        );

        assertEquals(expected, commands.keySet(), "plugin.yml command keys drifted from Java wiring");
    }

    @Test
    void permissionNodesContainAllKnownChecks() throws IOException {
        Map<String, Object> root = loadPluginYml();
        Map<String, Object> permissions = getMap(root, "permissions");

        Set<String> required = Set.of(
                "mandomc.admin",
                "mandomc.admin.reload",
                "mandomc.admin.test",
                "mandomc.bounty.gui",
                "mandomc.event.admin",
                "mandomc.items.drop",
                "mandomc.items.get",
                "mandomc.items.give",
                "mandomc.lottery.admin",
                "mandomc.recipes.view",
                "mandomc.shop.admin",
                "mandomc.warp.use",
                "mmc.parkour.bypass-command-blocks"
        );

        assertTrue(permissions.keySet().containsAll(required), "plugin.yml missing required permission nodes");
    }

    @Test
    void mmcReloadPermissionIsMandomcAdminReload() throws IOException {
        Map<String, Object> root = loadPluginYml();
        Map<String, Object> commands = getMap(root, "commands");
        Map<String, Object> mmcReload = getMap(commands, "mmcreload");
        Object permission = mmcReload.get("permission");

        assertEquals("mandomc.admin.reload", permission);
    }

    @Test
    void softDependenciesContainOptionalIntegrations() throws IOException {
        Map<String, Object> root = loadPluginYml();
        Object softdependNode = root.get("softdepend");
        assertNotNull(softdependNode, "plugin.yml missing softdepend list");
        @SuppressWarnings("unchecked")
        Set<String> softdepends = Set.copyOf((java.util.List<String>) softdependNode);

        Set<String> requiredSoftdepends = Set.of(
                "FancyHolograms",
                "ModelEngine",
                "WeaponMechanics"
        );
        assertTrue(softdepends.containsAll(requiredSoftdepends),
                "plugin.yml softdepend is missing optional integration entries");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadPluginYml() throws IOException {
        String content = Files.readString(PLUGIN_YML_PATH);
        Object loaded = new Yaml().load(content);
        assertNotNull(loaded, "plugin.yml must not be empty");
        return (Map<String, Object>) loaded;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> source, String key) {
        Object value = source.get(key);
        assertNotNull(value, "Missing map key: " + key);
        return (Map<String, Object>) value;
    }
}
