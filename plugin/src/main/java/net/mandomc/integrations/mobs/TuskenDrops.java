package net.mandomc.integrations.mobs;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.mandomc.gameplay.items.ItemCatalog;
import org.bukkit.Bukkit;
import org.bukkit.event.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Adds catalog weapons to Mythic's single death-drop pipeline; despawns never call this event. */
public final class TuskenDrops implements Listener {
    @SuppressWarnings("unchecked")
    public TuskenDrops(JavaPlugin plugin, ItemCatalog items, double chance) throws ReflectiveOperationException {
        if (!Double.isFinite(chance) || chance < 0 || chance > 1)
            throw new IllegalArgumentException("mobs/spawning.yml: weapon-drops.chance must be between 0 and 1");
        var vendor = Bukkit.getPluginManager().getPlugin("MythicMobs");
        var type = (Class<? extends Event>) Class.forName("io.lumine.mythic.bukkit.events.MythicMobDeathEvent",
                true, vendor.getClass().getClassLoader());
        var mobType = type.getMethod("getMobType");
        var name = mobType.getReturnType().getMethod("getInternalName");
        var drops = type.getMethod("getDrops");
        for (String id : List.of("gaffi_stick", "cycler_rifle")) {
            if (!items.ids().contains(id)) throw new IllegalArgumentException("Missing Tusken drop item: " + id);
        }
        Bukkit.getPluginManager().registerEvent(type, this, EventPriority.HIGH, (listener, event) -> {
            try {
                String id = switch ((String) name.invoke(mobType.invoke(event))) {
                    case "TuskenRaiderMelee" -> "gaffi_stick";
                    case "TuskenRaiderSniper" -> "cycler_rifle";
                    default -> null;
                };
                if (id != null && ThreadLocalRandom.current().nextDouble() < chance) {
                    ((List<ItemStack>) drops.invoke(event)).add(items.create(id));
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Tusken weapon drop failed", error);
            }
        }, plugin);
    }
}
