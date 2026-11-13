package net.mandomc.integrations.mobs;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import net.mandomc.gameplay.items.ItemCatalog;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import net.mandomc.gameplay.mobs.HerdAge;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Adds adult hide and ordinary brown wool once through Mythic's death-drop pipeline; population cleanup does not generate loot. */
public final class BanthaDrops implements Listener {
    @SuppressWarnings("unchecked")
    public BanthaDrops(JavaPlugin plugin, ItemCatalog items, ConfigurationSection config)
            throws ReflectiveOperationException {
        Objects.requireNonNull(config, "mobs/drops/bantha.yml: drops missing");
        int min = config.getInt("minimum", -1);
        int max = config.getInt("maximum", -1);
        if (!config.isInt("minimum") || !config.isInt("maximum") || min < 1 || max < min || max > 64) {
            throw new IllegalArgumentException("mobs/drops/bantha.yml: require 1 <= drops.minimum <= drops.maximum <= 64");
        }
        int woolMin = config.getInt("wool-minimum", 1);
        int woolMax = config.getInt("wool-maximum", 3);
        if (woolMin < 1 || woolMax < woolMin || woolMax > 64
                || config.contains("wool-minimum") && !config.isInt("wool-minimum")
                || config.contains("wool-maximum") && !config.isInt("wool-maximum")) {
            throw new IllegalArgumentException("mobs/drops/bantha.yml: require 1 <= wool-minimum <= wool-maximum <= 64");
        }
        Objects.requireNonNull(items.create("bantha_hide"), "Missing bantha_hide item");
        var vendor = Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("MythicMobs"), "Missing MythicMobs");
        var type = (Class<? extends Event>) Class.forName("io.lumine.mythic.bukkit.events.MythicMobDeathEvent",
                true, vendor.getClass().getClassLoader());
        var mobType = type.getMethod("getMobType");
        var name = mobType.getReturnType().getMethod("getInternalName");
        var drops = type.getMethod("getDrops");
        var entity = type.getMethod("getEntity");
        Bukkit.getPluginManager().registerEvent(type, this, EventPriority.HIGH, (listener, event) -> {
            try {
                if (!"Bantha".equals(name.invoke(mobType.invoke(event)))
                        || HerdAge.baby((org.bukkit.entity.Entity) entity.invoke(event))) return;
                var hide = Objects.requireNonNull(items.create("bantha_hide"), "Missing bantha_hide item");
                hide.setAmount(ThreadLocalRandom.current().nextInt(min, max + 1));
                ((List<ItemStack>) drops.invoke(event)).add(hide);
                ((List<ItemStack>) drops.invoke(event)).add(new ItemStack(Material.BROWN_WOOL,
                        ThreadLocalRandom.current().nextInt(woolMin, woolMax + 1)));
            } catch (ReflectiveOperationException error) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Bantha hide drop failed", error);
            }
        }, plugin);
    }
}
