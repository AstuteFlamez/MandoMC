package net.mandomc.server.shop.command;

import net.mandomc.core.LangManager;
import net.mandomc.core.guis.GUIManager;
import net.mandomc.server.shop.ShopSellService;
import net.mandomc.server.shop.gui.ShopSellGUI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class SellCommand implements CommandExecutor, TabCompleter {

    private static final String USE_PERMISSION = "mandomc.sell.use";
    private static final String ADVANCED_PERMISSION = "mandomc.sell.advanced";

    private final GUIManager guiManager;

    public SellCommand(GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            handleNoArgs(sender);
            return true;
        }

        if (args.length > 1) {
            sender.sendMessage(LangManager.get("shops.usage-sell"));
            return true;
        }

        String arg = args[0];
        if (arg.equalsIgnoreCase("hand")) {
            return handleQuickSell(sender, true);
        }
        if (arg.equalsIgnoreCase("all")) {
            return handleQuickSell(sender, false);
        }

        if (!sender.hasPermission(USE_PERMISSION)) {
            sender.sendMessage(LangManager.get("shops.no-permission"));
            return true;
        }

        Player target = Bukkit.getPlayer(arg);
        if (target == null) {
            sender.sendMessage(LangManager.get("shops.player-not-found"));
            return true;
        }

        openSellGui(target);
        return true;
    }

    private void handleNoArgs(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LangManager.get("shops.console-no-player-sell"));
            return;
        }

        if (!sender.hasPermission(ADVANCED_PERMISSION)) {
            sender.sendMessage(LangManager.get("shops.no-permission"));
            return;
        }

        openSellGui(player);
    }

    private boolean handleQuickSell(CommandSender sender, boolean hand) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LangManager.get("shops.players-only"));
            return true;
        }

        if (!sender.hasPermission(ADVANCED_PERMISSION)) {
            sender.sendMessage(LangManager.get("shops.no-permission"));
            return true;
        }

        if (hand) {
            ShopSellService.sellHand(player);
        } else {
            ShopSellService.sellAll(player);
        }
        return true;
    }

    private void openSellGui(Player target) {
        ShopSellGUI sellGUI = new ShopSellGUI(target);
        guiManager.registerHandledInventory(sellGUI.getInventory(), sellGUI);
        target.openInventory(sellGUI.getInventory());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        List<String> options = new ArrayList<>();
        if (sender.hasPermission(USE_PERMISSION)) {
            options.addAll(onlinePlayerNames());
        }
        if (sender.hasPermission(ADVANCED_PERMISSION)) {
            options.add("hand");
            options.add("all");
        }

        return filter(options, args[0]);
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
            if (opt.toLowerCase().startsWith(lower)) {
                result.add(opt);
            }
        }
        return result;
    }
}
