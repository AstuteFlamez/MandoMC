package com.astuteflamez.mandomc.mechanics.fuel.managers;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.astuteflamez.mandomc.mechanics.fuel.FuelManager;

public class BarrelManager {

    private static final String HOLOGRAM_TAG = "rhydonium_barrel_hologram";

    /* ------------------------------------------------
       INVENTORY ITEM (6–10)
    ------------------------------------------------ */

    public static ItemStack updateItem(ItemStack item) {

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        int fuel = FuelManager.getCurrentFuel(item);
        int max = FuelManager.getMaxFuel(item);

        if (max <= 0) return item;

        double percent = (fuel * 100.0) / max;

        int cmd;

        if (percent >= 100) cmd = 10;
        else if (percent >= 75) cmd = 9;
        else if (percent >= 50) cmd = 8;
        else if (percent >= 25) cmd = 7;
        else cmd = 6;

        meta.setCustomModelData(cmd);
        item.setItemMeta(meta);

        return item;
    }

    /* ------------------------------------------------
       ARMORSTAND MODEL (10–14)
    ------------------------------------------------ */

    public static ItemStack updateModel(ItemStack item) {

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        int fuel = FuelManager.getCurrentFuel(item);
        int max = FuelManager.getMaxFuel(item);

        if (max <= 0) return item;

        double percent = (fuel * 100.0) / max;

        int stage;

        if (percent >= 100) stage = 4;
        else if (percent >= 75) stage = 3;
        else if (percent >= 50) stage = 2;
        else if (percent >= 25) stage = 1;
        else stage = 0;

        int cmd = 11 + stage;

        meta.setCustomModelData(cmd);
        item.setItemMeta(meta);

        return item;
    }

    /* ------------------------------------------------
       Apply +5 offset when placing
    ------------------------------------------------ */

    public static ItemStack applyPlacementOffset(ItemStack item) {

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return item;

        int cmd = meta.getCustomModelData();

        meta.setCustomModelData(cmd + 5);
        item.setItemMeta(meta);

        return item;
    }

    /* ------------------------------------------------
       Create hologram
    ------------------------------------------------ */

    public static ArmorStand createHologram(ArmorStand barrel) {

        Location loc = barrel.getLocation().clone().add(0, 1.4, 0);

        ArmorStand holo = barrel.getWorld().spawn(loc, ArmorStand.class);

        holo.setInvisible(true);
        holo.setMarker(true);
        holo.setGravity(false);
        holo.setCustomNameVisible(true);

        holo.addScoreboardTag(HOLOGRAM_TAG);

        updateHologram(barrel, holo);

        return holo;
    }

    /* ------------------------------------------------
       Update hologram
    ------------------------------------------------ */

    public static void updateHologram(ArmorStand barrel, ArmorStand holo) {

        ItemStack item = barrel.getEquipment().getHelmet();
        if (item == null) return;

        int fuel = FuelManager.getCurrentFuel(item);
        int max = FuelManager.getMaxFuel(item);

        if (max <= 0) return;

        double percent = (double) fuel / max;

        int filled = (int) Math.round(percent * 10);

        ChatColor color;

        if (percent >= 0.75) color = ChatColor.GREEN;
        else if (percent >= 0.40) color = ChatColor.YELLOW;
        else if (percent > 0) color = ChatColor.GOLD;
        else color = ChatColor.RED;

        StringBuilder bar = new StringBuilder();

        for (int i = 0; i < 10; i++) {
            if (i < filled) bar.append(color).append("■");
            else bar.append(ChatColor.DARK_GRAY).append("■");
        }

        holo.setCustomName(bar.toString());
    }

    /* ------------------------------------------------ */

    public static ArmorStand getHologram(ArmorStand barrel) {

        for (var entity : barrel.getPassengers()) {

            if (entity instanceof ArmorStand stand &&
                stand.getScoreboardTags().contains(HOLOGRAM_TAG)) {

                return stand;
            }
        }

        return null;
    }

    public static void removeHologram(ArmorStand barrel) {

        for (var entity : barrel.getPassengers()) {

            if (entity instanceof ArmorStand stand &&
                stand.getScoreboardTags().contains(HOLOGRAM_TAG)) {

                stand.remove();
            }
        }
    }
}