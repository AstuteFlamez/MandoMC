package net.mandomc.content.lightsabers;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.mandomc.MandoMC;
import net.mandomc.system.items.configs.ItemsConfig;

import java.util.ArrayList;
import java.util.List;

import static org.bukkit.ChatColor.translateAlternateColorCodes;

/**
 * Applies lightsaber-specific stats and metadata to items.
 *
 * Reads stat values from the item configuration and updates
 * attributes and lore accordingly.
 */
public class SaberFactory {

    /**
     * Applies configured stats to a lightsaber item.
     *
     * Updates attack damage attributes and appends formatted
     * combat stats to the item's lore.
     *
     * @param item the item to modify
     * @param itemId the config item id
     * @return the modified item
     */
    public static ItemStack applyStats(ItemStack item, String itemId) {

        ConfigurationSection stats = getStatsSection(itemId);
        if (stats == null) return item;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        double meleeDamage = stats.getDouble("melee_damage", 1);
        double throwDamage = stats.getDouble("throw_damage", 0);
        int throwCooldown = stats.getInt("throw_cooldown", 0);
        double swingSpeed = stats.getDouble("swing_speed", 1);

        applyDamageModifier(meta, itemId, meleeDamage);
        applyLore(meta, meleeDamage, throwDamage, throwCooldown, swingSpeed);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Retrieves the stats section for an item.
     *
     * @param itemId the item id
     * @return the stats section or null if not present
     */
    private static ConfigurationSection getStatsSection(String itemId) {
        ConfigurationSection section = ItemsConfig.getItemSection(itemId);
        if (section == null || !section.contains("stats")) return null;
        return section.getConfigurationSection("stats");
    }

    /**
     * Applies attack damage modifier to the item meta.
     *
     * @param meta the item meta
     * @param itemId the item id
     * @param damage the melee damage value
     */
    private static void applyDamageModifier(ItemMeta meta, String itemId, double damage) {

        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);

        NamespacedKey key = new NamespacedKey(
                MandoMC.getInstance(),
                itemId + "_damage"
        );

        AttributeModifier modifier = new AttributeModifier(
                key,
                damage,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.HAND
        );

        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, modifier);
    }

    /**
     * Appends combat stats to item lore.
     *
     * @param meta the item meta
     * @param meleeDamage melee damage value
     * @param throwDamage throw damage value
     * @param cooldown throw cooldown in seconds
     * @param swingSpeed swing speed value
     */
    private static void applyLore(ItemMeta meta,
                                 double meleeDamage,
                                 double throwDamage,
                                 int cooldown,
                                 double swingSpeed) {

        List<String> lore = meta.hasLore()
                ? new ArrayList<>(meta.getLore())
                : new ArrayList<>();

        lore.add("");
        lore.add(color("&6Combat Stats"));
        lore.add(color("&7Melee Damage: &c" + meleeDamage));
        lore.add(color("&7Throw Damage: &c" + throwDamage));
        lore.add(color("&7Throw Cooldown: &e" + cooldown + "s"));
        lore.add(color("&7Swing Speed: &b" + swingSpeed));

        meta.setLore(lore);
    }

    /**
     * Translates color codes in a string.
     *
     * @param text the text to colorize
     * @return the colored string
     */
    private static String color(String text) {
        return translateAlternateColorCodes('&', text);
    }
}