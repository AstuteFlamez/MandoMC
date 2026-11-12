package net.mandomc.integrations.mobs;

import java.util.List;
import net.mandomc.gameplay.mobs.HerdAge;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Native age survives saves; both death pipelines suppress baby rewards regardless of killer. */
public final class HerdBabies implements Listener {
    private final MythicMobAccess mobs;

    @SuppressWarnings("unchecked")
    public HerdBabies(JavaPlugin plugin, MythicMobAccess mobs) throws ReflectiveOperationException {
        this.mobs = mobs;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        var vendor = Bukkit.getPluginManager().getPlugin("MythicMobs");
        var type = (Class<? extends Event>) Class.forName("io.lumine.mythic.bukkit.events.MythicMobDeathEvent",
                true, vendor.getClass().getClassLoader());
        var entity = type.getMethod("getEntity");
        var mobType = type.getMethod("getMobType");
        var name = mobType.getReturnType().getMethod("getInternalName");
        var drops = type.getMethod("getDrops");
        Bukkit.getPluginManager().registerEvent(type, this, EventPriority.HIGHEST, (listener, event) -> {
            try {
                String id = (String) name.invoke(mobType.invoke(event));
                if (!HerdAge.herd(id)) return;
                var animal = (Entity) entity.invoke(event);
                animal.getPersistentDataContainer().set(HerdAge.KIND, PersistentDataType.STRING, id);
                if (HerdAge.baby(animal)) ((List<ItemStack>) drops.invoke(event)).clear();
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot suppress baby herd drops", error);
            }
        }, plugin);
    }

    /** Used by the breeding authority; this does not add babies to natural encounter rolls. */
    public LivingEntity spawn(String type, Location location, int level) {
        if (!HerdAge.herd(type)) throw new IllegalArgumentException("Not a herd species: " + type);
        var entity = mobs.spawn(type, location, level);
        if (!(entity instanceof Ageable ageable)) {
            if (entity != null) mobs.remove(entity);
            throw new IllegalStateException(type + " must use a native ageable base");
        }
        ageable.setBaby();
        ageable.setAgeLock(false);
        entity.getPersistentDataContainer().set(HerdAge.KIND, PersistentDataType.STRING, type);
        entity.setPersistent(true);
        mobs.persist(entity);
        var modeled = com.ticxo.modelengine.api.ModelEngineAPI.getModeledEntity(entity);
        if (modeled != null) for (var model : modeled.getModels().values()) {
            net.mandomc.integrations.models.HerdModels.scale(entity, model);
        }
        return entity;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(EntityDeathEvent event) {
        var entity = event.getEntity();
        String type = entity.getPersistentDataContainer().get(HerdAge.KIND, PersistentDataType.STRING);
        if (!HerdAge.herd(type)) type = mobs.type(entity);
        if (!HerdAge.herd(type)) return;
        if (HerdAge.baby(entity)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        } else {
            // Retain native equipment drops and catalog rewards; replace only the pig/horse body loot.
            event.getDrops().removeIf(item -> !item.hasItemMeta() && switch (item.getType()) {
                case LEATHER, PORKCHOP, COOKED_PORKCHOP -> true;
                default -> false;
            });
        }
    }
}
