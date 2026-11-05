package net.mandomc.gameplay.lightsaber;

import net.mandomc.server.items.ItemUtils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Shared lightsaber item predicates.
 */
public final class SaberItemUtil {

    private SaberItemUtil() {}

    public static boolean isSaberShield(ItemStack item) {
        return item != null
                && item.getType() == Material.SHIELD
                && ItemUtils.hasTag(item, "SABER");
    }
}
