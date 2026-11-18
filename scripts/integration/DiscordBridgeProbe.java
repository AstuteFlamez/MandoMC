package net.mandomc.verification;

import java.nio.file.*;
import java.util.*;
import net.mandomc.gameplay.credits.CreditService;
import net.mandomc.gameplay.ranks.RankService;
import net.mandomc.integrations.discord.DiscordBridge;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Standalone disposable Paper fixture. No players, production worlds or live Discord access. */
public final class DiscordBridgeProbe extends JavaPlugin {
    private static final UUID PLAYER = UUID.fromString("12345678-1234-1234-1234-123456789012");
    private RankService ranks;
    private CreditService credits;
    private DiscordBridge bridge;
    private boolean intervened, finished;
    private int ticks;
    private YamlConfiguration load(String path) throws Exception {
        saveResource(path, false);
        var result = new YamlConfiguration(); result.load(getDataFolder().toPath().resolve(path).toFile()); return result;
    }
    @Override public void onEnable() {
        try {
            ranks = new RankService(this, load("ranks/ranks.yml"));
            credits = new CreditService(this, load("economy/credits.yml"));
            ranks.change(PLAYER, Set.of("builder", "warrior"), saved -> {
                if (!saved) { fail("Initial rank persistence"); return; }
                try {
                    var settings = load("integrations/discord.yml"); settings.set("endpoint", System.getProperty("probe.endpoint"));
                    settings.set("rank-roles.warrior", "333333333333333333");
                    settings.set("rank-roles.marshal", "444444444444444444");
                    bridge = new DiscordBridge(this, ranks, credits, settings, load("language/discord.yml"));
                    var tick = DiscordBridge.class.getDeclaredMethod("tick"); tick.setAccessible(true);
                    Bukkit.getScheduler().runTaskTimer(this, () -> {
                        try {
                            if (finished) return;
                            if (++ticks > 300) { fail("Bridge probe timed out"); return; }
                            Path root = Path.of(System.getProperty("probe.control"));
                            if (Files.exists(root.resolve("intervene")) && !intervened) {
                                intervened = true;
                                ranks.change(PLAYER, Set.of("builder", "warrior"), ok -> { if (!ok) fail("Intervening rank write"); });
                            }
                            if (Files.exists(root.resolve("complete"))) {
                                if (!ranks.memberships(PLAYER).equals(Set.of("builder", "marshal"))) { fail("Final membership set"); return; }
                                var stored = RankService.read(getDataFolder().toPath().resolve("data/rank-users.yml"), ranks.catalog());
                                if (!stored.get(PLAYER).equals(ranks.memberships(PLAYER))) { fail("Durable rank acknowledgment"); return; }
                                finished = true;
                                getLogger().info("DISCORD_BRIDGE_PROBE_PASSED: native persistence, outbound memberships, managed merge, stale reply rejection, outage retry and offline UUID synchronization");
                                Bukkit.shutdown(); return;
                            }
                            tick.invoke(bridge);
                        } catch (Exception error) { fail(error.toString()); }
                    }, 1, 2);
                } catch (Exception error) { fail(error.toString()); }
            });
        } catch (Exception error) { fail(error.toString()); }
    }
    private void fail(String reason) {
        if (finished) return;
        finished = true; getLogger().severe("DISCORD_BRIDGE_PROBE_FAILED: " + reason); Bukkit.shutdown();
    }
    @Override public void onDisable() {
        if (bridge != null) bridge.close();
        if (credits != null) credits.close();
        if (ranks != null) ranks.close();
    }
}
