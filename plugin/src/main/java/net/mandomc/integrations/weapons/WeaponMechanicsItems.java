package net.mandomc.integrations.weapons;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

/** Optional bridge to the pinned vendor's public API. No vendor implementation or weapon state is copied. */
public final class WeaponMechanicsItems {
    private Method weapon;
    private Method ammo;

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
