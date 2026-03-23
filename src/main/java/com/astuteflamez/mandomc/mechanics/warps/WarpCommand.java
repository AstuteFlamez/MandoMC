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

public class WarpCommand implements TabExecutor {

    private final GUIManager guiManager;

    public WarpCommand(GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cYou do not have permission to run this command.");
            return true;
        }

        ConfigurationSection warps = WarpConfig.get().getConfigurationSection("warps");
        if (warps == null) {
            player.sendMessage(color("&4&lᴍᴀɴᴅᴏᴍᴄ &r&8» &cNo warps configured."));
            return true;
        }

        // /warp → open GUI
        if (args.length == 0) {
            guiManager.openGUI(new WarpsGUI(guiManager), player);
            return true;
        }

        // /warp Hoth
        String warpName = args[0];

        if (!warps.contains(warpName)) {
            player.sendMessage(color("&4&lᴍᴀɴᴅᴏᴍᴄ &r&8» &cWarp not found."));
            return true;
        }

        String path = "warps." + warpName;

        double x = com.astuteflamez.mandomc.mechanics.warps.WarpConfig.get().getDouble(path + ".x");
        double y = WarpConfig.get().getDouble(path + ".y");
        double z = WarpConfig.get().getDouble(path + ".z");

        float yaw = (float) WarpConfig.get().getDouble(path + ".yaw");
        float pitch = (float) WarpConfig.get().getDouble(path + ".pitch");

        String worldName = WarpConfig.get().getString(path + ".world");
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            player.sendMessage(color("&4&lᴍᴀɴᴅᴏᴍᴄ &r&8» &cWorld not loaded."));
            return true;
        }

        Location location = new Location(world, x, y, z, yaw, pitch);

        player.teleport(location);

        return true;
    }

    // TAB COMPLETION
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {

            ConfigurationSection warps =
                    WarpConfig.get().getConfigurationSection("warps");

            if (warps != null) {
                for (String warp : warps.getKeys(false)) {

                    if (warp.toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(warp);
                    }
                }
            }
        }

        return completions;
    }

    public static String color(String text) {
        if (text == null) return "";
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}