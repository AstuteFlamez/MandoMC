package com.astuteflamez.mandomc.features.small_features.fuel.managers;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.astuteflamez.mandomc.features.small_features.fuel.FuelManager;

public class BarrelManager {

    private static final String HOLOGRAM_TAG = "rhydonium_barrel_hologram";

    /* ------------------------------------------------
       Update barrel model based on fuel %
    ------------------------------------------------ */

    public static ItemStack updateModel(ItemStack item) {

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        int currentFuel = FuelManager.getCurrentFuel(item);
        int maxFuel = FuelManager.getMaxFuel(item);

        if (maxFuel <= 0) return item;

        int percent = (int) ((double) currentFuel / maxFuel * 100);

        int model;

        if (percent >= 100) {
            model = 10;
        } else if (percent >= 75) {
            model = 9;
        } else if (percent >= 50) {
            model = 8;
        } else if (percent >= 25) {
            model = 7;
        } else {
            model = 6;
        }

        meta.setCustomModelData(model);
        item.setItemMeta(meta);

        return item;
    }

    /* ------------------------------------------------
       Create hologram above barrel
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
       Update hologram fuel bar
    ------------------------------------------------ */

    public static void updateHologram(ArmorStand barrel, ArmorStand holo) {

        ItemStack barrelItem = barrel.getEquipment().getHelmet();
        if (barrelItem == null) return;

        int fuel = FuelManager.getCurrentFuel(barrelItem);
        int max = FuelManager.getMaxFuel(barrelItem);

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

            if (i < filled) {
                bar.append(color).append("■");
            } else {
                bar.append(ChatColor.DARK_GRAY).append("■");
            }
        }

        holo.setCustomName(bar.toString());
    }

    /* ------------------------------------------------
       Find hologram passenger
    ------------------------------------------------ */

    public static ArmorStand getHologram(ArmorStand barrel) {

        for (var entity : barrel.getPassengers()) {

            if (entity instanceof ArmorStand stand &&
                stand.getScoreboardTags().contains(HOLOGRAM_TAG)) {

                return stand;
            }
        }

        return null;
    }

    /* ------------------------------------------------
       Remove hologram when barrel removed
    ------------------------------------------------ */

    public static void removeHologram(ArmorStand barrel) {

        for (var entity : barrel.getPassengers()) {

            if (entity instanceof ArmorStand stand &&
                stand.getScoreboardTags().contains(HOLOGRAM_TAG)) {

                stand.remove();
            }
        }
    }
}