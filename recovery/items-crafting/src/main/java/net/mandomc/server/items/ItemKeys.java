package net.mandomc.server.items;

import org.bukkit.NamespacedKey;

import net.mandomc.MandoMC;

/**
 * Central registry of {@link NamespacedKey}s used for item metadata.
 *
 * These keys are stored in {@link org.bukkit.persistence.PersistentDataContainer}
 * to identify custom items and their properties.
 */
public final class ItemKeys {

    /**
     * Persistent key storing the unique item id.
     */
    public static final NamespacedKey ITEM_ID =
            new NamespacedKey(MandoMC.getInstance(), "item_id");

    /**
     * Persistent key storing item tags (comma-separated).
     */
    public static final NamespacedKey ITEM_TAGS =
            new NamespacedKey(MandoMC.getInstance(), "item_tags");

    /**
     * Prevent instantiation.
     */
    private ItemKeys() {
        throw new UnsupportedOperationException("Utility class");
    }
}