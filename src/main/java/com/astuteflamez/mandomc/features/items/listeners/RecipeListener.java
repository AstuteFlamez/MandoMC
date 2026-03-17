package com.astuteflamez.mandomc.features.items.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;

import com.astuteflamez.mandomc.features.items.ItemUtils;
import com.astuteflamez.mandomc.features.items.RecipeRegistry;
import com.astuteflamez.mandomc.features.items.guis.RecipeBrowserGUI;
import com.astuteflamez.mandomc.features.items.guis.RecipeViewerGUI;

public class RecipeListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {

        if (!(e.getWhoClicked() instanceof Player player)) return;

        String title = e.getView().getTitle();

        if (!title.equals("§8Recipes") && !title.equals("§8Recipe Viewer")) return;

        e.setCancelled(true);

        if (e.getCurrentItem() == null) return;

        String name = e.getCurrentItem().getItemMeta() != null
                ? e.getCurrentItem().getItemMeta().getDisplayName()
                : "";

        int page = player.hasMetadata("recipe_page")
                ? player.getMetadata("recipe_page").get(0).asInt()
                : 0;

        /*
         * NEXT PAGE
         */
        if ("§aNext Page".equalsIgnoreCase(name)) {
            RecipeBrowserGUI.open(player, page + 1);
            return;
        }

        /*
         * PREVIOUS PAGE
         */
        if ("§cPrevious Page".equalsIgnoreCase(name)) {
            RecipeBrowserGUI.open(player, page - 1);
            return;
        }

        /*
         * BACK BUTTON (viewer)
         */
        if (e.getCurrentItem().getType() == Material.ARROW && title.equals("§8Recipe Viewer")) {
            RecipeBrowserGUI.open(player, page);
            return;
        }

        /*
         * OPEN RECIPE
         */
        String id = ItemUtils.getItemId(e.getCurrentItem());

        if (id == null) return;

        if (!RecipeRegistry.hasRecipe(id)) return;

        RecipeViewerGUI.open(player, id);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {

        if (e.getView().getTitle().contains("Recipe")) {
            e.setCancelled(true);
        }
    }
}