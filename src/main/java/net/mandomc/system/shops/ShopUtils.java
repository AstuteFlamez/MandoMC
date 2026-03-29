package net.mandomc.system.shops;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.deecaad.weaponmechanics.WeaponMechanicsAPI;
import net.mandomc.system.items.ItemRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility methods for shop items.
 */
public class ShopUtils {

    /**
     * Builds the display item shown in the GUI.
     *
     * @param section config section of the item
     * @return display ItemStack
     */
    public static ItemStack buildDisplayItem(ConfigurationSection section) {

        // Default material (you can extend later)
        Material material = Material.PAPER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        // Name
        String name = section.getString("display.name", "&7Item");
        meta.setDisplayName(color(name));

        // Lore
        List<String> rawLore = section.getStringList("display.lore");
        List<String> lore = new ArrayList<>();

        int price = section.getInt("price.amount");

        for (String line : rawLore) {
            line = line.replace("%price%", String.valueOf(price));
            lore.add(color(line));
        }

        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Creates the actual item given to the player.
     *
     * @param item shop item
     * @param amount amount to give
     * @return ItemStack
     */
    public static ItemStack createItem(ShopItem item, int amount) {

        switch (item.getType()) {

            case VANILLA:
                return new ItemStack(Material.valueOf(item.getId()), amount);

            case WEAPON_MECHANICS_AMMO: {
                ItemStack ammo = WeaponMechanicsAPI.generateAmmo(item.getId(), false);

                if (ammo == null) return null;

                ammo = ammo.clone();
                ammo.setAmount(amount);
                return ammo;
            }

            case WEAPON_MECHANICS_WEAPON: {
                ItemStack weapon = WeaponMechanicsAPI.generateWeapon(item.getId());

                if (weapon == null) return null;

                weapon = weapon.clone();
                weapon.setAmount(amount);
                return weapon;
            }

            case CUSTOM: {
                ItemStack custom = ItemRegistry.get(item.getId());

                if (custom == null) return null;

                custom = custom.clone();
                custom.setAmount(amount);
                return custom;
            }


            case GENERIC: {
                // 🔥 Used for filler or display-only items
                ItemStack base = item.getDisplay();

                if (base == null) return null;

                base = base.clone();
                base.setAmount(amount);
                return base;
            }
        }

        return null;
    }

    /**
     * Applies color formatting.
     */
    private static String color(String text) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }
}