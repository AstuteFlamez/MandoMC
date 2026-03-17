package com.astuteflamez.mandomc.features.items.guis;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;

import com.astuteflamez.mandomc.MandoMC;
import com.astuteflamez.mandomc.features.items.ItemRegistry;
import com.astuteflamez.mandomc.features.items.RecipeRegistry;

import java.util.*;

public class RecipeBrowserGUI {

    private static final String TITLE = "§8Recipes";
    private static final int ITEMS_PER_PAGE = 36;

    public static void open(Player player, int page) {

        Inventory gui = Bukkit.createInventory(null, 54, TITLE);

        fillBackground(gui);

        List<String> items = new ArrayList<>(RecipeRegistry.getRecipeItems());

        // 🔥 FIX: better sorting (category + id)
        items.sort(Comparator
                .comparing((String id) -> ItemRegistry.getCategory(id))
                .thenComparing(id -> id)
        );

        // 🔥 FIX: clamp page
        int maxPage = items.isEmpty() ? 0 : (items.size() - 1) / ITEMS_PER_PAGE;

        if (page > maxPage) page = maxPage;
        if (page < 0) page = 0;

        // 🔥 FIX: store page
        player.setMetadata("recipe_page",
                new FixedMetadataValue(MandoMC.getInstance(), page));

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, items.size());

        int slot = 9;

        for (int i = start; i < end; i++) {

            ItemStack item = ItemRegistry.get(items.get(i));

            if (item != null)
                gui.setItem(slot, item);

            slot++;

            if (slot % 9 == 0) slot++;
        }

        placeNavigation(gui, page, items.size());

        player.openInventory(gui);
    }

    private static void fillBackground(Inventory gui) {

        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);

        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }

        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, pane);
        }
    }

    private static void placeNavigation(Inventory gui, int page, int totalItems) {

        ItemStack next = new ItemStack(Material.ARROW);
        ItemMeta n = next.getItemMeta();
        if (n != null) {
            n.setDisplayName("§aNext Page");
            next.setItemMeta(n);
        }

        ItemStack prev = new ItemStack(Material.ARROW);
        ItemMeta p = prev.getItemMeta();
        if (p != null) {
            p.setDisplayName("§cPrevious Page");
            prev.setItemMeta(p);
        }

        if ((page + 1) * ITEMS_PER_PAGE < totalItems)
            gui.setItem(53, next);

        if (page > 0)
            gui.setItem(45, prev);
    }
}