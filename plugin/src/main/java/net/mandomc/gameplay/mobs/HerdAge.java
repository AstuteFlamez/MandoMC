package net.mandomc.gameplay.mobs;

import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;

/** Native entity age is the sole age authority, including save/load and normal tick growth. */
public final class HerdAge {
    // Species fallback after Mythic unregisters a dead instance; age remains native, never duplicated.
    public static final org.bukkit.NamespacedKey KIND = new org.bukkit.NamespacedKey("mandomc", "herd_kind");
    private HerdAge() {}

    public static boolean baby(Entity entity) {
        return entity instanceof Ageable ageable && !ageable.isAdult();
    }

    public static boolean herd(String type) {
        return "Bantha".equals(type) || "Dewback".equals(type);
    }

    public static double scale(Entity entity) {
        return baby(entity) ? 0.5 : 1.0;
    }
}
