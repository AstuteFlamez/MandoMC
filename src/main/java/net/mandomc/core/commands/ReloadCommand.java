package net.mandomc.core.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import net.mandomc.MandoMC;
import net.mandomc.content.vehicles.VehicleRegistry;
import net.mandomc.content.vehicles.config.VehicleConfig;
import net.mandomc.mechanics.gambling.lottery.LotteryConfig;
import net.mandomc.mechanics.warps.WarpConfig;
import net.mandomc.system.items.ItemRegistry;
import net.mandomc.system.items.config.ItemsConfig;
import net.mandomc.system.planets.ilum.configs.ParkourConfig;

/**
 * Command used to reload plugin configurations at runtime.
 *
 * Reloads all major systems including items and vehicles.
 */
public class ReloadCommand implements CommandExecutor {

    private final MandoMC plugin;

    public ReloadCommand(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (!sender.hasPermission("mmc.reload")) {
            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cYou do not have permission to run this command.");
            return true;
        }

        sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Reloading plugin...");

        try {

            /* ---------------------------
               Core Config
            --------------------------- */
            plugin.reloadConfig();

            /* ---------------------------
               System Configs
            --------------------------- */
            WarpConfig.reload();
            ParkourConfig.reload();

            /* ---------------------------
               Items + Vehicles (IMPORTANT ORDER)
            --------------------------- */
            ItemsConfig.reload();
            ItemRegistry.reload();
            VehicleConfig.reload();
            VehicleRegistry.reload();

            /* ---------------------------
               Rebuild Vehicle Mapping
            --------------------------- */
            VehicleRegistry.load();

            LotteryConfig.reload();

            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Configs reloaded.");

        } catch (Exception e) {

            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cReload failed. Check console.");

            e.printStackTrace();
            return true;
        }

        sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §aReload complete.");

        return true;
    }
}