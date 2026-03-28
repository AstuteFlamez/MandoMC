package net.mandomc.core.modules.core;

import net.mandomc.MandoMC;
import net.mandomc.core.module.Module;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyModule implements Module {

    private final MandoMC plugin;

    private static Economy ECONOMY;

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

    // =========================
    // 💰 STATIC HELPERS
    // =========================

    public static boolean isReady() {
        return ECONOMY != null;
    }

    public static double getBalance(OfflinePlayer player) {
        if (!isReady()) return 0;
        return ECONOMY.getBalance(player);
    }

    public static boolean has(OfflinePlayer player, double amount) {
        if (!isReady()) return false;
        return ECONOMY.has(player, amount);
    }

    public static boolean withdraw(OfflinePlayer player, double amount) {
        if (!isReady()) return false;
        return ECONOMY.withdrawPlayer(player, amount).transactionSuccess();
    }

    public static boolean deposit(OfflinePlayer player, double amount) {
        if (!isReady()) return false;
        return ECONOMY.depositPlayer(player, amount).transactionSuccess();
    }

    /**
     * Formats money nicely with commas and 2 decimals.
     *
     * @param amount value to format
     * @return formatted string
     */
    public static String format(double amount) {
        return String.format("%,.2f", amount);
    }

    public static Economy get() {
        return ECONOMY;
    }
}