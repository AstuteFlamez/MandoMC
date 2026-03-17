package com.astuteflamez.mandomc.features.small_features.lightsabers;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import com.astuteflamez.mandomc.MandoMC;
import com.astuteflamez.mandomc.features.items.configs.ItemsConfig;

import java.util.ArrayList;
import java.util.List;

import static org.bukkit.ChatColor.translateAlternateColorCodes;

public class SaberFactory {

    public static ItemStack applyStats(ItemStack item, String itemId) {

        ConfigurationSection section = ItemsConfig.getItemSection(itemId);

        if (section == null) return item;

        if (!section.contains("stats")) return item;

        ConfigurationSection stats = section.getConfigurationSection("stats");

        double melee = stats.getDouble("melee_damage", 1);
        double throwDamage = stats.getDouble("throw_damage", 0);
        int cooldown = stats.getInt("throw_cooldown", 0);
        double swingSpeed = stats.getDouble("swing_speed", 1);

        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        /*
         * Remove existing attack modifiers
         */
        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);

        /*
         * Create modern AttributeModifier
         */
        NamespacedKey key = new NamespacedKey(MandoMC.getInstance(), itemId + "_damage");

        AttributeModifier damageModifier =
                new AttributeModifier(
                        key,
                        melee,
                        AttributeModifier.Operation.ADD_NUMBER,
                        EquipmentSlotGroup.HAND
                );

        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, damageModifier);

        /*
         * Lore block
         */
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();

        lore.add("");
        lore.add(color("&6Combat Stats"));
        lore.add(color("&7Melee Damage: &c" + melee));
        lore.add(color("&7Throw Damage: &c" + throwDamage));
        lore.add(color("&7Throw Cooldown: &e" + cooldown + "s"));
        lore.add(color("&7Swing Speed: &b" + swingSpeed));

        meta.setLore(lore);

        item.setItemMeta(meta);

        return item;
    }

    private static String color(String text) {
        return translateAlternateColorCodes('&', text);
    }
    
}