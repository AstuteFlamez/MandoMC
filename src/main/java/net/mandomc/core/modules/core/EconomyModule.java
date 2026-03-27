package net.mandomc.core.modules.core;

import net.mandomc.MandoMC;
import net.mandomc.core.module.Module;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyModule implements Module {

    private final MandoMC plugin;

    public static Economy ECONOMY;

    public EconomyModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {

        if (!setupEconomy()) {
            plugin.getLogger().severe("❌ Vault or Economy provider not found!");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }

        plugin.getLogger().info("✅ Economy hooked: " + ECONOMY.getName());
    }

    private boolean setupEconomy() {

        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);

        if (rsp == null) return false;

        ECONOMY = rsp.getProvider();
        return ECONOMY != null;
    }

    @Override
    public void disable() {
        ECONOMY = null;
    }
}