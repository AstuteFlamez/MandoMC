package com.bilal.mandomc.features.small_features.lightsabers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import com.bilal.mandomc.features.items.ItemUtils;
import com.bilal.mandomc.features.items.configs.ItemsConfig;

public class SaberManager {

    public static ConfigurationSection getStats(ItemStack item) {

        String id = ItemUtils.getItemId(item);

        if (id == null) return null;

        ConfigurationSection sec = ItemsConfig.getItemSection(id);

        if (sec == null) return null;

        return sec.getConfigurationSection("stats");
    }

    public static double getMeleeDamage(ItemStack item) {

        ConfigurationSection stats = getStats(item);

        if (stats == null) return 1;

        return stats.getDouble("melee_damage", 1);
    }

    public static double getThrowDamage(ItemStack item) {

        ConfigurationSection stats = getStats(item);

        if (stats == null) return 1;

        return stats.getDouble("throw_damage", 1);
    }

    public static int getThrowCooldown(ItemStack item) {

        ConfigurationSection stats = getStats(item);

        if (stats == null) return 40;

        return stats.getInt("throw_cooldown", 40);
    }
}