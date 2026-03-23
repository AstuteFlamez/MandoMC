package com.astuteflamez.mandomc.mechanics.warps;

import net.md_5.bungee.api.ChatColor;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.astuteflamez.mandomc.core.guis.GUIManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the /warp command.
 *
 * Opens the warp GUI or teleports the player to a specified warp.
 * Also provides tab completion for available warp names.
 */
public class WarpCommand implements TabExecutor {

    private final GUIManager guiManager;

    /**
     * Creates a new warp command.
     *
     * @param guiManager the GUI manager used to open warp menus
     */
    public WarpCommand(GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    /**
     * Executes the warp command.
     *
     * /warp → opens GUI
     * /warp <name> → teleports player
     *
     * @param sender the command sender
     * @param command the command
     * @param label command label
     * @param args arguments
     * @return true if handled
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix("&cPlayers only."));
            return true;
        }

        ConfigurationSection warps = getWarpSection();
        if (warps == null) {
            player.sendMessage(prefix("&cNo warps configured."));
            return true;
        }

        // Open GUI
        if (args.length == 0) {
            guiManager.openGUI(new WarpsGUI(guiManager), player);
            return true;
        }

        String warpName = args[0];

        if (!warps.contains(warpName)) {
            player.sendMessage(prefix("&cWarp not found."));
            return true;
        }

        Location location = getWarpLocation(warpName);

        if (location == null) {
            player.sendMessage(prefix("&cWorld not loaded."));
            return true;
        }

        player.teleport(location);
        return true;
    }

    /**
     * Handles tab completion for warp names.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {

        List<String> completions = new ArrayList<>();

        if (args.length != 1) return completions;

        ConfigurationSection warps = getWarpSection();
        if (warps == null) return completions;

        String input = args[0].toLowerCase();

        for (String warp : warps.getKeys(false)) {
            if (warp.toLowerCase().startsWith(input)) {
                completions.add(warp);
            }
        }

        return completions;
    }

    /**
     * Retrieves the warp configuration section.
     */
    private ConfigurationSection getWarpSection() {
        return WarpConfig.get().getConfigurationSection("warps");
    }

    /**
     * Builds a location from a warp configuration.
     *
     * @param warpName the warp name
     * @return the location or null if invalid
     */
    private Location getWarpLocation(String warpName) {

        String path = "warps." + warpName;

        ConfigurationSection config = WarpConfig.get();

        String worldName = config.getString(path + ".world");
        World world = Bukkit.getWorld(worldName);

        if (world == null) return null;

        double x = config.getDouble(path + ".x");
        double y = config.getDouble(path + ".y");
        double z = config.getDouble(path + ".z");

        float yaw = (float) config.getDouble(path + ".yaw");
        float pitch = (float) config.getDouble(path + ".pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }

    /**
     * Applies color formatting to text.
     */
    private static String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Formats a message with plugin prefix.
     */
    private static String prefix(String message) {
        return color("&4&lᴍᴀɴᴅᴏᴍᴄ &r&8» " + message);
    }
}