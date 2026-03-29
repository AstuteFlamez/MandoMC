package net.mandomc.core.modules.core;

import net.mandomc.MandoMC;
import net.mandomc.core.module.Module;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class EconomyModule implements Module {

    private final MandoMC plugin;

    private static Economy ECONOMY;

    // =========================
    // DATABASE CONFIG
    // =========================
    private String host;
    private int port;
    private String database;
    private String username;
    private String password;

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

        // Load DB config
        host = plugin.getConfig().getString("database.host");
        port = plugin.getConfig().getInt("database.port");
        database = plugin.getConfig().getString("database.name");
        username = plugin.getConfig().getString("database.username");
        password = plugin.getConfig().getString("database.password");

        plugin.getLogger().info("✅ Economy hooked: " + ECONOMY.getName());

        // START SYNC TASK
        startBalanceSync();
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

    // =========================
    // 🔄 SYNC TASK
    // =========================
    private void startBalanceSync() {

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {

            if (!isReady()) return;

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false";

            try (Connection conn = DriverManager.getConnection(url, username, password)) {

                PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO balances (uuid, username, balance) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE username = ?, balance = ?"
                );

                for (Player player : Bukkit.getOnlinePlayers()) {

                    double balance = getBalance(player);

                    ps.setString(1, player.getUniqueId().toString());
                    ps.setString(2, player.getName());
                    ps.setDouble(3, balance);

                    ps.setString(4, player.getName());
                    ps.setDouble(5, balance);

                    ps.addBatch();
                }

                ps.executeBatch();

            } catch (Exception e) {
                plugin.getLogger().warning("❌ Balance sync failed: " + e.getMessage());
            }

        }, 20L * 60, 20L * 60); // every 60 seconds
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

    public static String format(double amount) {
        return String.format("%,.2f", amount);
    }

    public static Economy get() {
        return ECONOMY;
    }
}