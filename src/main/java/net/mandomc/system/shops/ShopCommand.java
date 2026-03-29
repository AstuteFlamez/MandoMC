package net.mandomc.system.shops;

import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import net.mandomc.core.guis.GUIManager;

public class ShopCommand implements CommandExecutor {

    private final GUIManager guiManager;

    public ShopCommand(GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (!sender.hasPermission("mandomc.shop.admin")) {
            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cYou do not have permission to use this command.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cUsage: /shop <shop> <player>");
            return true;
        }

        String shopId = args[0].toLowerCase();
        Player player = Bukkit.getPlayer(args[1]);

        if (player == null) {
            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cPlayer not found.");
            return true;
        }

        Shop shop = ShopManager.get(shopId);

        // 🔥 HARD STOP (NO GUI CREATION)
        if (shop == null) {
            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cShop not found: §7" + shopId);
            sender.sendMessage("§7Loaded shops: §f" + ShopManager.getAll().keySet());
            return true;
        }

        guiManager.openGUI(new ShopGUI(guiManager, shop), player);

        return true;
    }
}