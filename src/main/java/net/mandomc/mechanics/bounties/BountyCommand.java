package net.mandomc.mechanics.bounties;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import net.mandomc.core.LangManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.modules.core.EconomyModule;

/**
 * Handles the /bounty command.
 *
 * Supports sub-commands: place, remove, gui (admin).
 * Tab-completes sub-command names and online player names.
 */
public class BountyCommand implements CommandExecutor, TabCompleter {

    private final GUIManager guiManager;

    /**
     * Creates the bounty command.
     *
     * @param guiManager the GUI manager for opening bounty GUIs
     */
    public BountyCommand(GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LangManager.get("bounties.players-only"));
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("gui")) {
            if (!player.hasPermission("mandomc.bounty.gui")) {
                player.sendMessage(LangManager.get("bounties.no-permission"));
                return true;
            }

            if (args.length == 1) {
                guiManager.openGUI(new BountyGUI(guiManager), player);
                return true;
            }

            if (args.length >= 2) {
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(LangManager.get("bounties.player-not-found"));
                    return true;
                }
                guiManager.openGUI(new BountyGUI(guiManager), target);
                return true;
            }
        }

        if (args[0].equalsIgnoreCase("place")) {
            if (BountyStorage.hasPlaced(player.getUniqueId())) {
                player.sendMessage(LangManager.get("bounties.already-placed"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
                return true;
            }
            guiManager.openGUI(new BountySelectGUI(guiManager, 0), player);
            return true;
        }

        if (args[0].equalsIgnoreCase("remove")) {
            UUID targetId = BountyStorage.getPlacedTarget(player.getUniqueId());

            if (targetId == null) {
                player.sendMessage(LangManager.get("bounties.no-active-bounty"));
                return true;
            }

            Bounty bounty = BountyStorage.get(targetId);
            if (bounty == null) {
                player.sendMessage(LangManager.get("bounties.bounty-not-found"));
                return true;
            }

            BountyEntry entry = bounty.getEntries().get(player.getUniqueId());
            if (entry == null) {
                player.sendMessage(LangManager.get("bounties.something-wrong"));
                return true;
            }

            EconomyModule.deposit(player, entry.getAmount());
            bounty.removeEntry(player.getUniqueId());

            if (bounty.isEmpty()) {
                BountyStorage.remove(targetId);
            }

            player.sendMessage(LangManager.get("bounties.refunded"));
            return true;
        }

        sendHelp(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("place", "remove", "gui").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("place") || args[0].equalsIgnoreCase("gui")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("place")) {
            return Arrays.asList("100", "500", "1000", "5000", "10000");
        }

        return Collections.emptyList();
    }

    /**
     * Sends the help message to the given player.
     *
     * @param player the player to send help to
     */
    private void sendHelp(Player player) {
        player.sendMessage(LangManager.get("bounties.help.header"));
        player.sendMessage(LangManager.get("bounties.help.place"));
        player.sendMessage(LangManager.get("bounties.help.remove"));
        player.sendMessage(LangManager.get("bounties.help.gui"));
    }
}
