package net.mandomc.gameplay.items;

import java.util.List;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;

/** Immutable content description; runtime items are built only after all files validate. */
public record ItemDefinition(String id, Material material, String name, String rarity, String category,
        List<String> lore, int customModelData, NamespacedKey itemModel, NamespacedKey equipmentModel) {
    private static final Set<String> KEYS = Set.of("material", "name", "rarity", "category", "lore",
            "custom-model-data", "item-model", "equipment-model");

    public ItemDefinition {
        lore = List.copyOf(lore);
    }

    public static ItemDefinition parse(String id, ConfigurationSection section, String source) {
        String path = source + ": items." + id;
        if (!id.matches("[a-z0-9_]+")) throw new IllegalArgumentException(path + ": invalid item ID");
        if (section == null) throw new IllegalArgumentException(path + ": expected an item section");
        for (String key : section.getKeys(false)) {
            if (!KEYS.contains(key)) throw new IllegalArgumentException(path + ": unsupported key " + key);
        }
        Material material;
        try {
            material = Material.valueOf(required(section, "material", path));
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException(path + ".material: invalid material", error);
        }
        if (Set.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR).contains(material)) throw new IllegalArgumentException(path + ".material: air is not an item");
        if (!section.isList("lore") || section.getList("lore").stream().anyMatch(line -> !(line instanceof String))) {
            throw new IllegalArgumentException(path + ".lore: expected a list of strings");
        }
        int modelData = section.getInt("custom-model-data", -1);
        if (!section.isInt("custom-model-data") || modelData < 0) {
            throw new IllegalArgumentException(path + ".custom-model-data: expected a nonnegative integer");
        }
        var equipment = section.contains("equipment-model") ? key(section, "equipment-model", path) : null;
        return new ItemDefinition(id, material, required(section, "name", path),
                required(section, "rarity", path), required(section, "category", path),
                section.getStringList("lore"), modelData, key(section, "item-model", path), equipment);
    }

    private static String required(ConfigurationSection section, String key, String path) {
        if (!section.isString(key) || section.getString(key).isBlank()) {
            throw new IllegalArgumentException(path + "." + key + ": expected a nonempty string");
        }
        return section.getString(key);
    }

    private static NamespacedKey key(ConfigurationSection section, String key, String path) {
        String value = required(section, key, path);
        var parsed = NamespacedKey.fromString(value);
        if (!value.contains(":") || parsed == null) {
            throw new IllegalArgumentException(path + "." + key + ": expected namespace:path");
        }
        return parsed;
    }
}
