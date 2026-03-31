package net.mandomc.gameplay.bounty;

import net.mandomc.gameplay.bounty.model.Bounty;
import net.mandomc.gameplay.bounty.storage.BountyRepository;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BountyPersistenceConsistencyTest {

    @TempDir
    Path tempDir;

    @Test
    void bountyStorageFacadePersistsThroughRepositoryBackend() {
        Plugin plugin = mockPlugin(tempDir);
        BountyRepository repository = new BountyRepository(plugin);
        repository.load();

        BountyStorage.setup(tempDir.toFile(), repository);
        BountyStorage.load();

        UUID target = UUID.randomUUID();
        UUID placer = UUID.randomUUID();

        Bounty bounty = BountyStorage.getOrCreate(target);
        bounty.addEntry(placer, 250.0D);
        BountyStorage.save();

        BountyRepository reloaded = new BountyRepository(plugin);
        reloaded.load();

        assertTrue(reloaded.findById(target).isPresent(), "target bounty should persist in repository");
        assertEquals(250.0D, reloaded.findById(target).orElseThrow().getTotal(), 0.001D);
    }

    private Plugin mockPlugin(Path dataFolder) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getDataFolder" -> dataFolder.toFile();
            case "getLogger" -> Logger.getLogger("test-bounty");
            default -> null;
        };
        return (Plugin) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Plugin.class},
                handler
        );
    }
}
