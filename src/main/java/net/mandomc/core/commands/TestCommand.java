package net.mandomc.core.commands;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Test command that gives the player a head item.
 */
public class TestCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        player.getInventory().addItem(getPlayerHead(player.getUniqueId()));
        player.sendMessage("§aGiven Notch head!");

        return true;
    }

    /**
     * Creates a player head item for the given UUID.
     *
     * @param uuid the UUID of the player whose head to create
     * @return an ItemStack of type PLAYER_HEAD with the player skin, or null if the player is unknown
     */
    public static ItemStack getPlayerHead(UUID uuid) {
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        if (offlinePlayer.getName() == null) return null;

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
        skullMeta.setOwningPlayer(offlinePlayer);
        head.setItemMeta(skullMeta);
        return head;
    }
}
