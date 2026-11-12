package net.mandomc.gameplay.items;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Main-thread catalog. Reload builds a complete candidate snapshot before replacing the active one. */
public final class ItemCatalog {
    public static final NamespacedKey ITEM_ID = new NamespacedKey("mandomc", "item_id");
    private final Path directory;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private record Entry(String category, java.util.function.Supplier<ItemStack> factory) { }
    private Map<String, Entry> entries = Map.of();
    private final java.util.function.BiFunction<String, String, ItemStack> externalFactory;

    public ItemCatalog(Path directory) {
        this(directory, (kind, id) -> { throw new IllegalStateException("External items unavailable"); });
    }

    public ItemCatalog(Path directory, java.util.function.BiFunction<String, String, ItemStack> externalFactory) {
        this.directory = directory;
        this.externalFactory = externalFactory;
    }

    public void reload() throws IOException, InvalidConfigurationException {
        var presentation = read(directory.resolve("items/catalog.yml"));
        var definitions = new LinkedHashMap<String, ItemDefinition>();
        try (var files = Files.list(directory.resolve("items/definitions"))) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".yml")).sorted().toList()) {
                var config = read(file);
                var section = config.getConfigurationSection("items");
                if (section == null) throw new IllegalArgumentException(file.getFileName() + ": missing items section");
                for (String id : section.getKeys(false)) {
                    var definition = ItemDefinition.parse(id, section.getConfigurationSection(id), file.getFileName().toString());
                    if (definitions.putIfAbsent(id, definition) != null) {
                        throw new IllegalArgumentException(file.getFileName() + ": duplicate item ID " + id);
                    }
                }
            }
        }
        if (definitions.isEmpty()) throw new IllegalArgumentException("items/: no item definitions found");
        var candidate = new LinkedHashMap<String, Entry>();
        for (var definition : definitions.values()) {
            var template = build(definition, presentation);
            candidate.put(definition.id(), new Entry(definition.category(), template::clone));
        }
        var external = presentation.getConfigurationSection("external-items");
        if (external != null) {
            for (String id : external.getKeys(false)) {
                var section = external.getConfigurationSection(id);
                if (!id.matches("[a-z0-9_]+") || section == null) {
                    throw new IllegalArgumentException("items/catalog.yml: invalid external item " + id);
                }
                String kind = section.getString("kind", "");
                String vendorId = section.getString("vendor-id", "");
                String category = section.getString("category", "");
                if (!Set.of("weapon", "ammo").contains(kind) || !vendorId.matches("[A-Za-z0-9_]+")
                        || !category.matches("[a-z0-9_]+")) {
                    throw new IllegalArgumentException("items/catalog.yml: invalid external item fields for " + id);
                }
                if (candidate.putIfAbsent(id, new Entry(category, () -> externalFactory.apply(kind, vendorId))) != null) {
                    throw new IllegalArgumentException("items/catalog.yml: duplicate item ID " + id);
                }
            }
        }
        entries = Collections.unmodifiableMap(candidate);
    }

    /** Returns an independent item. Callers never receive a cached mutable template. */
    public ItemStack create(String id) {
        var entry = entries.get(id);
        return entry == null ? null : entry.factory().get().clone();
    }

    public Set<String> ids() {
        return entries.keySet();
    }

    public java.util.List<String> categories() {
        return entries.values().stream().map(Entry::category).distinct().sorted().toList();
    }

    public java.util.List<String> ids(String category) {
        return entries.entrySet().stream().filter(entry -> category == null || entry.getValue().category().equals(category))
                .map(Map.Entry::getKey).sorted().toList();
    }

    public String identify(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(ITEM_ID, PersistentDataType.STRING);
        if (id == null || !entries.containsKey(id)) return null;
        return create(id).getType() == item.getType() ? id : null;
    }

    private ItemStack build(ItemDefinition definition, YamlConfiguration presentation) {
        if (!definition.material().isItem()) throw new IllegalArgumentException(definition.id() + ": material is not an item");
        var item = new ItemStack(definition.material());
        var meta = item.getItemMeta();
        meta.displayName(text(definition.name()));
        String rarity = badge(presentation, "rarities", definition.rarity());
        String category = badge(presentation, "categories", definition.category());
        var lore = new ArrayList<Component>();
        lore.add(text("<white>" + rarity + category));
        lore.add(Component.empty());
        definition.lore().stream().map(this::text).forEach(lore::add);
        meta.lore(lore);
        var modelData = meta.getCustomModelDataComponent();
        modelData.setFloats(java.util.List.of((float) definition.customModelData()));
        meta.setCustomModelDataComponent(modelData);
        meta.setItemModel(definition.itemModel());
        meta.getPersistentDataContainer().set(ITEM_ID, PersistentDataType.STRING, definition.id());
        if (definition.attackDamage() != null) meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_DAMAGE,
                new org.bukkit.attribute.AttributeModifier(new NamespacedKey("mandomc", "item_damage"), definition.attackDamage() - 1,
                        org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
        if (definition.attackSpeed() != null) meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_SPEED,
                new org.bukkit.attribute.AttributeModifier(new NamespacedKey("mandomc", "item_speed"), definition.attackSpeed() - 4,
                        org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
        item.setItemMeta(meta);
        if (definition.equipmentModel() != null) {
            EquipmentSlot slot = definition.material().getEquipmentSlot();
            if (!Set.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET).contains(slot)) {
                throw new IllegalArgumentException(definition.id() + ": equipment-model requires wearable armor");
            }
            var equipment = item.getData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE);
            if (equipment == null) throw new IllegalArgumentException(definition.id() + ": no native equipment component");
            item.setData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE,
                    equipment.toBuilder().assetId(definition.equipmentModel()));
        }
        if (definition.foodNutrition() != null) {
            var food = item.getData(io.papermc.paper.datacomponent.DataComponentTypes.FOOD);
            if (food == null) throw new IllegalArgumentException(definition.id() + ": native food component missing");
            item.setData(io.papermc.paper.datacomponent.DataComponentTypes.FOOD,
                    food.toBuilder().nutrition(definition.foodNutrition()));
        }
        return item;
    }

    private Component text(String value) {
        return mini.deserialize(value).decoration(TextDecoration.ITALIC, false);
    }

    private static String badge(YamlConfiguration config, String group, String name) {
        String key = group + "." + name;
        if (!config.isString(key) || config.getString(key).isBlank()) {
            throw new IllegalArgumentException("items/catalog.yml: unknown or empty badge " + key);
        }
        return config.getString(key);
    }

    private static YamlConfiguration read(Path path) throws IOException, InvalidConfigurationException {
        var config = new YamlConfiguration();
        config.load(path.toFile());
        if (config.getInt("schema-version", -1) != 1) {
            throw new IllegalArgumentException(path.getFileName() + ": expected schema-version 1");
        }
        return config;
    }
}
