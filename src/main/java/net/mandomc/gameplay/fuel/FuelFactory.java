package net.mandomc.gameplay.fuel;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.mandomc.MandoMC;
import net.mandomc.core.LangManager;
import net.mandomc.gameplay.vehicle.VehicleRegistry;
import net.mandomc.gameplay.vehicle.config.VehicleConfig;
import net.mandomc.gameplay.fuel.manager.CanisterManager;
import net.mandomc.server.items.ItemUtils;
import net.mandomc.server.items.config.ItemsConfig;

/**
 * Applies fuel statistics to item stacks.
 *
 * Reads fuel configuration from either the vehicle config or the item config,
 * writes current and max fuel values to the item's PersistentDataContainer,
 * and appends fuel lore. Handles initial canister mode setup for rhydonium canisters.
 */
public class FuelFactory {

    private static final NamespacedKey CURRENT_FUEL =
            new NamespacedKey(MandoMC.getInstance(), "current_fuel");

    private static final NamespacedKey MAX_FUEL =
            new NamespacedKey(MandoMC.getInstance(), "max_fuel");

    private static final NamespacedKey MODE =
            new NamespacedKey(MandoMC.getInstance(), "mode");

    /**
     * Applies fuel stats to the given item based on its item ID.
     *
     * Checks the vehicle registry first; if the item is a vehicle it reads
     * fuel from the vehicle config. Otherwise reads from the items config.
     *
     * @param item   the item to apply stats to
     * @param itemId the item ID used to look up config
     * @return the item with fuel stats applied
     */
    public static ItemStack applyStats(ItemStack item, String itemId) {
        String vehicleId = VehicleRegistry.getVehicleId(itemId);

        if (vehicleId != null) {
            FileConfiguration config = VehicleConfig.get(vehicleId);

            if (config != null) {
                ConfigurationSection fuel = config.getConfigurationSection("vehicle.fuel");

                if (fuel != null) {
                    return applyFuel(item, fuel);
                }
            }
        }

        ConfigurationSection section = ItemsConfig.getItemSection(itemId);

        if (section == null) return item;
        if (!section.contains("fuel")) return item;

        ConfigurationSection fuel = section.getConfigurationSection("fuel");

        return applyFuel(item, fuel);
    }

    /**
     * Writes fuel PDC tags and lore to the given item using the provided config section.
     *
     * Sets current_fuel to the configured start value and max_fuel to the configured max.
     * If the item is a rhydonium canister, performs additional canister mode setup.
     *
     * @param item the item to modify
     * @param fuel the config section containing "max" and "start" values
     * @return the modified item
     */
    private static ItemStack applyFuel(ItemStack item, ConfigurationSection fuel) {
        int maxFuel = fuel.getInt("max", 100);
        int startFuel = fuel.getInt("start", 0);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(CURRENT_FUEL, PersistentDataType.INTEGER, startFuel);
        container.set(MAX_FUEL, PersistentDataType.INTEGER, maxFuel);

        List<String> lore = meta.hasLore()
                ? new ArrayList<>(meta.getLore())
                : new ArrayList<>();

        lore.add("");
        lore.add(color("&6Fuel Stats"));
        lore.add(color("&7Fuel: &c" + startFuel + "/" + maxFuel));

        meta.setLore(lore);
        item.setItemMeta(meta);

        if (ItemUtils.isItem(item, "rhydonium_canister")) {
            return canisterSetup(item);
        }

        return item;
    }

    /**
     * Sets the initial depositing mode tag on a canister item and updates its display name.
     *
     * @param item the canister item stack
     * @return the modified canister item
     */
    public static ItemStack canisterSetup(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(MODE, PersistentDataType.STRING, "depositing");

        boolean depositing = CanisterManager.getMode(item).equals("depositing");

        String name = meta.getDisplayName();

        if (name != null) {
            name = name.replaceAll(" &6\\[[^\\]]+\\]$", "");
            name = name + color(" &6[" + (depositing ? "Inject" : "Extract") + "]");
            meta.setDisplayName(name);
        }

        item.setItemMeta(meta);
        return item;
    }

    private static String color(String text) {
        return LangManager.colorize(text);
    }
}
