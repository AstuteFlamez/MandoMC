package net.mandomc.world.ilum.manager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import net.mandomc.world.ilum.config.ParkourConfig;

public class CheckpointManager {

    private final Plugin plugin;

    private final Map<Location, Integer> checkpoints = new HashMap<>();
    private final Set<TextDisplay> displays = new HashSet<>();

    public CheckpointManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void registerCheckpoint(Location plateLocation, int number) {

        Location blockLoc = plateLocation.getBlock().getLocation();

        checkpoints.put(blockLoc, number);

        spawnHologram(blockLoc, number);
    }

    public Integer getCheckpointNumber(Location plateLocation) {

        Location blockLoc = plateLocation.getBlock().getLocation();

        return checkpoints.get(blockLoc);
    }

    private void spawnHologram(Location loc, int number) {

        // Center hologram above plate
        Location holo = loc.clone().add(0.5, 1.5, 0.5);

        TextDisplay display = (TextDisplay) loc.getWorld()
                .spawnEntity(holo, EntityType.TEXT_DISPLAY);

        display.setText("§e§lCheckpoint §l§f#" + number);

        display.setShadowed(true);
        display.setBackgroundColor(Color.fromARGB(0,0,0,0));
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(false);
        displays.add(display);
    }

    public void loadCheckpoints() {
        clearDisplays();
        checkpoints.clear();

        if (ParkourConfig.get() == null) {
            plugin.getLogger().warning("[Parkour] parkour.yml is not loaded; cannot load checkpoints.");
            return;
        }

        ConfigurationSection section =
                ParkourConfig.get().getConfigurationSection("parkour.checkpoints");

        if (section == null) {
            plugin.getLogger().warning("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7No parkour.checkpoints section found.");
            return;
        }

        String worldName = ParkourConfig.get().getString("parkour.world");
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            plugin.getLogger().warning("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Parkour world not found: " + worldName);
            return;
        }

        for (String key : section.getKeys(false)) {

            int number;
            try {
                number = Integer.parseInt(key);
            } catch (NumberFormatException ex) {
                plugin.getLogger().warning("[Parkour] Invalid checkpoint key (must be numeric): " + key);
                continue;
            }

            if (!section.contains(key + ".x")
                    || !section.contains(key + ".y")
                    || !section.contains(key + ".z")) {
                plugin.getLogger().warning("[Parkour] Checkpoint #" + number + " missing x/y/z coordinates.");
                continue;
            }

            double x = section.getDouble(key + ".x");
            double y = section.getDouble(key + ".y");
            double z = section.getDouble(key + ".z");

            // Normalize to plate block location
            Location plateLocation = new Location(world, x, y, z)
                    .getBlock()
                    .getLocation();

            checkpoints.put(plateLocation, number);

            spawnHologram(plateLocation, number);

            plugin.getLogger().info(
                    "§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Loaded checkpoint #" + number + " at " + plateLocation
            );
        }
    }

    public void clearDisplays() {
        for (TextDisplay display : new HashSet<>(displays)) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        displays.clear();
    }
}