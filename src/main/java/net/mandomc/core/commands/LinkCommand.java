package net.mandomc.core.commands;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.mandomc.MandoMC;
import net.mandomc.core.LangManager;
import net.mandomc.core.modules.core.EconomyModule;

/**
 * Handles the /link command, allowing players to link their Minecraft account
 * to a Discord account.
 *
 * Generates a six-character code, stores it in the database, and polls
 * for completion. Deposits a reward when linking is confirmed.
 */
public class LinkCommand implements CommandExecutor {

    private final MandoMC plugin;

    /**
     * Creates the link command.
     *
     * @param plugin the plugin instance
     */
    public LinkCommand(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LangManager.get("link.players-only"));
            return true;
        }

        UUID uuid = player.getUniqueId();

        String host = plugin.getConfig().getString("database.host");
        int port = plugin.getConfig().getInt("database.port");
        String database = plugin.getConfig().getString("database.name");
        String username = plugin.getConfig().getString("database.username");
        String password = plugin.getConfig().getString("database.password");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false";

        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            PreparedStatement check = conn.prepareStatement(
                    "SELECT * FROM links WHERE minecraft_uuid = ? AND linked = TRUE LIMIT 1"
            );
            check.setString(1, uuid.toString());

            ResultSet resultSet = check.executeQuery();

            if (resultSet.next()) {
                player.sendMessage(LangManager.get("link.already-linked"));
                return true;
            }

            String code = generateCode();

            PreparedStatement insert = conn.prepareStatement(
                    "INSERT INTO links (minecraft_uuid, link_code) VALUES (?, ?)"
            );
            insert.setString(1, uuid.toString());
            insert.setString(2, code);
            insert.executeUpdate();

            player.sendMessage(LangManager.get("link.code", "%code%", code));
            player.sendMessage(LangManager.get("link.instructions", "%code%", code));

            final int[] taskId = new int[1];

            taskId[0] = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {

                int attempts = 0;

                @Override
                public void run() {
                    try (Connection pollConn = DriverManager.getConnection(url, username, password)) {
                        PreparedStatement pollCheck = pollConn.prepareStatement(
                                "SELECT linked FROM links WHERE minecraft_uuid = ? ORDER BY created_at DESC LIMIT 1"
                        );
                        pollCheck.setString(1, uuid.toString());

                        ResultSet pollResult = pollCheck.executeQuery();

                        if (pollResult.next() && pollResult.getBoolean("linked")) {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                EconomyModule.deposit(player, 10000);
                                player.sendMessage(LangManager.get("link.success"));
                            });
                            Bukkit.getScheduler().cancelTask(taskId[0]);
                            return;
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    attempts++;
                    if (attempts > 12) {
                        Bukkit.getScheduler().cancelTask(taskId[0]);
                    }
                }

            }, 100L, 100L).getTaskId();

        } catch (Exception e) {
            e.printStackTrace();
            player.sendMessage(LangManager.get("link.error"));
        }

        return true;
    }

    /**
     * Generates a random six-character alphanumeric code.
     *
     * Uses characters that are easy to read and distinguish.
     *
     * @return the generated code
     */
    private String generateCode() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder code = new StringBuilder();

        for (int i = 0; i < 6; i++) {
            int index = ThreadLocalRandom.current().nextInt(chars.length());
            code.append(chars.charAt(index));
        }

        return code.toString();
    }
}
