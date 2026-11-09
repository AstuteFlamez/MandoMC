package net.mandomc.integrations.mobs;

import java.io.File;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import net.mandomc.gameplay.items.ItemCatalog;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** One catalog food roll in Mythic's death pipeline; entity removal never awards food. */
public final class MassiffDrops implements Listener {
    @SuppressWarnings("unchecked")
    public MassiffDrops(JavaPlugin plugin, ItemCatalog items) throws Exception {
        var config = new YamlConfiguration();
        config.load(new File(plugin.getDataFolder(), "mobs/drops/massiff.yml"));
        String id = config.getString("item", "");
        double chance = config.getDouble("chance", Double.NaN);
        if (config.getInt("schema-version") != 1 || !Double.isFinite(chance) || chance < 0 || chance > 1
                || items.create(id) == null || !items.create(id).getType().isEdible()) {
            throw new IllegalArgumentException("mobs/drops/massiff.yml: expected schema 1, edible catalog item and chance 0–1");
        }
        var vendor = Bukkit.getPluginManager().getPlugin("MythicMobs");
        var type = (Class<? extends Event>) Class.forName("io.lumine.mythic.bukkit.events.MythicMobDeathEvent",
                true, vendor.getClass().getClassLoader());
        var mobType = type.getMethod("getMobType");
        var name = mobType.getReturnType().getMethod("getInternalName");
        var drops = type.getMethod("getDrops");
        Bukkit.getPluginManager().registerEvent(type, this, EventPriority.HIGH, (listener, event) -> {
            try {
                if ("Massiff".equals(name.invoke(mobType.invoke(event)))
                        && ThreadLocalRandom.current().nextDouble() < chance) {
                    ((List<ItemStack>) drops.invoke(event)).add(items.create(id));
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                plugin.getLogger().log(Level.SEVERE, "Massiff food drop failed", error);
            }
        }, plugin);
    }
}
