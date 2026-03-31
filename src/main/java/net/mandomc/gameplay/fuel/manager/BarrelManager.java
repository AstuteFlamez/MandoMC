package net.mandomc.gameplay.fuel.manager;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.mandomc.gameplay.fuel.FuelManager;

/**
 * Manages visual updates for placed rhydonium barrel armor stands.
 *
 * Handles custom model data selection based on fuel level for both
 * inventory items and armor stand models. Also creates and updates
 * the fuel level hologram displayed above barrels.
 */
public class BarrelManager {

    private static final String HOLOGRAM_TAG = "rhydonium_barrel_hologram";

    /**
     * Updates the custom model data of an inventory barrel item based on fuel percentage.
     *
     * CMD values 6-10 correspond to 0%, 25%, 50%, 75%, and 100% fill levels.
     *
     * @param item the barrel item stack to update
     * @return the updated item
     */
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

    /**
     * Updates the custom model data of a barrel armor stand model based on fuel percentage.
     *
     * CMD values 11-15 correspond to stages 0-4 (0%, 25%, 50%, 75%, 100% fill levels).
     *
     * @param item the barrel model item stack to update
     * @return the updated item
     */
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

    /**
     * Applies a +5 custom model data offset to a barrel item for use when placed.
     *
     * @param item the item to offset
     * @return the modified item
     */
    public static ItemStack applyPlacementOffset(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasCustomModelData()) return item;

        int cmd = meta.getCustomModelData();
        meta.setCustomModelData(cmd + 5);
        item.setItemMeta(meta);

        return item;
    }

    /**
     * Creates and attaches a fuel level hologram armor stand above the given barrel.
     *
     * The hologram displays a 10-segment color-coded fuel bar.
     *
     * @param barrel the barrel armor stand to attach the hologram to
     * @return the created hologram armor stand
     */
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

    /**
     * Updates the name/display of the hologram to reflect the barrel's current fuel level.
     *
     * Uses a 10-segment bar with color-coded segments based on percentage.
     *
     * @param barrel the barrel armor stand
     * @param holo   the hologram armor stand to update
     */
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

    /**
     * Returns the hologram armor stand attached to the given barrel, or null if none.
     *
     * @param barrel the barrel armor stand
     * @return the hologram, or null
     */
    public static ArmorStand getHologram(ArmorStand barrel) {
        for (var entity : barrel.getPassengers()) {
            if (entity instanceof ArmorStand stand &&
                stand.getScoreboardTags().contains(HOLOGRAM_TAG)) {
                return stand;
            }
        }

        return null;
    }

    /**
     * Removes the hologram arm stand attached to the given barrel, if present.
     *
     * @param barrel the barrel armor stand
     */
    public static void removeHologram(ArmorStand barrel) {
        for (var entity : barrel.getPassengers()) {
            if (entity instanceof ArmorStand stand &&
                stand.getScoreboardTags().contains(HOLOGRAM_TAG)) {
                stand.remove();
            }
        }
    }
}
