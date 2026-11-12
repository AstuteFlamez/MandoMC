package net.mandomc.integrations.mobs;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.mandomc.gameplay.items.ItemCatalog;
import net.mandomc.gameplay.mobs.JabbaDropsSettings;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Replaces the legacy op console commands with Mythic's single, collectible death-loot pipeline. */
public final class JabbaDrops implements Listener {
    @SuppressWarnings("unchecked")
    public JabbaDrops(JavaPlugin plugin, ItemCatalog items, JabbaDropsSettings settings)
            throws ReflectiveOperationException {
        var vendor = Bukkit.getPluginManager().getPlugin("MythicMobs");
        if (vendor == null || !vendor.isEnabled()) {
            throw new IllegalStateException("jabba-mobs.enabled requires MythicMobs");
        }
        for (var rule : settings.mobs().values()) {
            if (!items.ids().contains(rule.weapon()) || (rule.ammoRolls() > 0 && !items.ids().contains(rule.ammo()))
                    || !items.ids().containsAll(rule.salvage().pool())) {
                throw new IllegalArgumentException("mobs/drops/jabba.yml: unknown catalog weapon or ammo item");
            }
        }
        var type = (Class<? extends Event>) Class.forName("io.lumine.mythic.bukkit.events.MythicMobDeathEvent",
                true, vendor.getClass().getClassLoader());
        var mobType = type.getMethod("getMobType");
        var name = mobType.getReturnType().getMethod("getInternalName");
        var drops = type.getMethod("getDrops");
        Bukkit.getPluginManager().registerEvent(type, this, EventPriority.HIGH, (listener, event) -> {
            try {
                var rule = settings.mobs().get((String) name.invoke(mobType.invoke(event)));
                if (rule == null) return;
                // Prepare the whole roll before touching the vendor list; failed factories cannot leave partial loot.
                var additions = new java.util.ArrayList<ItemStack>();
                for (String id : rule.roll(ThreadLocalRandom.current())) {
                    var item = items.create(id);
                    if (item == null) throw new IllegalStateException("Missing Jabba loot item: " + id);
                    item.setAmount(1);
                    additions.add(item);
                }
                ((List<ItemStack>) drops.invoke(event)).addAll(additions);
            } catch (ReflectiveOperationException | RuntimeException error) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Jabba mob drop failed", error);
            }
        }, plugin);
        new RancorTrophyDrops(plugin,items);
    }
}
