package net.mandomc.core.commands;

import java.io.File;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import net.mandomc.MandoMC;
import net.mandomc.content.vehicles.VehicleRegistry;
import net.mandomc.content.vehicles.config.VehicleConfig;
import net.mandomc.core.LangManager;
import net.mandomc.mechanics.bounties.BountyConfig;
import net.mandomc.mechanics.bounties.BountyShowcaseManager;
import net.mandomc.mechanics.gambling.lottery.LotteryConfig;
import net.mandomc.mechanics.warps.WarpConfig;
import net.mandomc.system.items.ItemLoader;
import net.mandomc.system.items.ItemRegistry;
import net.mandomc.system.items.config.ItemsConfig;
import net.mandomc.system.planets.ilum.configs.ParkourConfig;
import net.mandomc.system.shops.ShopLoader;

/**
 * Command used to reload plugin configurations at runtime.
 *
 * Reloads all major systems including items, vehicles, and shops.
 */
public class ReloadCommand implements CommandExecutor {

    private final MandoMC plugin;

    /**
     * Creates the reload command.
     *
     * @param plugin the plugin instance
     */
    public ReloadCommand(MandoMC plugin) {
        this.plugin = plugin;
    }

    /**
     * Executes the reload sequence.
     *
     * Requires the mmc.reload permission. Reloads core config, system configs,
     * items, vehicles, and shops in dependency order.
     *
     * @param sender the command sender
     * @param command the command
     * @param label the command alias used
     * @param args the command arguments
     * @return true always
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("mmc.reload")) {
            sender.sendMessage(LangManager.get("core.reload.no-permission"));
            return true;
        }

        sender.sendMessage(LangManager.get("core.reload.reloading"));

        try {
            plugin.reloadConfig();

            WarpConfig.reload();
            ParkourConfig.reload();
            LotteryConfig.reload();
            BountyConfig.reload();
            BountyShowcaseManager.start();

            ItemsConfig.reload();
            VehicleConfig.reload();

            ItemRegistry.clear();
            ItemLoader.loadItems();

            VehicleRegistry.load();

            File shopsFolder = new File(plugin.getDataFolder(), "shops");
            File pluginJar = null;
            try {
                pluginJar = new File(plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            } catch (java.net.URISyntaxException ex) {
                plugin.getLogger().warning("[Shops] Could not resolve plugin jar path — default configs may not be copied.");
            }
            ShopLoader.loadAll(shopsFolder, pluginJar);

            sender.sendMessage(LangManager.get("core.reload.success"));

        } catch (Exception e) {
            sender.sendMessage(LangManager.get("core.reload.failure"));
            e.printStackTrace();
        }

        return true;
    }
}
