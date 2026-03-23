package com.astuteflamez.mandomc.mechanics.fuel;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.astuteflamez.mandomc.core.MandoMC;

import net.md_5.bungee.api.ChatColor;

public class FuelManager {

    private static final NamespacedKey CURRENT_FUEL =
            new NamespacedKey(MandoMC.getInstance(), "current_fuel");

    private static final NamespacedKey MAX_FUEL =
            new NamespacedKey(MandoMC.getInstance(), "max_fuel");

    public static int getCurrentFuel(ItemStack item) {

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;

        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.getOrDefault(
                CURRENT_FUEL,
                PersistentDataType.INTEGER,
                0
        );
    }

    public static int getMaxFuel(ItemStack item) {

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;

        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.getOrDefault(
                MAX_FUEL,
                PersistentDataType.INTEGER,
                0
        );
    }

    public static void updateFuel(ItemStack item, int newFuel) {

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer container = meta.getPersistentDataContainer();

        int maxFuel = getMaxFuel(item);

        container.set(CURRENT_FUEL, PersistentDataType.INTEGER, newFuel);

        item.setItemMeta(meta);

        updateDisplay(item, newFuel, maxFuel);
    }

    private static void updateDisplay(ItemStack item, int current, int max) {

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        /* -------------------------
        Update Lore
        ------------------------- */

        List<String> lore = meta.hasLore()
                ? new ArrayList<>(meta.getLore())
                : new ArrayList<>();

        boolean replaced = false;

        for (int i = 0; i < lore.size(); i++) {

            String stripped = ChatColor.stripColor(lore.get(i)).toLowerCase();

            if (stripped.startsWith("fuel:")) {

                lore.set(i, color("&7Fuel: &c" + current + "/" + max));
                replaced = true;
                break;
            }
        }

        if (!replaced) {
            lore.add("");
            lore.add(color("&6Fuel Stats"));
            lore.add(color("&7Fuel: &c" + current + "/" + max));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}