package net.mandomc.gameplay.leaderboards;

import java.io.File;
import net.mandomc.bootstrap.MandoPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitTask;

/** One lifecycle owner, started after the existing world and credit owners. */
public final class LeaderboardFeature implements AutoCloseable {
    private final BukkitTask startup;
    private WorldLeaderboards service;
    private LeaderboardFeature(MandoPlugin plugin) {
        Bukkit.getServicesManager().register(LeaderboardFeature.class, this, plugin, ServicePriority.Normal);
        startup = Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                var file = new File(plugin.getDataFolder(), "leaderboards/world.yml");
                if (!file.exists()) plugin.saveResource("leaderboards/world.yml", false);
                var config = new YamlConfiguration(); config.load(file);
                if (!config.getBoolean("enabled", false)) return;
                service = new WorldLeaderboards(plugin, config);
                Bukkit.getServicesManager().register(WorldLeaderboards.class, service, plugin, ServicePriority.Normal);
                plugin.getLogger().info("World leaderboards ready: kills and balance at saved anchors.");
            } catch (Exception error) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "World leaderboards unavailable", error);
                close();
            }
        });
    }
    public static void start(MandoPlugin plugin) {
        if (Bukkit.getServicesManager().load(LeaderboardFeature.class) == null) new LeaderboardFeature(plugin);
    }
    public static void stop() {
        var feature = Bukkit.getServicesManager().load(LeaderboardFeature.class);
        if (feature != null) feature.close();
    }
    @Override public void close() {
        startup.cancel();
        if (service != null) {
            Bukkit.getServicesManager().unregister(WorldLeaderboards.class, service); service.close(); service = null;
        }
        Bukkit.getServicesManager().unregister(LeaderboardFeature.class, this);
    }
}
