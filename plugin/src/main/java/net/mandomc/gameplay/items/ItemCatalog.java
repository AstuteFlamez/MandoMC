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
    private record Entry(String category, String rarity, java.util.function.Supplier<ItemStack> factory) { }
    private Map<String, Entry> entries = Map.of();
    private Set<String> externalIds = Set.of();
    private Map<String, Set<String>> badgeTexts = Map.of();
    private java.util.function.Consumer<ItemStack> presentation = item -> { };
    private YamlConfiguration badgeConfig;
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
            candidate.put(definition.id(), new Entry(definition.category(), definition.rarity(), template::clone));
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
                if (candidate.putIfAbsent(id, new Entry(category, "common", () -> externalFactory.apply(kind, vendorId))) != null) {
                    throw new IllegalArgumentException("items/catalog.yml: duplicate item ID " + id);
                }
            }
        }
        var badges = new java.util.HashMap<String, Set<String>>();
        for (String category : presentation.getConfigurationSection("categories").getKeys(false)) {
            var texts = new java.util.HashSet<String>();
            for (String rarity : presentation.getConfigurationSection("rarities").getKeys(false))
                texts.add(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(text("<white>" + badge(presentation, "rarities", rarity) + badge(presentation, "categories", category))));
            badges.put(category, Set.copyOf(texts));
        }
        badgeTexts = Map.copyOf(badges);
        badgeConfig = presentation;
        entries = Collections.unmodifiableMap(candidate);
        externalIds = external == null ? Set.of() : Set.copyOf(external.getKeys(false));
    }

    public boolean external(String id) { return externalIds.contains(id); }

    public boolean material(ItemStack item) {
        var id = identify(item); var entry = id == null ? null : entries.get(id);
        return entry != null && Set.of("materials", "metals", "components", "crystals").contains(entry.category());
    }

    public boolean refreshMaterialBadge(ItemStack item) {
        if (!material(item)) return false;
        String id = identify(item); var meta = item.getItemMeta(); var lore = meta.lore();
        if (lore == null || lore.isEmpty()) return false;
        String category = entries.get(id).category();
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        if (!badgeTexts.getOrDefault(category, Set.of()).contains(plain.serialize(lore.getFirst()))) return false;
        var badge = create(id).getItemMeta().lore().getFirst();
        if (lore.getFirst().equals(badge)) return false;
        var updated = new ArrayList<>(lore); updated.set(0, badge); meta.lore(updated); item.setItemMeta(meta); return true;
    }

    /** Returns an independent item. Callers never receive a cached mutable template. */
    public ItemStack create(String id) {
        var entry = entries.get(switch (id == null ? "" : id) {
            case "saber_forge" -> "saber_workbench";
            case "electrum_scrap" -> "electrum_dust";
            default -> MetalMigration.ingotId(id);
        });
        if (entry == null) return null;
        var item = entry.factory().get().clone();
        presentation.accept(item);
        return item;
    }

    public void presentation(java.util.function.Consumer<ItemStack> update) { presentation = update; }
    public Component badges(String rarity, String category) {
        return text("<white>" + badge(badgeConfig, "rarities", rarity) + badge(badgeConfig, "categories", category));
    }

    public Map<String, Component> catalogBadges() {
        var result = new LinkedHashMap<String, Component>();
        entries.forEach((id, entry) -> result.put(id, badges(entry.rarity(), entry.category())));
        return Map.copyOf(result);
    }

    public Set<String> badgeRows() {
        return badgeTexts.values().stream().flatMap(Set::stream)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
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
        if ("saber_forge".equals(id)) id = "saber_workbench";
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
        if (!definition.storedEnchantments().isEmpty()) {
            var book = (org.bukkit.inventory.meta.EnchantmentStorageMeta) meta;
            definition.storedEnchantments().forEach((key, level) -> {
                var enchantment = org.bukkit.Registry.ENCHANTMENT.get(NamespacedKey.fromString(key));
                if (enchantment == null || level > enchantment.getMaxLevel())
                    throw new IllegalArgumentException(definition.id() + ": unknown enchantment or level " + key);
                book.addStoredEnchant(enchantment, level, false);
            });
        }
        if (definition.attackDamage() != null) meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_DAMAGE,
                new org.bukkit.attribute.AttributeModifier(new NamespacedKey("mandomc", "item_damage"), definition.attackDamage() - 1,
                        org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
        if (definition.attackSpeed() != null) meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_SPEED,
                new org.bukkit.attribute.AttributeModifier(new NamespacedKey("mandomc", "item_speed"), definition.attackSpeed() - 4,
                        org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
        item.setItemMeta(meta);
        if (definition.material() == org.bukkit.Material.SHIELD && definition.attackDamage() != null)
            item.setData(io.papermc.paper.datacomponent.DataComponentTypes.ENCHANTABLE,
                    io.papermc.paper.datacomponent.item.Enchantable.enchantable(15));
        if (definition.equipmentModel() != null || definition.headModel()) {
            EquipmentSlot slot = definition.material().getEquipmentSlot();
            if (!Set.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET).contains(slot)) {
                throw new IllegalArgumentException(definition.id() + ": equipment-model requires wearable armor");
            }
            var equipment = item.getData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE);
            if (equipment == null) throw new IllegalArgumentException(definition.id() + ": no native equipment component");
            item.setData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE,
                    equipment.toBuilder().assetId(definition.headModel() ? null : definition.equipmentModel()));
        }
        if (definition.foodNutrition() != null) {
            var food = item.getData(io.papermc.paper.datacomponent.DataComponentTypes.FOOD);
            if (food == null) throw new IllegalArgumentException(definition.id() + ": native food component missing");
            item.setData(io.papermc.paper.datacomponent.DataComponentTypes.FOOD,
                    food.toBuilder().nutrition(definition.foodNutrition()));
        }
        if(net.mandomc.gameplay.spawners.SpawnerItems.known(definition.id()))
            net.mandomc.gameplay.spawners.SpawnerItems.hideDefaultCreature(item);
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
