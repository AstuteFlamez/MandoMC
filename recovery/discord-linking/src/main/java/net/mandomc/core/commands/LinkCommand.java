package net.mandomc.core.commands;

import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.mandomc.MandoMC;
import net.mandomc.core.LangManager;
import net.mandomc.core.services.EconomyService;
import net.mandomc.server.discord.service.LinkService;

/**
 * Handles the /link command, allowing players to link their Minecraft account
 * to a Discord account.
 *
 * Generates a six-character code, stores it in the database, and polls
 * for completion. Deposits a reward when linking is confirmed.
 */
public class LinkCommand implements CommandExecutor {

    private final MandoMC plugin;
    private final LinkService linkService;
    private final EconomyService economyService;

    /**
     * Creates the link command.
     *
     * @param plugin the plugin instance
     */
    public LinkCommand(MandoMC plugin, LinkService linkService, EconomyService economyService) {
        this.plugin = plugin;
        this.linkService = linkService;
        this.economyService = economyService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LangManager.get("link.players-only"));
            return true;
        }

        UUID uuid = player.getUniqueId();

        if (!linkService.isAvailable()) {
            player.sendMessage(LangManager.get("link.error"));
            return true;
        }

        runAsyncTask(() -> {
            try {
                if (linkService.isAlreadyLinked(uuid)) {
                    runSyncTask(() -> player.sendMessage(LangManager.get("link.already-linked")));
                    return;
                }

                String code = linkService.createPendingLink(uuid);
                runSyncTask(() -> {
                    player.sendMessage(LangManager.get("link.code", "%code%", code));
                    player.sendMessage(LangManager.get("link.instructions", "%code%", code));
                    startLinkPolling(uuid);
                });
            } catch (Exception e) {
                getLogger().warning("[Link] Failed to create link code: " + e.getMessage());
                runSyncTask(() -> player.sendMessage(LangManager.get("link.error")));
            }
        });

        return true;
    }

    private void startLinkPolling(UUID uuid) {
        final int[] taskId = new int[1];
        taskId[0] = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            int attempts = 0;

            @Override
            public void run() {
                Player onlinePlayer = Bukkit.getPlayer(uuid);
                if (onlinePlayer == null || !onlinePlayer.isOnline()) {
                    Bukkit.getScheduler().cancelTask(taskId[0]);
                    return;
                }

                if (linkService.isLinked(uuid)) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player linkedPlayer = Bukkit.getPlayer(uuid);
                        if (linkedPlayer == null || !linkedPlayer.isOnline()) {
                            return;
                        }
                        economyService.deposit(linkedPlayer, 10000);
                        linkedPlayer.sendMessage(LangManager.get("link.success"));
                    });
                    Bukkit.getScheduler().cancelTask(taskId[0]);
                    return;
                }

                attempts++;
                if (attempts > 12) {
                    Bukkit.getScheduler().cancelTask(taskId[0]);
                }
            }
        }, 100L, 100L).getTaskId();
    }

    private void runAsyncTask(Runnable runnable) {
        if (plugin == null) {
            runnable.run();
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    private void runSyncTask(Runnable runnable) {
        if (plugin == null) {
            runnable.run();
            return;
        }
        Bukkit.getScheduler().runTask(plugin, runnable);
    }

    private Logger getLogger() {
        if (plugin != null) {
            return plugin.getLogger();
        }
        return Logger.getLogger(LinkCommand.class.getName());
    }
}
