package net.mandomc.integrations.mobs;

import java.lang.reflect.Method;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Tameable;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Removes encounter leveling without respawning entities or resetting combat/animal state. */
public final class MobBaseStats implements Listener {
    public static final Set<String> TYPES = Set.of("TuskenRaiderSniper", "TuskenRaiderMelee", "Bantha",
            "Dewback", "Massiff", "GamorreanGuard", "QuarrenGuard", "TwilekGuard");
    private final JavaPlugin plugin;
    private final MythicMobAccess mobs;
    private final Method active;
    private final Object api;
    private final Method setLevel;
    private final Method setDisplayName;

    @SuppressWarnings("unchecked")
    public MobBaseStats(JavaPlugin plugin, MythicMobAccess mobs, Object vendor, Object api,
            Method active, Class<?> activeType) throws ReflectiveOperationException {
        this.plugin = plugin;
        this.mobs = mobs;
        this.api = api;
        this.active = active;
        setLevel = activeType.getMethod("setLevel", double.class);
        setDisplayName = activeType.getMethod("setDisplayName", String.class);
        var eventType = (Class<? extends Event>) Class.forName(
                "io.lumine.mythic.bukkit.events.MythicMobSpawnEvent", true, vendor.getClass().getClassLoader());
        var entity = eventType.getMethod("getEntity");
        var mobType = eventType.getMethod("getMobType");
        var name = mobType.getReturnType().getMethod("getInternalName");
        var level = eventType.getMethod("setMobLevel", double.class);
        Bukkit.getPluginManager().registerEvent(eventType, this, EventPriority.HIGHEST, (listener, event) -> {
            try {
                if (!TYPES.contains((String) name.invoke(mobType.invoke(event)))) return;
                level.invoke(event, 1.0);
                later((Entity) entity.invoke(event));
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Cannot use base mob stats", error);
            }
        }, plugin, true);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getWorlds().forEach(world ->
                world.getLivingEntities().forEach(this::normalize)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void loaded(EntitiesLoadEvent event) {
        event.getEntities().forEach(this::later);
    }

    private void later(Entity entity) {
        Bukkit.getScheduler().runTask(plugin, () -> normalize(entity));
    }

    /** Final current HP is min(previous HP, new maximum); low-health fights receive no healing. */
    public void normalize(Entity candidate) {
        if (!(candidate instanceof LivingEntity entity) || !entity.isValid() || entity.isDead()
                || !TYPES.contains(mobs.type(entity))) return;
        try {
            var mob = active.invoke(api, entity);
            String oldName = entity.getCustomName();
            if (mobs.level(entity) != 1) {
                double health = entity.getHealth();
                boolean ai = entity.hasAI();
                var attributes = new java.util.HashMap<org.bukkit.attribute.AttributeInstance, Double>();
                for (var attribute : org.bukkit.Registry.ATTRIBUTE) {
                    if (attribute == org.bukkit.attribute.Attribute.MAX_HEALTH
                            || attribute == org.bukkit.attribute.Attribute.ATTACK_DAMAGE) continue;
                    var instance = entity.getAttribute(attribute);
                    if (instance != null) attributes.put(instance, instance.getBaseValue());
                }
                int age = entity instanceof Ageable animal ? animal.getAge() : 0;
                boolean ageLock = entity instanceof Ageable animal && animal.getAgeLock();
                var target = entity instanceof Mob creature ? creature.getTarget() : null;
                boolean tamed = entity instanceof Tameable animal && animal.isTamed();
                var owner = entity instanceof Tameable animal ? animal.getOwner() : null;
                // Pinned vendor API recalculates its stat registry and persists the authoritative level.
                setLevel.invoke(mob, 1.0);
                attributes.forEach(org.bukkit.attribute.AttributeInstance::setBaseValue);
                entity.setAI(ai);
                if (entity instanceof Ageable animal) { animal.setAge(age); animal.setAgeLock(ageLock); }
                if (entity instanceof Tameable animal) { animal.setOwner(owner); animal.setTamed(tamed); }
                if (entity instanceof Mob creature) creature.setTarget(target);
                entity.setHealth(Math.min(health, entity.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
            }
            if (oldName != null) {
                String name = oldName.replaceAll("(?i)(?:§[0-9a-fk-or])*\\s*\\[Lv\\. (?:§[0-9a-fk-or])*[0-9.]+\\]", "")
                        .replaceAll("(?i)(?:§[0-9a-fk-or])*Lvl\\. (?:§[0-9a-fk-or])*[0-9.]+ (?:§[0-9a-fk-or])*- ", "");
                if (!name.equals(oldName)) {
                    name = name.stripTrailing();
                    setDisplayName.invoke(mob, name);
                    entity.setCustomName(name);
                }
            }
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot normalize existing mob " + entity.getUniqueId(), error);
        }
    }
}
