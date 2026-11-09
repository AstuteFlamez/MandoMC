package net.mandomc.gameplay.worlds;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Loads existing Paper dimensions only; worlds.yml owns aliases and precise arrival locations. */
public final class WorldService implements Listener {
    private record Entry(NamespacedKey key, List<String> aliases, Location spawn, boolean weather) { }
    private final Map<NamespacedKey, Entry> entries = new LinkedHashMap<>();
    private final Map<String, NamespacedKey> aliases = new LinkedHashMap<>();

    public WorldService(JavaPlugin plugin, ConfigurationSection config) {
        if (config.getInt("schema-version", -1) != 1) {
            throw new IllegalArgumentException("worlds.yml: expected schema-version 1");
        }
        var section = config.getConfigurationSection("worlds");
        if (section == null) throw new IllegalArgumentException("worlds.yml: missing worlds");
        for (String id : section.getKeys(false)) {
            var data = section.getConfigurationSection(id);
            var key = NamespacedKey.fromString(data.getString("key", ""));
            if (key == null || key.equals(NamespacedKey.minecraft("the_nether"))
                    || key.equals(NamespacedKey.minecraft("the_end")) || entries.containsKey(key)) {
                throw new IllegalArgumentException("worlds.yml: invalid, disabled or duplicate dimension " + id);
            }
            World world = Bukkit.getWorld(key);
            if (world == null) {
                var directory = Bukkit.getWorld(NamespacedKey.minecraft("overworld")).getWorldFolder().toPath().getParent().getParent()
                        .resolve(key.getNamespace()).resolve(key.getKey());
                if (!Files.isDirectory(directory.resolve("region"))
                        || !Files.isRegularFile(directory.resolve("data/paper/level_overrides.dat"))) {
                    throw new IllegalStateException("worlds.yml: existing dimension data missing for " + key
                            + "; refusing to generate replacement terrain");
                }
                world = WorldCreator.ofKey(key).createWorld();
            }
            if (world == null) throw new IllegalStateException("Could not load existing dimension " + key);
            double x = number(data, "spawn.x"), y = number(data, "spawn.y"), z = number(data, "spawn.z");
            float yaw = (float) number(data, "spawn.yaw"), pitch = (float) number(data, "spawn.pitch");
            if (Math.abs(x) > 29999984 || Math.abs(z) > 29999984 || y < world.getMinHeight()
                    || y >= world.getMaxHeight() || Math.abs(pitch) > 90 || !Float.isFinite(yaw)) {
                throw new IllegalArgumentException("worlds.yml: invalid spawn for " + key);
            }
            var location = new Location(world, x, y, z, yaw, pitch);
            var entry = new Entry(key, data.getStringList("aliases"), location, data.getBoolean("weather", true));
            entries.put(key, entry);
            alias(id, key); alias(key.toString(), key); alias(world.getName(), key);
            for (String name : entry.aliases()) alias(name, key);
            world.setPVP(data.getBoolean("pvp", true));
            world.setDifficulty(Difficulty.valueOf(data.getString("difficulty", "NORMAL").toUpperCase(Locale.ROOT)));
            world.setSpawnLocation(location);
            if (!entry.weather()) { world.setStorm(false); world.setThundering(false); }
            plugin.getLogger().info("Loaded existing world " + key + " with MandoMC arrival settings.");
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private static double number(ConfigurationSection data, String path) {
        if (!data.isDouble(path) && !data.isInt(path) && !data.isLong(path)) {
            throw new IllegalArgumentException("worlds.yml: missing numeric " + path);
        }
        double value = data.getDouble(path);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("worlds.yml: non-finite " + path);
        return value;
    }

    private void alias(String name, NamespacedKey key) {
        var previous = aliases.putIfAbsent(name.toLowerCase(Locale.ROOT), key);
        if (previous != null && !previous.equals(key)) throw new IllegalArgumentException("Duplicate world alias " + name);
    }

    public World resolve(String name) {
        var key = aliases.get(name.toLowerCase(Locale.ROOT));
        if (key == null) throw new IllegalArgumentException("Unknown world: " + name);
        var world = Bukkit.getWorld(key);
        if (world == null) throw new IllegalStateException("World is unavailable: " + name);
        return world;
    }

    public Location spawn(String name) {
        var world = resolve(name);
        return entries.get(world.getKey()).spawn().clone();
    }

    public List<String> names() { return List.copyOf(aliases.keySet()); }

    private boolean weatherAllowed(World world) {
        var entry = entries.get(world.getKey());
        return entry == null || entry.weather();
    }

    @EventHandler(ignoreCancelled = true) public void weather(WeatherChangeEvent event) {
        if (event.toWeatherState() && !weatherAllowed(event.getWorld())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void thunder(ThunderChangeEvent event) {
        if (event.toThunderState() && !weatherAllowed(event.getWorld())) event.setCancelled(true);
    }
}
