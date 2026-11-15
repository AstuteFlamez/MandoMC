package net.mandomc.integrations.weapons;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/** Optional bridge to the pinned vendor's public API. No vendor implementation or weapon state is copied. */
public final class WeaponMechanicsItems {
    private Method weapon;
    private Method ammo;

    /** Reload callbacks may mutate inventory; do not swap inventories while one is running. */
    public static boolean reloading(org.bukkit.entity.LivingEntity player) {
        var plugin = Bukkit.getPluginManager().getPlugin("WeaponMechanics");
        if (plugin == null || !plugin.isEnabled()) return false;
        try {
            var api = Class.forName("me.deecaad.weaponmechanics.WeaponMechanicsAPI", true, plugin.getClass().getClassLoader());
            return (boolean) api.getMethod("isReloading", org.bukkit.entity.LivingEntity.class).invoke(null, player);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot check active weapon reload", error); }
    }
    /** Cancel both vendor hand tasks before restoring an unrelated inventory. */
    public static void stopActions(org.bukkit.entity.LivingEntity player) {
        var plugin = Bukkit.getPluginManager().getPlugin("WeaponMechanics");
        if (plugin == null || !plugin.isEnabled()) return;
        try {
            var wrapper = plugin.getClass().getMethod("getEntityWrapper", org.bukkit.entity.LivingEntity.class).invoke(plugin, player);
            if (wrapper == null) return;
            for (String hand : java.util.List.of("getMainHandData", "getOffHandData")) {
                var data = wrapper.getClass().getMethod(hand).invoke(wrapper);
                data.getClass().getMethod("cancelTasks").invoke(data);
            }
        } catch (ReflectiveOperationException error) { throw new IllegalStateException("Cannot stop tutorial weapon tasks", error); }
    }
    public ItemStack generate(String kind, String id) {
        var plugin = Bukkit.getPluginManager().getPlugin("WeaponMechanics");
        if (plugin == null || !plugin.isEnabled()) throw new IllegalStateException("WeaponMechanics is unavailable");
        try {
            if (weapon == null) {
                var api = Class.forName("me.deecaad.weaponmechanics.WeaponMechanicsAPI", true,
                        plugin.getClass().getClassLoader());
                weapon = api.getMethod("generateWeapon", String.class);
                ammo = api.getMethod("generateAmmo", String.class, boolean.class);
            }
            var result = (ItemStack) (kind.equals("weapon") ? weapon.invoke(null, id) : ammo.invoke(null, id, false));
            if (result == null || result.getType().isAir()) throw new IllegalStateException("Unknown vendor item " + id);
            return result;
        } catch (ReflectiveOperationException error) {
            Throwable cause = error instanceof InvocationTargetException invocation ? invocation.getCause() : error;
            throw new IllegalStateException("Cannot generate WeaponMechanics " + kind + " " + id, cause);
        }
    }
}
