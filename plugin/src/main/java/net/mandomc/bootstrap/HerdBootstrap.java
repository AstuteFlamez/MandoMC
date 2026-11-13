package net.mandomc.bootstrap;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.mandomc.gameplay.mobs.HerdSettings;
import net.mandomc.integrations.mobs.*;
import net.mandomc.integrations.models.HerdModels;
import net.mandomc.integrations.regions.NativeTerritoryAccess;
import org.bukkit.event.*;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** A single lifecycle entry point also permits a scoped bootstrap patch over newer unrelated features. */
public final class HerdBootstrap implements Listener {
    private final JavaPlugin plugin;
    private final HerdModels models;
    private final HerdTempt tempt;
    private boolean closed;

    public static void enable(JavaPlugin plugin) throws Exception {
        if (HandlerList.getRegisteredListeners(plugin).stream()
                .anyMatch(listener -> listener.getListener() instanceof HerdBootstrap)) return;
        new HerdBootstrap(plugin);
    }

    private HerdBootstrap(JavaPlugin plugin) throws Exception {
        this.plugin = plugin;
        var mobs = new MythicMobAccess();
        var babies = new HerdBabies(plugin, mobs);
        models = new HerdModels(plugin);
        var settings = HerdSettings.read(load(plugin, "mobs/herd.yml"));
        var animals = new HerdAnimals(plugin, mobs, babies, models, settings, load(plugin, "language/herd.yml"), location -> {
            var territory = NativeTerritoryAccess.current();
            return territory != null && territory.edits(location);
        });
        tempt = new HerdTempt(plugin, mobs, settings.banthaFood(), settings.dewbackFood(), animals);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private static YamlConfiguration load(JavaPlugin plugin, String path) throws Exception {
        var file = new File(plugin.getDataFolder(), path);
        if (!file.isFile()) plugin.saveResource(path, false);
        var config = new YamlConfiguration(); config.load(file);
        try (var reader = new InputStreamReader(Objects.requireNonNull(plugin.getResource(path)), StandardCharsets.UTF_8)) {
            config.setDefaults(YamlConfiguration.loadConfiguration(reader));
        }
        return config;
    }

    @EventHandler public void disabled(PluginDisableEvent event) {
        if (event.getPlugin() != plugin || closed) return;
        closed = true;
        tempt.close(); models.close();
    }
}
