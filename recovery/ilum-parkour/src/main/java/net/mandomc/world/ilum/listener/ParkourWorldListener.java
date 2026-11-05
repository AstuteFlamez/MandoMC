package net.mandomc.world.ilum.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import net.mandomc.world.ilum.config.ParkourConfig;
import net.mandomc.world.ilum.manager.ParkourManager;

import org.bukkit.entity.Player;

public class ParkourWorldListener implements Listener {

    private final ParkourManager parkourManager;

    public ParkourWorldListener(ParkourManager parkourManager) {
        this.parkourManager = parkourManager;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {

        Player player = event.getPlayer();
        FileConfiguration config = ParkourConfig.get();
        if (config == null) return;

        String parkourWorldName = config.getString("parkour.world");

        if (parkourWorldName == null) return;

        World parkourWorld = Bukkit.getWorld(parkourWorldName);

        if (parkourWorld == null) return;

        // Player left parkour world while session was active.
        if (!player.getWorld().equals(parkourWorld) && parkourManager.hasSession(player)) {
            parkourManager.exitParkour(player);
            return;
        }

        // Player entered the parkour world
        if (player.getWorld().equals(parkourWorld)) {
            boolean autoEnter = config.getBoolean("parkour.settings.auto-enter-on-world-change", true);
            if (!autoEnter || parkourManager.hasSession(player)) {
                return;
            }

            Location startLocation = getStartLocation(parkourWorld);
            parkourManager.enterParkour(player, startLocation);
        }
    }

    private Location getStartLocation(World world) {
        FileConfiguration config = ParkourConfig.get();
        if (config == null) {
            return world.getSpawnLocation();
        }

        double x = config.getDouble("parkour.start.x");
        double y = config.getDouble("parkour.start.y");
        double z = config.getDouble("parkour.start.z");

        float yaw = (float) config.getDouble("parkour.start.yaw");
        float pitch = (float) config.getDouble("parkour.start.pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

}