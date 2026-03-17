package com.bilal.mandomc.features.parkour.managers;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;

import com.bilal.mandomc.features.parkour.configs.ParkourConfig;

public class CheckpointManager {

    private final Plugin plugin;

    private final Map<Location, Integer> checkpoints = new HashMap<>();

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
    }

    public void loadCheckpoints() {

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

            int number = Integer.parseInt(key);

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
}