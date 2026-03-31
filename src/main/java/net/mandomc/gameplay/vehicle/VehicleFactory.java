package net.mandomc.gameplay.vehicle;

import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.mandomc.MandoMC;
import net.mandomc.gameplay.vehicle.config.VehicleConfig;

import java.util.ArrayList;
import java.util.List;

public class VehicleFactory {

    private static final NamespacedKey CURRENT_HEALTH =
            new NamespacedKey(MandoMC.getInstance(), "current_health");

    private static final NamespacedKey MAX_HEALTH =
            new NamespacedKey(MandoMC.getInstance(), "max_health");

    public static ItemStack applyStats(ItemStack item, String itemId) {

        String vehicleId = VehicleRegistry.getVehicleId(itemId);
        if (vehicleId == null) return item;

        FileConfiguration config = VehicleConfig.get(vehicleId);
        if (config == null) return item;

        ConfigurationSection stats = config.getConfigurationSection("vehicle.stats");
        if (stats == null) return item;

        double maxHealth = stats.getDouble("max_health", 100);
        double speed = stats.getDouble("speed", 1);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        PersistentDataContainer container = meta.getPersistentDataContainer();

        container.set(MAX_HEALTH, PersistentDataType.DOUBLE, maxHealth);
        container.set(CURRENT_HEALTH, PersistentDataType.DOUBLE, maxHealth);

        List<String> lore = meta.hasLore()
                ? new ArrayList<>(meta.getLore())
                : new ArrayList<>();

        lore.add("");
        lore.add(color("&6Combat Stats"));
        lore.add(color("&7Health: &c" + (int) maxHealth + "/" + (int) maxHealth));
        lore.add(color("&7Speed: &e" + speed));

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}