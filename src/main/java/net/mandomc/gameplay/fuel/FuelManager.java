package net.mandomc.gameplay.fuel;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.mandomc.MandoMC;
import net.mandomc.core.LangManager;
import net.md_5.bungee.api.ChatColor;

/**
 * Reads and writes fuel values on item stacks via PersistentDataContainer.
 *
 * Manages the current_fuel and max_fuel PDC keys, and keeps the
 * "Fuel: x/y" lore line in sync after every update.
 */
public class FuelManager {

    private static final NamespacedKey CURRENT_FUEL =
            new NamespacedKey(MandoMC.getInstance(), "current_fuel");

    private static final NamespacedKey MAX_FUEL =
            new NamespacedKey(MandoMC.getInstance(), "max_fuel");

    /**
     * Returns the current fuel stored in the given item's PDC.
     *
     * @param item the item to read from
     * @return the current fuel, or 0 if not set
     */
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

    /**
     * Returns the maximum fuel stored in the given item's PDC.
     *
     * @param item the item to read from
     * @return the max fuel, or 0 if not set
     */
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

    /**
     * Updates the current fuel value in the item's PDC and refreshes the lore display.
     *
     * @param item    the item to update
     * @param newFuel the new fuel value to write
     */
    public static void updateFuel(ItemStack item, int newFuel) {
        updateFuel(item, newFuel, true);
    }

    /**
     * Updates current fuel and optionally refreshes lore display.
     *
     * @param item          the item to update
     * @param newFuel       the new fuel value
     * @param refreshDisplay whether to update the lore line this call
     */
    public static void updateFuel(ItemStack item, int newFuel, boolean refreshDisplay) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        int maxFuel = container.getOrDefault(MAX_FUEL, PersistentDataType.INTEGER, 0);
        int clampedFuel = maxFuel > 0 ? Math.max(0, Math.min(newFuel, maxFuel)) : Math.max(0, newFuel);
        int currentFuel = container.getOrDefault(CURRENT_FUEL, PersistentDataType.INTEGER, 0);

        if (currentFuel == clampedFuel && !refreshDisplay) {
            return;
        }

        container.set(CURRENT_FUEL, PersistentDataType.INTEGER, clampedFuel);

        item.setItemMeta(meta);

        if (refreshDisplay) {
            updateDisplay(item, clampedFuel, maxFuel);
        }
    }

    /**
     * Updates the "Fuel: x/y" lore line on the item.
     *
     * Replaces the existing fuel lore line if found, or appends a new one.
     *
     * @param item    the item whose lore to update
     * @param current the current fuel value
     * @param max     the max fuel value
     */
    private static void updateDisplay(ItemStack item, int current, int max) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

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
        return LangManager.colorize(s);
    }
}
