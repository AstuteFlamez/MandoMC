package net.mandomc.mechanics.fuel;

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
import net.mandomc.mechanics.fuel.managers.CanisterManager;
import net.mandomc.system.items.ItemUtils;
import net.mandomc.system.items.config.ItemsConfig;
import net.mandomc.system.vehicles.VehicleRegistry;
import net.mandomc.system.vehicles.config.VehicleConfig;

public class FuelFactory {

    private static final NamespacedKey CURRENT_FUEL =
            new NamespacedKey(MandoMC.getInstance(), "current_fuel");

    private static final NamespacedKey MAX_FUEL =
            new NamespacedKey(MandoMC.getInstance(), "max_fuel");

    private static final NamespacedKey MODE =
            new NamespacedKey(MandoMC.getInstance(), "mode");

    public static ItemStack applyStats(ItemStack item, String itemId) {

        /* =========================
           VEHICLE FUEL
        ========================= */

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

        /* =========================
           ITEM FUEL
        ========================= */

        ConfigurationSection section = ItemsConfig.getItemSection(itemId);

        if (section == null) return item;
        if (!section.contains("fuel")) return item;

        ConfigurationSection fuel = section.getConfigurationSection("fuel");

        return applyFuel(item, fuel);
    }

    /* =========================
       CORE FUEL APPLY
    ========================= */

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

    /* =========================
       CANISTER MODE
    ========================= */

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
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}