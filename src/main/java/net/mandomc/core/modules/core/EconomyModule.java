package net.mandomc.core.modules.core;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import net.mandomc.MandoMC;
import net.mandomc.core.config.MainConfig;
import net.mandomc.core.lifecycle.TaskRegistrar;
import net.mandomc.core.module.Module;
import net.mandomc.core.services.EconomyService;
import net.mandomc.core.services.ServiceRegistry;
import net.milkbowl.vault.economy.Economy;

/**
 * Integrates with Vault to provide economy operations across the plugin.
 *
 * Hooks into the registered Vault Economy provider on enable and
 * periodically syncs online player balances to the configured database.
 *
 * Static utility methods ({@code getBalance}, {@code has}, {@code withdraw},
 * {@code deposit}) are retained for backward compatibility during the refactor.
 * They will be replaced with injected {@code EconomyService} calls in Phase 5.
 */
public class EconomyModule implements Module {

    private final MandoMC plugin;
    private TaskRegistrar taskRegistrar;

    private static Economy economy;

    private String host;
    private int port;
    private String database;
    private String username;
    private String password;

    /**
     * Creates the economy module.
     *
     * @param plugin the plugin instance
     */
    public EconomyModule(MandoMC plugin) {
        this.plugin = plugin;
    }

    /**
     * Hooks into Vault, loads database config, and starts the balance sync task.
     *
     * Disables the plugin if Vault or an economy provider is unavailable.
     */
    @Override
    public void enable(ServiceRegistry registry) {
        if (!setupEconomy()) {
            plugin.getLogger().severe("Vault or Economy provider not found!");
            plugin.getServer().getPluginManager().disablePlugin(plugin);
            return;
        }
        registry.register(EconomyService.class, EconomyModule::deposit);

        MainConfig config = registry.get(MainConfig.class);
        host = config.getDatabaseHost();
        port = config.getDatabasePort();
        database = config.getDatabaseName();
        username = config.getDatabaseUser();
        password = config.getDatabasePassword();

        plugin.getLogger().info("Economy hooked: " + economy.getName());

        taskRegistrar = new TaskRegistrar(plugin);
        startBalanceSync();
    }

    /**
     * Attempts to hook into the Vault Economy service provider.
     *
     * @return true if an economy provider was found
     */
    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);

        if (rsp == null) return false;

        economy = rsp.getProvider();
        return economy != null;
    }

    /**
     * Starts a repeating async task that syncs online player balances to the database.
     *
     * Runs every 60 seconds. Uses a batch insert/update for efficiency.
     * The task is tracked by {@link TaskRegistrar} and cancelled on {@link #disable()}.
     */
    private void startBalanceSync() {
        taskRegistrar.runTimer(() -> {
            if (!isReady()) return;

            List<BalanceSnapshot> snapshots = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                snapshots.add(new BalanceSnapshot(
                        player.getUniqueId(),
                        player.getName(),
                        getBalance(player)
                ));
            }

            if (snapshots.isEmpty()) {
                return;
            }

            taskRegistrar.runAsync(() -> persistBalances(snapshots));
        }, 20L * 60, 20L * 60);
    }

    private void persistBalances(List<BalanceSnapshot> snapshots) {
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false";

        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO balances (uuid, username, balance) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE username = ?, balance = ?"
            );

            for (BalanceSnapshot snapshot : snapshots) {
                ps.setString(1, snapshot.uuid().toString());
                ps.setString(2, snapshot.username());
                ps.setDouble(3, snapshot.balance());
                ps.setString(4, snapshot.username());
                ps.setDouble(5, snapshot.balance());
                ps.addBatch();
            }

            ps.executeBatch();
        } catch (Exception e) {
            plugin.getLogger().warning("Balance sync failed: " + e.getMessage());
        }
    }

    /**
     * Cancels the balance sync task and clears the economy reference on shutdown.
     */
    @Override
    public void disable() {
        if (taskRegistrar != null) {
            taskRegistrar.cancelAll();
        }
        economy = null;
    }

    /**
     * Returns whether the economy provider is available.
     *
     * @return true if the economy is ready for use
     */
    public static boolean isReady() {
        return economy != null;
    }

    /**
     * Returns the balance of a player.
     *
     * @param player the player
     * @return the player's balance, or 0 if economy is unavailable
     */
    public static double getBalance(OfflinePlayer player) {
        if (!isReady()) return 0;
        return economy.getBalance(player);
    }

    /**
     * Checks whether a player has at least the given amount.
     *
     * @param player the player
     * @param amount the required amount
     * @return true if the player can afford it
     */
    public static boolean has(OfflinePlayer player, double amount) {
        if (!isReady()) return false;
        return economy.has(player, amount);
    }

    /**
     * Withdraws the given amount from a player's balance.
     *
     * @param player the player
     * @param amount the amount to withdraw
     * @return true if the transaction succeeded
     */
    public static boolean withdraw(OfflinePlayer player, double amount) {
        if (!isReady()) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    /**
     * Deposits the given amount into a player's balance.
     *
     * @param player the player
     * @param amount the amount to deposit
     * @return true if the transaction succeeded
     */
    public static boolean deposit(OfflinePlayer player, double amount) {
        if (!isReady()) return false;
        return economy.depositPlayer(player, amount).transactionSuccess();
    }

    /**
     * Formats a monetary value for display using commas and two decimal places.
     *
     * @param amount the amount to format
     * @return the formatted string
     */
    public static String format(double amount) {
        return String.format("%,.2f", amount);
    }

    /**
     * Returns the raw Vault Economy instance.
     *
     * @return the economy provider, or null if not available
     */
    public static Economy get() {
        return economy;
    }

    private record BalanceSnapshot(UUID uuid, String username, double balance) {
    }
}
