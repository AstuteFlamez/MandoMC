package com.astuteflamez.mandomc.core.commands;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TestCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        Location loc = player.getLocation();

        String world = loc.getWorld() != null ? loc.getWorld().getName() : "null";

        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        // 🔥 Console log
        Bukkit.getLogger().info("[SPAWN] " + world + "," + x + "," + y + "," + z);

        // Optional: send to player too
        player.sendMessage("§7Logged: §f" + world + "," + x + "," + y + "," + z);

        return true;
    }
}