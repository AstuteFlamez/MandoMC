package net.mandomc.integrations.mobs;

import java.lang.reflect.Method;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;
import net.mandomc.gameplay.territory.TerritoryService;

/** Pinned Mythic API adapter: remove protected targets from skill snapshots and threat tables. */
public final class SafezoneMobs implements Listener {
    @SuppressWarnings("unchecked")
    public SafezoneMobs(JavaPlugin plugin, Object vendor, Object api, Method active, Class<?> activeType) throws ReflectiveOperationException {
        var loader = vendor.getClass().getClassLoader();
        var abstractEntity = Class.forName("io.lumine.mythic.api.adapters.AbstractEntity", true, loader);
        var bukkit = abstractEntity.getMethod("getBukkitEntity");
        var metadata = Class.forName("io.lumine.mythic.api.skills.SkillMetadata", true, loader);
        var caster = metadata.getMethod("getCaster");
        var casterEntity = caster.getReturnType().getMethod("getEntity");
        var mobType = activeType.getMethod("getMobType");
        var hostiles = Set.of("BobaFett", "Bossk", "Rancor", "GamorreanGuard", "QuarrenGuard", "TwilekGuard", "TuskenRaiderSniper", "TuskenRaiderMelee", "Massiff");
        var targets = metadata.getMethod("getEntityTargets");
        var replace = metadata.getMethod("setEntityTargets", Collection.class);
        for (String name : List.of("MythicTargetEvent", "MythicSkillEvent")) {
            var type = (Class<? extends Event>) Class.forName("io.lumine.mythic.bukkit.events." + name, true, loader);
            var data = type.getMethod("getSkillMetadata");
            Bukkit.getPluginManager().registerEvent(type, this, EventPriority.HIGHEST, (listener, event) -> {
                if (event.isAsynchronous()) return;
                try {
                    var territory = Bukkit.getServicesManager().load(TerritoryService.class); if (territory == null) return;
                    var context = data.invoke(event);
                    var actor = (Entity) bukkit.invoke(casterEntity.invoke(caster.invoke(context)));
                    var source = active.invoke(api, actor);
                    if (source == null || !hostiles.contains(mobType.invoke(source))) return;
                    var original = (Collection<?>) targets.invoke(context); if (original == null || original.isEmpty()) return;
                    var allowed = new ArrayList<Object>(); boolean blocked = false;
                    for (var target : original) {
                        var entity = (Entity) bukkit.invoke(target);
                        if (entity instanceof Player player && (!territory.combat(player.getLocation()) || !territory.combat(actor.getLocation()))) blocked = true;
                        else allowed.add(target);
                    }
                    if (blocked) {
                        replace.invoke(context, allowed);
                        if (allowed.isEmpty() && event instanceof Cancellable cancellable) cancellable.setCancelled(true);
                    }
                } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot filter protected Mythic target", error); }
            }, plugin, true);
        }
        var hasTable = activeType.getMethod("hasThreatTable");
        var table = activeType.getMethod("getThreatTable");
        var all = table.getReturnType().getMethod("getAllThreatTargets");
        var forget = table.getReturnType().getMethod("observeChangeWorld", abstractEntity);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            var territory = Bukkit.getServicesManager().load(TerritoryService.class); if (territory == null) return;
            var seen = new HashSet<UUID>();
            for (var player : Bukkit.getOnlinePlayers()) for (var entity : player.getNearbyEntities(64, 32, 64)) {
                if (!(entity instanceof Mob) || !seen.add(entity.getUniqueId())) continue;
                try {
                    var mob = active.invoke(api, entity);
                    if (mob == null || !(boolean) hasTable.invoke(mob)) continue;
                    var threat = table.invoke(mob);
                    for (var target : new ArrayList<>((Collection<?>) all.invoke(threat))) {
                        var victim = (Entity) bukkit.invoke(target);
                        if (victim instanceof Player && (!territory.combat(victim.getLocation()) || !territory.combat(entity.getLocation())))
                            forget.invoke(threat, target);
                    }
                } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot clear protected Mythic threat", error); }
            }
        }, 1, 5);
    }
}
