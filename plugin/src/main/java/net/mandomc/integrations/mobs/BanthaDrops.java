package net.mandomc.integrations.mobs;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import net.mandomc.gameplay.items.ItemCatalog;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Adds hide once through Mythic's death-drop pipeline; population cleanup does not generate loot. */
public final class BanthaDrops implements Listener {
    @SuppressWarnings("unchecked")
    public BanthaDrops(JavaPlugin plugin, ItemCatalog items, ConfigurationSection config)
            throws ReflectiveOperationException {
        Objects.requireNonNull(config, "bantha-hide.yml: drops missing");
        int min = config.getInt("minimum", -1);
        int max = config.getInt("maximum", -1);
        if (!config.isInt("minimum") || !config.isInt("maximum") || min < 1 || max < min || max > 64) {
            throw new IllegalArgumentException("bantha-hide.yml: require 1 <= drops.minimum <= drops.maximum <= 64");
        }
        Objects.requireNonNull(items.create("bantha_hide"), "Missing bantha_hide item");
        var vendor = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("MythicMobs"), "Missing MythicMobs");
        var type = (Class<? extends Event>) Class.forName("io.lumine.mythic.bukkit.events.MythicMobDeathEvent",
                true, vendor.getClass().getClassLoader());
        var mobType = type.getMethod("getMobType");
        var name = mobType.getReturnType().getMethod("getInternalName");
        var drops = type.getMethod("getDrops");
        Bukkit.getPluginManager().registerEvent(type, this, EventPriority.HIGH, (listener, event) -> {
            try {
                if (!"Bantha".equals(name.invoke(mobType.invoke(event)))) return;
                var hide = Objects.requireNonNull(items.create("bantha_hide"), "Missing bantha_hide item");
                hide.setAmount(ThreadLocalRandom.current().nextInt(min, max + 1));
                ((List<ItemStack>) drops.invoke(event)).add(hide);
            } catch (ReflectiveOperationException error) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Bantha hide drop failed", error);
            }
        }, plugin);
    }
}
