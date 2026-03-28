package net.mandomc.mechanics.bounties;

import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.modules.core.EconomyModule;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles /bounty command logic.
 */
public class BountyCommand implements CommandExecutor, TabCompleter {

    private final GUIManager guiManager;

    public BountyCommand(GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player p = (Player) sender;

        // =========================
        // 🧾 BASE HELP
        // =========================
        if (args.length == 0) {
            sendHelp(p);
            return true;
        }

        // =========================
        // 🖥 GUI (ADMIN)
        // =========================
        if (args[0].equalsIgnoreCase("gui")) {

            if (!p.hasPermission("mandomc.bounty.gui")) {
                p.sendMessage(color("&cNo permission."));
                return true;
            }

            // /bounty gui
            if (args.length == 1) {
                guiManager.openGUI(new BountyGUI(guiManager), p);
                return true;
            }

            // /bounty gui <player>
            if (args.length >= 2) {
                Player target = Bukkit.getPlayer(args[1]);

                if (target == null) {
                    p.sendMessage(color("&cPlayer not found."));
                    return true;
                }

                guiManager.openGUI(new BountyGUI(guiManager), target);
                return true;
            }
        }

        // =========================
        // 💰 PLACE BOUNTY
        // =========================
        if (args[0].equalsIgnoreCase("place")) {

            if (BountyStorage.hasPlaced(p.getUniqueId())) {
                p.sendMessage(color("&cYou already placed a bounty."));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
                return true;
            }

            guiManager.openGUI(new BountySelectGUI(guiManager, 0), p);
            return true;
        }

        // =========================
        // ❌ REMOVE BOUNTY
        // =========================
        if (args[0].equalsIgnoreCase("remove")) {

            UUID targetId = BountyStorage.getPlacedTarget(p.getUniqueId());

            if (targetId == null) {
                p.sendMessage(color("&cYou have no active bounty."));
                return true;
            }

            Bounty bounty = BountyStorage.get(targetId);

            if (bounty == null) {
                p.sendMessage(color("&cBounty not found."));
                return true;
            }

            BountyEntry entry = bounty.getEntries().get(p.getUniqueId());

            if (entry == null) {
                p.sendMessage(color("&cSomething went wrong."));
                return true;
            }

            // 💰 refund
            EconomyModule.deposit(p, entry.getAmount());

            bounty.removeEntry(p.getUniqueId());

            if (bounty.isEmpty()) {
                BountyStorage.remove(targetId);
            }

            p.sendMessage(color("&aBounty removed and refunded."));
            return true;
        }

        sendHelp(p);
        return true;
    }

    // =========================
    // 🔮 TAB COMPLETION
    // =========================
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {

        if (args.length == 1) {
            return Arrays.asList("place", "remove", "gui")
                    .stream()
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

    // =========================
    // 🧰 HELPERS
    // =========================

    private void sendHelp(Player p) {
        p.sendMessage(color("&c&lBounty Commands"));
        p.sendMessage(color("&7/bounty place <player> <amount>"));
        p.sendMessage(color("&7/bounty remove"));
        p.sendMessage(color("&7/bounty gui &8(Admin)"));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}