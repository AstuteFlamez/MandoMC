package net.mandomc.gameplay.warp.command;

import net.mandomc.gameplay.warp.config.WarpConfig;
import net.mandomc.gameplay.warp.gui.WarpsGUI;
import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

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
    private final WarpConfig warpConfig;

    /**
     * Creates a new warp command.
     *
     * @param guiManager the GUI manager used to open warp menus
     * @param warpConfig typed warp configuration
     */
    public WarpCommand(GUIManager guiManager, WarpConfig warpConfig) {
        this.guiManager = guiManager;
        this.warpConfig = warpConfig;
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
            sender.sendMessage(LangManager.get("warps.players-only"));
            return true;
        }

        ConfigurationSection warps = getWarpSection();
        if (warps == null) {
            player.sendMessage(LangManager.get("warps.no-warps"));
            return true;
        }

        // Open GUI
        if (args.length == 0) {
            guiManager.openGUI(new WarpsGUI(guiManager, warpConfig), player);
            return true;
        }

        String warpName = args[0];

        if (!warps.contains(warpName)) {
            player.sendMessage(LangManager.get("warps.not-found"));
            return true;
        }

        Location location = getWarpLocation(warpName);

        if (location == null) {
            player.sendMessage(LangManager.get("warps.world-offline"));
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
        return warpConfig.getSection("warps");
    }

    /**
     * Builds a location from a warp configuration.
     *
     * @param warpName the warp name
     * @return the location or null if invalid
     */
    private Location getWarpLocation(String warpName) {

        ConfigurationSection warp = warpConfig.getWarpSection(warpName);
        if (warp == null) return null;

        String worldName = warp.getString("world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        double x = warp.getDouble("x");
        double y = warp.getDouble("y");
        double z = warp.getDouble("z");
        float yaw = (float) warp.getDouble("yaw");
        float pitch = (float) warp.getDouble("pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }
}