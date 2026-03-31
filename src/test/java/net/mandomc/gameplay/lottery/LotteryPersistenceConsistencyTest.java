package net.mandomc.gameplay.lottery;

import net.mandomc.gameplay.lottery.storage.LotteryRepository;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LotteryPersistenceConsistencyTest {

    @TempDir
    Path tempDir;

    @Test
    void lotteryStorageFacadeLoadsAndSavesRepositoryState() {
        Plugin plugin = mockPlugin(tempDir);
        LotteryRepository repository = new LotteryRepository(plugin);
        repository.load();
        LotteryStorage.setup(repository);

        LotteryManager.loadData(0.0D, new HashMap<>());
        UUID playerId = UUID.randomUUID();
        LotteryManager.addTicket(playerId, 500.0D);
        LotteryStorage.save();

        LotteryManager.loadData(0.0D, new HashMap<>());
        LotteryStorage.load();

        assertEquals(500.0D, LotteryManager.getPot(), 0.001D);
        assertEquals(1, LotteryManager.getTickets(playerId));
    }

    private Plugin mockPlugin(Path dataFolder) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getDataFolder" -> dataFolder.toFile();
            case "getLogger" -> Logger.getLogger("test-lottery");
            default -> null;
        };
        return (Plugin) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Plugin.class},
                handler
        );
    }
}
