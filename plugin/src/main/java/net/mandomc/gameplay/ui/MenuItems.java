package net.mandomc.gameplay.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Shared background for player-facing inventory menus. */
public final class MenuItems {
    private MenuItems() { }

    public static void fill(Inventory inventory) {
        var pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        meta.setHideTooltip(true);
        pane.setItemMeta(meta);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (inventory.getItem(slot) == null) inventory.setItem(slot, pane.clone());
        }
    }
}
