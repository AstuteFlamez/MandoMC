package net.mandomc.core.storage;

import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class JsonRepositoryRobustnessTest {

    @TempDir
    Path tempDir;

    @Test
    void loadSwallowsRuntimePopulateFailures() throws Exception {
        Plugin plugin = mockPlugin(tempDir);
        Path repoFile = tempDir.resolve("test.json");
        Files.writeString(repoFile, "{\"key\":\"value\"}");

        ExplodingRepository repository = new ExplodingRepository(plugin);
        assertDoesNotThrow(repository::load);
    }

    private Plugin mockPlugin(Path dataFolder) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getDataFolder" -> dataFolder.toFile();
            case "getLogger" -> Logger.getLogger("json-repo-test");
            default -> null;
        };
        return (Plugin) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Plugin.class},
                handler
        );
    }

    private static final class ExplodingRepository extends JsonRepository<String, String> {
        private ExplodingRepository(Plugin plugin) {
            super(plugin, "test.json");
        }

        @Override
        protected void populate(String json, Map<String, String> target) {
            throw new IllegalStateException("boom");
        }

        @Override
        protected String serialize(Map<String, String> data) {
            return "{}";
        }

        @Override
        protected String idOf(String entity) {
            return entity;
        }
    }
}
