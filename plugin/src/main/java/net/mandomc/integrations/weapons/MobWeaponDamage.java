package net.mandomc.integrations.weapons;

import net.mandomc.gameplay.mobs.SandySettings;
import net.mandomc.integrations.mobs.MythicMobAccess;
import net.mandomc.integrations.regions.MobProtection;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Scales the NPC's shot without changing the cycler item or the player's weapon damage. */
public final class MobWeaponDamage implements Listener {
    @SuppressWarnings("unchecked")
    public MobWeaponDamage(JavaPlugin plugin, MythicMobAccess mobs, SandySettings settings) throws ReflectiveOperationException {
        var vendor = Bukkit.getPluginManager().getPlugin("WeaponMechanics");
        if (vendor == null || !vendor.isEnabled()) throw new IllegalStateException("Sandy mobs require WeaponMechanics");
        var eventType = (Class<? extends Event>) Class.forName(
                "me.deecaad.weaponmechanics.weapon.weaponevents.WeaponDamageEntityEvent", true,
                vendor.getClass().getClassLoader());
        var shooterMethod = eventType.getMethod("getShooter");
        var victimMethod = eventType.getMethod("getVictim");
        var titleMethod = eventType.getMethod("getWeaponTitle");
        var damageMethod = eventType.getMethod("setBaseDamage", double.class);
        var protection = new MobProtection();
        Bukkit.getPluginManager().registerEvent(eventType, this, EventPriority.HIGH, (listener, event) -> {
            try {
                var shooter = (LivingEntity) shooterMethod.invoke(event);
                if (shooter == null || !mobs.managed(shooter)) return;
                var victim = (LivingEntity) victimMethod.invoke(event);
                if (!protection.allowed(shooter.getLocation()) || !protection.allowed(victim.getLocation())) {
                    ((Cancellable) event).setCancelled(true);
                    return;
                }
                if ("TuskenRaiderSniper".equals(mobs.type(shooter)) && "CyclerRifle".equals(titleMethod.invoke(event))) {
                    damageMethod.invoke(event, settings.rifleDamage(mobs.level(shooter)));
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                ((Cancellable) event).setCancelled(true);
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Mob weapon integration failed; hit denied", error);
            }
        }, plugin, true);
    }
}
