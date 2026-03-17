package com.astuteflamez.mandomc.features.parkour.managers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import com.astuteflamez.mandomc.features.parkour.ParkourItemFactory;
import com.astuteflamez.mandomc.features.parkour.ParkourSession;
import com.astuteflamez.mandomc.features.parkour.ParkourState;
import com.astuteflamez.mandomc.features.parkour.TimeFormatter;
import com.astuteflamez.mandomc.features.parkour.configs.ParkourConfig;

public class ParkourManager {

    private final Map<UUID, ParkourSession> sessions = new HashMap<>();
    private final ParkourTimeManager timeManager;
    private final ParkourLeaderboardManager leaderboardManager;


    public ParkourManager(ParkourTimeManager timeManager, ParkourLeaderboardManager leaderboardManager) {
        this.timeManager = timeManager;
        this.leaderboardManager = leaderboardManager;
    }

    /*
     * Check if player currently has a parkour session
     */
    public boolean hasSession(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    /*
     * Get a player's session
     */
    public ParkourSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    /*
     * Start a parkour session
     */
    public void enterParkour(Player player, Location startLocation) {

        if (hasSession(player)) return;

        ParkourSession session = new ParkourSession(player.getUniqueId());

        // Save player state
        session.setSavedInventory(player.getInventory().getContents());
        session.setSavedArmor(player.getInventory().getArmorContents());
        session.setSavedGamemode(player.getGameMode());
        session.setReturnLocation(player.getLocation());

        session.setStartLocation(startLocation);

        session.setStartTime(System.currentTimeMillis());

        sessions.put(player.getUniqueId(), session);

        // Prepare player for parkour
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);

        player.setGameMode(GameMode.ADVENTURE);

        giveParkourItems(player);

        player.teleport(startLocation);
    }

    /*
     * End a parkour session
     */
    public void exitParkour(Player player) {

        if (!hasSession(player)) return;

        ParkourSession session = getSession(player);

        session.setState(ParkourState.EXITING);

        // Restore player state
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().setContents(session.getSavedInventory());
        player.getInventory().setArmorContents(session.getSavedArmor());

        player.setGameMode(session.getSavedGamemode());

        String parkourWorldName = ParkourConfig.get().getString("parkour.spawn.world");
        double x = ParkourConfig.get().getDouble("parkour.spawn.x");
        double y = ParkourConfig.get().getDouble("parkour.spawn.y");
        double z = ParkourConfig.get().getDouble("parkour.spawn.z");
        float yaw = (float) ParkourConfig.get().getDouble("parkour.spawn.yaw");
        float pitch = (float) ParkourConfig.get().getDouble("parkour.spawn.pitch");

        Location returnLocation = new Location(Bukkit.getWorld(parkourWorldName), x, y, z, yaw, pitch);

        // Remove session before teleport
        sessions.remove(player.getUniqueId());

        if (returnLocation != null) {
            player.teleport(returnLocation);
        }

        player.sendMessage("§3§lᴍᴀɴᴅᴏᴍᴄ §r§8» §aYou left the parkour.");

        player.playSound(
            player.getLocation(),
            Sound.BLOCK_BEACON_ACTIVATE,
            1f,
            1f
        );
    }

    /*
    * Save checkpoint
    */
    public void setCheckpoint(Player player, Location location) {

        if (!hasSession(player)) return;

        ParkourSession session = getSession(player);

        Location checkpoint = location.clone();

        float yaw = normalizeYaw(player.getLocation().getYaw());
        float pitch = player.getLocation().getPitch();

        checkpoint.setYaw(yaw);
        checkpoint.setPitch(pitch);

        session.setCheckpoint(checkpoint);
    }

    /*
    * Teleport player to checkpoint
    */
    public void teleportCheckpoint(Player player) {

        if (!hasSession(player)) return;

        ParkourSession session = getSession(player);

        Location checkpoint = session.getCheckpoint();
        Location target;

        if (checkpoint != null) {
            target = checkpoint.clone();
        } else {
            target = session.getStartLocation().clone();

            target.setYaw((float) ParkourConfig.get().getDouble("parkour.start.yaw"));
            target.setPitch((float) ParkourConfig.get().getDouble("parkour.start.pitch"));
        }

        player.teleport(target);
        player.setFallDistance(0);

        player.playSound(
            player.getLocation(),
            Sound.ENTITY_ENDERMAN_TELEPORT,
            1f,
            1f
        );
    }

    /*
     * Restart parkour
     */
    public void restart(Player player) {

        if (!hasSession(player)) return;

        ParkourSession session = getSession(player);

        Location startLocation = session.getStartLocation();

        if (startLocation == null) {
            player.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Start location not set.");
            return;
        }

        // Reset checkpoint progress
        session.setCheckpoint(null);
        session.setLastCheckpointPlate(null);

        // Reset timer
        session.setStartTime(System.currentTimeMillis());

        // Clone location so we don't modify the stored session value
        Location tp = startLocation.clone();

        player.teleport(tp);

        player.playSound(
            player.getLocation(),
            Sound.ENTITY_ENDERMAN_TELEPORT,
            1f,
            1f
        );
    }

    private void giveParkourItems(Player player) {

        player.getInventory().setItem(0, ParkourItemFactory.createLeaveItem());
        player.getInventory().setItem(1, ParkourItemFactory.createCheckpointItem());
        player.getInventory().setItem(2, ParkourItemFactory.createRestartItem());
    }

    public void giveRewards(Player player) {

        List<String> commands =
                ParkourConfig.get().getStringList("parkour.rewards.commands");

        for (String cmd : commands) {

            cmd = cmd.replace("%player%", player.getName());

            Bukkit.dispatchCommand(
                    Bukkit.getConsoleSender(),
                    cmd
            );
        }
    }

    public void finishParkour(Player player) {

        if (!hasSession(player)) return;

        ParkourSession session = getSession(player);

        long endTime = System.currentTimeMillis();

        double seconds = (endTime - session.getStartTime()) / 1000.0;

        timeManager.updateTime(player, seconds);
        leaderboardManager.updateLeaderboards();

        player.sendTitle(
                "§6Course Complete!",
                "§eRewards Granted",
                10, 60, 20
        );

        player.sendMessage("§3§lᴍᴀɴᴅᴏᴍᴄ §r§8» §aYour time: §f" + TimeFormatter.format(seconds));

        Double best = timeManager.getBestTime(player.getUniqueId());
            
        player.sendMessage("§3§lᴍᴀɴᴅᴏᴍᴄ §r§8» §6Your best time: §f" + TimeFormatter.format(best));

        giveRewards(player);

        exitParkour(player);
    }

    private float normalizeYaw(float yaw) {

        yaw = yaw % 360;

        if (yaw < 0) yaw += 360;

        return Math.round(yaw / 90f) * 90f;
    }
}