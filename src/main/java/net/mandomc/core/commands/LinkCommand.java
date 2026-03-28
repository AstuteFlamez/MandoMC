package net.mandomc.core.commands;

import net.mandomc.MandoMC;
import net.mandomc.core.modules.core.EconomyModule;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class LinkCommand implements CommandExecutor {

    private final MandoMC plugin;

    // =========================
    // PREFIX
    // =========================
    private static final String PREFIX = "§9§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7";

    public LinkCommand(MandoMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
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

            // =========================
            // PREVENT DUPLICATE LINKING
            // =========================
            PreparedStatement check = conn.prepareStatement(
                    "SELECT * FROM links WHERE minecraft_uuid = ? AND linked = TRUE LIMIT 1"
            );
            check.setString(1, uuid.toString());

            ResultSet rs = check.executeQuery();

            if (rs.next()) {
                player.sendMessage(PREFIX + "§cYou are already linked to Discord.");
                return true;
            }

            // =========================
            // GENERATE CODE
            // =========================
            String code = generateCode();

            PreparedStatement insert = conn.prepareStatement(
                    "INSERT INTO links (minecraft_uuid, link_code) VALUES (?, ?)"
            );

            insert.setString(1, uuid.toString());
            insert.setString(2, code);

            insert.executeUpdate();

            player.sendMessage(PREFIX + "§aYour link code: §e" + code);
            player.sendMessage(PREFIX + "§7Use §b/link " + code + " §7in Discord.");

            // =========================
            // POLL FOR LINK COMPLETION
            // =========================
            final int[] taskId = new int[1];

            taskId[0] = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {

                int attempts = 0;

                @Override
                public void run() {

                    try (Connection checkConn = DriverManager.getConnection(url, username, password)) {

                        PreparedStatement check = checkConn.prepareStatement(
                                "SELECT linked FROM links WHERE minecraft_uuid = ? ORDER BY created_at DESC LIMIT 1"
                        );

                        check.setString(1, uuid.toString());

                        ResultSet rs = check.executeQuery();

                        if (rs.next() && rs.getBoolean("linked")) {

                            Bukkit.getScheduler().runTask(plugin, () -> {
                                EconomyModule.deposit(player, 10000);
                                player.sendMessage(PREFIX + "§a✔ Your Discord account has been linked!");
                            });

                            Bukkit.getScheduler().cancelTask(taskId[0]);
                            return;
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    attempts++;

                    if (attempts > 12) { // ~1 minute timeout
                        Bukkit.getScheduler().cancelTask(taskId[0]);
                    }
                }

            }, 100L, 100L).getTaskId();

        } catch (Exception e) {
            e.printStackTrace();
            player.sendMessage(PREFIX + "§cError generating link code.");
        }

        return true;
    }

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