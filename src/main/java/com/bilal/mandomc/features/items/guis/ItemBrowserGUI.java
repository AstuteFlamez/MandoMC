package com.bilal.mandomc.features.items.guis;

import com.bilal.mandomc.MandoMC;
import com.bilal.mandomc.features.items.ItemRegistry;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;

public class ItemBrowserGUI {

    private static final String TITLE = "§8Item Browser";
    private static final int ITEMS_PER_PAGE = 36;

    public static void open(Player player) {
        open(player, player, 0, null);
    }

    public static void open(Player viewer, Player target) {
        open(viewer, target, 0, null);
    }

    public static void open(Player viewer, Player target, int page, String category) {

        Inventory gui = Bukkit.createInventory(null, 54, TITLE);

        fillBackground(gui);

        List<String> categories = getCategories();
        placeCategoryTabs(gui, categories, category);

        List<String> items = getItemsForCategory(category);

        // 🔥 FIX: clamp page
        int maxPage = items.isEmpty() ? 0 : (items.size() - 1) / ITEMS_PER_PAGE;

        if (page > maxPage) page = maxPage;
        if (page < 0) page = 0;

        // 🔥 FIX: set metadata AFTER clamp
        viewer.setMetadata("item_browser_target",
                new FixedMetadataValue(MandoMC.getInstance(), target.getUniqueId()));

        viewer.setMetadata("item_browser_page",
                new FixedMetadataValue(MandoMC.getInstance(), page));

        viewer.setMetadata("item_browser_category",
                new FixedMetadataValue(MandoMC.getInstance(), category));

        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, items.size());

        int slot = 9;

        for (int i = start; i < end; i++) {

            ItemStack item = ItemRegistry.get(items.get(i));

            if (item != null) {
                gui.setItem(slot, item);
            }

            slot++;

            if (slot % 9 == 0) slot++;
        }

        placeNavigation(gui, page, items.size());

        viewer.openInventory(gui);
    }

    private static List<String> getCategories() {

        List<String> categories = new ArrayList<>(ItemRegistry.getCategories());

        Collections.sort(categories);

        return categories;
    }

    private static List<String> getItemsForCategory(String category) {

        List<String> ids = new ArrayList<>();

        for (String id : ItemRegistry.getItemIds()) {

            if (category == null || ItemRegistry.getCategory(id).equals(category)) {
                ids.add(id);
            }
        }

        Collections.sort(ids);

        return ids;
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

    private static void placeCategoryTabs(Inventory gui, List<String> categories, String selected) {

        int slot = 0;

        for (String category : categories) {

            ItemStack tab = new ItemStack(Material.PAPER);

            ItemMeta meta = tab.getItemMeta();
            if (meta != null) {

                if (category.equals(selected)) {
                    meta.setDisplayName("§a" + category);
                } else {
                    meta.setDisplayName("§e" + category);
                }

                tab.setItemMeta(meta);
            }

            gui.setItem(slot, tab);

            slot++;

            if (slot >= 9) break;
        }
    }

    private static void placeNavigation(Inventory gui, int page, int totalItems) {

        ItemStack next = new ItemStack(Material.ARROW);
        ItemMeta nextMeta = next.getItemMeta();
        if (nextMeta != null) {
            nextMeta.setDisplayName("§aNext Page");
            next.setItemMeta(nextMeta);
        }

        ItemStack prev = new ItemStack(Material.ARROW);
        ItemMeta prevMeta = prev.getItemMeta();
        if (prevMeta != null) {
            prevMeta.setDisplayName("§cPrevious Page");
            prev.setItemMeta(prevMeta);
        }

        if ((page + 1) * ITEMS_PER_PAGE < totalItems) {
            gui.setItem(53, next);
        }

        if (page > 0) {
            gui.setItem(45, prev);
        }
    }
}