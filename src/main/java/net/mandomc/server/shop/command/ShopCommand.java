package net.mandomc.server.shop.command;

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
import java.util.UUID;
import net.mandomc.server.shop.model.Shop;
import net.mandomc.server.shop.ShopManager;
import net.mandomc.server.shop.gui.ShopGUI;

/**
 * Handles the /shop command.
 *
 * Usage:
 *   /shop <category>          — opens a shop category for self
 *   /shop <category> <player> — opens a shop category for another player
 *
 * Permissions:
 *   mandomc.shop.use    — /shop <category> for self
 *   mandomc.shop.others — /shop <category> <player>
 *
 * Tab completion:
 *   arg 0 → shop IDs
 *   arg 1 → player names (with others permission)
 */
public class ShopCommand implements CommandExecutor, TabCompleter {

    private static final String USE_PERMISSION = "mandomc.shop.use";
    private static final String OTHERS_PERMISSION = "mandomc.shop.others";

    private final GUIManager guiManager;

    public ShopCommand(GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1 || args.length > 2) {
            sender.sendMessage(LangManager.get("shops.usage-shop"));
            return true;
        }

        handleOpenShop(sender, args);
        return true;
    }

    private void handleOpenShop(CommandSender sender, String[] args) {
        String shopId = args[0].toLowerCase();
        Shop shop = ShopManager.get(shopId);

        if (shop == null) {
            sender.sendMessage(LangManager.get("shops.not-found", "%shop%", shopId));
            return;
        }

        Player target;

        if (args.length >= 2) {
            if (!sender.hasPermission(OTHERS_PERMISSION)) {
                sender.sendMessage(LangManager.get("shops.no-permission"));
                return;
            }

            target = resolveOnlinePlayer(args[1]);
            if (target == null) {
                sender.sendMessage(LangManager.get("shops.player-not-found"));
                return;
            }
        } else {
            if (!sender.hasPermission(USE_PERMISSION)) {
                sender.sendMessage(LangManager.get("shops.no-permission"));
                return;
            }

            if (!(sender instanceof Player)) {
                sender.sendMessage(LangManager.get("shops.console-no-player-shop"));
                return;
            }
            target = (Player) sender;
        }

        guiManager.openGUI(new ShopGUI(shop, guiManager), target);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            if (sender.hasPermission(USE_PERMISSION) || sender.hasPermission(OTHERS_PERMISSION)) {
                return filter(new ArrayList<>(ShopManager.getShopIds()), args[0]);
            }
            return List.of();
        }

        if (args.length == 2) {
            if (sender.hasPermission(OTHERS_PERMISSION)) {
                return filter(onlinePlayerNames(), args[1]);
            }
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

    private static Player resolveOnlinePlayer(String input) {
        Player exact = Bukkit.getPlayerExact(input);
        if (exact != null) {
            return exact;
        }

        Player byName = Bukkit.getPlayer(input);
        if (byName != null) {
            return byName;
        }

        try {
            return Bukkit.getPlayer(UUID.fromString(input));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
