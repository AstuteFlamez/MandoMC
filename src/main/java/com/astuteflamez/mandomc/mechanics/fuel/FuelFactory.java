package com.astuteflamez.mandomc.mechanics.fuel;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.astuteflamez.mandomc.core.MandoMC;
import com.astuteflamez.mandomc.mechanics.fuel.managers.CanisterManager;
import com.astuteflamez.mandomc.system.items.ItemUtils;
import com.astuteflamez.mandomc.system.items.configs.ItemsConfig;

public class FuelFactory {

    private static final NamespacedKey CURRENT_FUEL =
            new NamespacedKey(MandoMC.getInstance(), "current_fuel");

    private static final NamespacedKey MAX_FUEL =
            new NamespacedKey(MandoMC.getInstance(), "max_fuel");

    private static final NamespacedKey MODE =
            new NamespacedKey(MandoMC.getInstance(), "mode");

    public static ItemStack applyStats(ItemStack item, String itemId) {

        ConfigurationSection section = ItemsConfig.getItemSection(itemId);

        if (section == null) return item;
        if (!section.contains("fuel")) return item;

        ConfigurationSection fuel = section.getConfigurationSection("fuel");

        int maxFuel = fuel.getInt("max", 100);
        int startFuel = fuel.getInt("start", 0);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        /* ---------------------------
           Store fuel in PDC
        --------------------------- */

        PersistentDataContainer container = meta.getPersistentDataContainer();

        container.set(CURRENT_FUEL, PersistentDataType.INTEGER, startFuel);
        container.set(MAX_FUEL, PersistentDataType.INTEGER, maxFuel);

        /* ---------------------------
           Update lore cleanly
        --------------------------- */

        List<String> lore = meta.hasLore()
                ? new ArrayList<>(meta.getLore())
                : new ArrayList<>();

        lore.add("");
        lore.add(color("&6Fuel Stats"));
        lore.add(color("&7Fuel: &c" + startFuel + "/" + maxFuel));

        meta.setLore(lore);
        item.setItemMeta(meta);

        if (ItemUtils.isItem(item, "rhydonium_canister")) {
            item = canisterSetup(item);
            return item;
        }

        return item;
    }

    public static ItemStack canisterSetup(ItemStack item) {

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(MODE, PersistentDataType.STRING, "depositing");

        boolean depositing = CanisterManager.getMode(item).equals("depositing");

        String name = meta.getDisplayName();

        if (name != null) {
            name = name.replaceAll(" &6\\[[DM]\\]$", ""); // remove existing mode suffix
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