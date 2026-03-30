package net.mandomc.system.shops;

import net.mandomc.core.LangManager;
import net.mandomc.core.guis.GUIManager;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the /shop command.
 *
 * Usage:
 *   /shop buy <shop>          — opens the buy GUI for the executing player
 *   /shop buy <shop> <player> — opens the buy GUI for another player (console-safe)
 *   /shop sell <player>       — opens the sell GUI for a player
 *
 * Permission: mandomc.shop.admin
 *
 * Tab completion:
 *   arg 0 → "buy" | "sell"
 *   buy arg 1 → shop IDs, buy arg 2 → player names
 *   sell arg 1 → player names
 */
public class ShopCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "mandomc.shop.admin";

    private final GUIManager guiManager;

    public ShopCommand(GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(LangManager.get("shops.no-permission"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(LangManager.get("shops.usage"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "buy"  -> handleBuy(sender, label, args);
            case "sell" -> handleSell(sender, label, args);
            default     -> sender.sendMessage(LangManager.get("shops.usage"));
        }

        return true;
    }

    // /shop buy <shop> [player]
    private void handleBuy(CommandSender sender, String label, String[] args) {

        if (args.length < 2) {
            sender.sendMessage(LangManager.get("shops.usage-buy"));
            return;
        }

        String shopId = args[1].toLowerCase();

        Player target;

        if (args.length >= 3) {
            target = Bukkit.getPlayer(args[2]);
            if (target == null) {
                sender.sendMessage(LangManager.get("shops.player-not-found"));
                return;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(LangManager.get("shops.console-no-player"));
                return;
            }
            target = (Player) sender;
        }

        Shop shop = ShopManager.get(shopId);

        if (shop == null) {
            sender.sendMessage(LangManager.get("shops.not-found", "%shop%", shopId));
            return;
        }

        guiManager.openGUI(new ShopGUI(shop), target);
    }

    // /shop sell <player>
    private void handleSell(CommandSender sender, String label, String[] args) {

        if (args.length < 2) {
            sender.sendMessage(LangManager.get("shops.usage-sell"));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);

        if (target == null) {
            sender.sendMessage(LangManager.get("shops.player-not-found"));
            return;
        }

        ShopSellGUI sellGUI = new ShopSellGUI(target);
        guiManager.registerHandledInventory(sellGUI.getInventory(), sellGUI);
        target.openInventory(sellGUI.getInventory());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (!sender.hasPermission(PERMISSION)) return List.of();

        if (args.length == 1) {
            return filter(List.of("buy", "sell"), args[0]);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("buy")) return filter(new ArrayList<>(ShopManager.getShopIds()), args[1]);
            if (args[0].equalsIgnoreCase("sell")) return filter(onlinePlayerNames(), args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("buy")) {
            return filter(onlinePlayerNames(), args[2]);
        }

        return List.of();
    }

    private static List<String> onlinePlayerNames() {
        List<String> names = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(p -> names.add(p.getName()));
        return names;
    }

    private static List<String> filter(List<String> options, String partial) {
        String lower = partial.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String opt : options) {
            if (opt.toLowerCase().startsWith(lower)) result.add(opt);
        }
        return result;
    }
}
