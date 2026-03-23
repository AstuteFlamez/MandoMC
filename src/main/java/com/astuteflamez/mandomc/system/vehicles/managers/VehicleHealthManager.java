package com.astuteflamez.mandomc.system.vehicles.managers;

import org.bukkit.inventory.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.astuteflamez.mandomc.core.MandoMC;

public class VehicleHealthManager {

    private static final NamespacedKey CURRENT_HEALTH =
            new NamespacedKey(MandoMC.getInstance(), "current_health");

    private static final NamespacedKey MAX_HEALTH =
            new NamespacedKey(MandoMC.getInstance(), "max_health");

    public static double getCurrentHealth(ItemStack item) {
        if (item == null) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;

        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.getOrDefault(
                CURRENT_HEALTH,
                PersistentDataType.DOUBLE,
                0.0
        );
    }

    public static double getMaxHealth(ItemStack item) {
        if (item == null) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;

        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.getOrDefault(
                MAX_HEALTH,
                PersistentDataType.DOUBLE,
                0.0
        );
    }

    public static void damage(ItemStack item, double damage, Player player) {
        double current = getCurrentHealth(item);
        // Ensure we are subtracting, not adding via negative damage
        setHealth(item, current - Math.max(0, damage), player);
    }

    public static void setHealth(ItemStack item, double newHealth, Player player) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        double maxHealth = getMaxHealth(item);

        // Check if health has dropped to or below zero before clamping
        if (newHealth <= 0) {
            VehicleManager.explodeVehicle(player);
        }

        // Clamp the value between 0 and Max Health
        double clampedHealth = Math.max(0, Math.min(newHealth, maxHealth));

        container.set(CURRENT_HEALTH, PersistentDataType.DOUBLE, clampedHealth);
        item.setItemMeta(meta);
    }
}