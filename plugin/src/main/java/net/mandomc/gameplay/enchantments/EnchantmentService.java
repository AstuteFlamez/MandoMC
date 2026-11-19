package net.mandomc.gameplay.enchantments;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Enchantable;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.items.ItemCatalog;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.*;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;

/** Native enchantments own levels and combinations; this service owns server policy and presentation. */
public final class EnchantmentService implements Listener, AutoCloseable {
    public static final NamespacedKey ENDURANCE = new NamespacedKey("mandomc", "endurance");
    public static final NamespacedKey RECOVERY = new NamespacedKey("mandomc", "recovery");
    private static final String ROW = "mandomc:enchantment-book";
    private final MandoPlugin plugin;
    private final ItemCatalog items;
    private final YamlConfiguration config;
    private final Set<NamespacedKey> allowed;
    private final Set<NamespacedKey> removed;
    private final org.bukkit.scheduler.BukkitTask task;
    public EnchantmentService(MandoPlugin plugin, ItemCatalog items, YamlConfiguration config) {
        this.plugin = plugin; this.items = items; this.config = config;
        if (config.getInt("schema-version") != 1) throw new IllegalArgumentException("enchantments.yml: schema-version 1 required");
        allowed = keys(config.getStringList("table-pool")); removed = keys(config.getStringList("removed"));
        for (var key : allowed) if (Registry.ENCHANTMENT.get(key) == null)
            throw new IllegalStateException("Install the MandoMC enchantment datapack before startup: missing " + key);
        for (var enchantment : Registry.ENCHANTMENT) if (!config.isString("books." + enchantment.getKey() + ".description"))
            throw new IllegalArgumentException("enchantments.yml: missing description for " + enchantment.getKey());
        double endurance = config.getDouble("endurance-reduction-per-level", Double.NaN);
        double recovery = config.getDouble("recovery-bonus-per-level", Double.NaN);
        if (!Double.isFinite(endurance) || endurance < 0 || endurance > .3 || !Double.isFinite(recovery) || recovery < 0 || recovery > 4)
            throw new IllegalArgumentException("enchantments.yml: invalid charge tuning");
        items.presentation(this::decorate);
        new net.mandomc.commands.EnchantCommand(plugin, config);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(player -> {
            refresh(player.getInventory());
            var cursor = player.getItemOnCursor(); if (maintain(cursor)) player.setItemOnCursor(cursor);
        }), 1, 10);
    }
    private static Set<NamespacedKey> keys(List<String> values) {
        var result = new HashSet<NamespacedKey>();
        for (String value : values) {
            var key = NamespacedKey.fromString(value);
            if (key == null || !value.contains(":")) throw new IllegalArgumentException("Invalid enchantment " + value);
            result.add(key);
        }
        return Set.copyOf(result);
    }
    public boolean tableAllowed(Enchantment enchantment) { return allowed.contains(enchantment.getKey()); }
    public int level(ItemStack item, NamespacedKey key) {
        var enchantment = Registry.ENCHANTMENT.get(key);
        return item == null || enchantment == null ? 0 : Math.clamp(item.getEnchantmentLevel(enchantment), 0, 255);
    }
    public double consumption(ItemStack item) { return Math.max(.1, 1 - level(item, ENDURANCE) * config.getDouble("endurance-reduction-per-level")); }
    public double regeneration(ItemStack item) { return 1 + level(item, RECOVERY) * config.getDouble("recovery-bonus-per-level"); }
    public void decorate(ItemStack item) {
        if (item == null || item.isEmpty()) return;
        if (item.getType() != Material.ENCHANTED_BOOK) { EnchantmentLore.update(item); return; }
        if (!(item.getItemMeta() instanceof EnchantmentStorageMeta meta)) return;
        var enchants = meta.getStoredEnchants(); if (enchants.isEmpty()) return;
        var entries = enchants.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().getKey().toString())).toList();
        String rarity = entries.stream().map(e -> config.getString("books." + e.getKey().getKey() + ".rarity", "rare"))
                .max(Comparator.comparingInt(r -> List.of("common", "uncommon", "rare", "epic", "legendary", "mythic").indexOf(r))).orElse("rare");
        var lore = new ArrayList<Component>();
        lore.add(items.badges(rarity, "enchantment").insertion(ROW)); lore.add(Component.empty().insertion(ROW));
        String id = meta.getPersistentDataContainer().get(ItemCatalog.ITEM_ID, PersistentDataType.STRING);
        var previousLore = meta.lore();
        if (previousLore != null && (id == null || previousLore.stream().anyMatch(line -> ROW.equals(line.insertion())))) {
            for (var line : previousLore) if (!ROW.equals(line.insertion())) lore.add(line);
        }
        if (!lore.isEmpty() && !net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(lore.getLast()).isBlank())
            lore.add(Component.empty().insertion(ROW));
        if (entries.size() > 1) {
            for (int index = 0; index < entries.size(); index++) {
                if (index > 0) lore.add(Component.empty().insertion(ROW));
                var entry = entries.get(index);
                lore.add(entry.getKey().displayName(entry.getValue()).decoration(TextDecoration.ITALIC, false).insertion(ROW));
                description(lore, entry.getKey());
            }
        } else description(lore, entries.getFirst().getKey());
        Component previous = meta.displayName();
        String generated = meta.getPersistentDataContainer().get(new NamespacedKey("mandomc", "book_name"), PersistentDataType.STRING);
        var plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        if (previous == null || generated != null && generated.equals(plain.serialize(previous)) || id != null && generated == null) {
            Component name = entries.size() == 1 ? entries.getFirst().getKey().displayName(entries.getFirst().getValue()) : Component.text("Enchanted Book");
            meta.displayName(name.color(NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            meta.getPersistentDataContainer().set(new NamespacedKey("mandomc", "book_name"), PersistentDataType.STRING, plain.serialize(name));
        }
        meta.lore(lore); meta.addItemFlags(ItemFlag.HIDE_STORED_ENCHANTS); item.setItemMeta(meta);
    }
    private void description(List<Component> lore, Enchantment enchantment) {
        String description = config.getString("books." + enchantment.getKey() + ".description", "Apply this enchantment to compatible equipment.");
        // Keep descriptions compact in the native tooltip without exposing charge tuning.
        var line = new StringBuilder();
        for (String word : description.split(" ")) {
            if (line.length() + word.length() > 44) { lore.add(gray(line.toString())); line.setLength(0); }
            if (!line.isEmpty()) line.append(' '); line.append(word);
        }
        if (!line.isEmpty()) lore.add(gray(line.toString()));
    }
    private static Component gray(String text) { return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false).insertion(ROW); }
    public boolean maintain(ItemStack item) {
        if (item == null || item.isEmpty()) return false;
        var before = item.clone(); var meta = item.getItemMeta();
        if ("saber_forge".equals(meta.getPersistentDataContainer().get(ItemCatalog.ITEM_ID, PersistentDataType.STRING))
                && item.getType() == Material.SMITHING_TABLE) {
            var replacement = items.create("saber_workbench").getItemMeta();
            meta.getPersistentDataContainer().set(ItemCatalog.ITEM_ID, PersistentDataType.STRING, "saber_workbench");
            meta.displayName(replacement.displayName()); meta.lore(replacement.lore());
        }
        for (var enchant : List.copyOf(meta.getEnchants().keySet())) if (removed.contains(enchant.getKey())) meta.removeEnchant(enchant);
        if (meta instanceof EnchantmentStorageMeta stored) {
            boolean stripped = false;
            for (var enchant : List.copyOf(stored.getStoredEnchants().keySet())) if (removed.contains(enchant.getKey())) { stored.removeStoredEnchant(enchant); stripped = true; }
            item.setItemMeta(stored);
            if (stripped && stored.getStoredEnchants().isEmpty()) {
                item.setType(Material.BOOK); clearBookPresentation(item);
            }
        } else item.setItemMeta(meta);
        if (plugin.sabers().isSaber(item)) migrateSaber(item);
        if (item.getType() == Material.BOOK && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey("mandomc", "book_name"))) clearBookPresentation(item);
        decorate(item); return !before.equals(item);
    }
    private void clearBookPresentation(ItemStack item) {
        var meta = item.getItemMeta(); var lore = new ArrayList<>(meta.lore() == null ? List.<Component>of() : meta.lore());
        lore.removeIf(line -> ROW.equals(line.insertion())); meta.lore(lore.isEmpty() ? null : lore);
        meta.displayName(null); meta.removeItemFlags(ItemFlag.HIDE_STORED_ENCHANTS);
        meta.setItemModel(null); meta.setCustomModelData(null);
        meta.getPersistentDataContainer().remove(new NamespacedKey("mandomc", "book_name"));
        for (String id : List.of("endurance_book", "recovery_book", "mending_book")) if (id.equals(meta.getPersistentDataContainer().get(ItemCatalog.ITEM_ID, PersistentDataType.STRING)))
            meta.getPersistentDataContainer().remove(ItemCatalog.ITEM_ID);
        item.setItemMeta(meta);
    }
    private void migrateSaber(ItemStack item) {
        var meta = item.getItemMeta();
        for (var attribute : List.of(Attribute.ATTACK_DAMAGE, Attribute.ATTACK_SPEED)) {
            var mods = meta.getAttributeModifiers(attribute);
            if (mods != null) for (var modifier : List.copyOf(mods)) if (modifier.getKey().getNamespace().equals("mandomc")
                    && Set.of("item_damage", "item_speed", "forge_damage", "forge_speed").contains(modifier.getKey().getKey())) meta.removeAttributeModifier(attribute, modifier);
            meta.addAttributeModifier(attribute, new AttributeModifier(new NamespacedKey("mandomc", attribute == Attribute.ATTACK_DAMAGE ? "item_damage" : "item_speed"),
                    attribute == Attribute.ATTACK_DAMAGE ? 7 : -2.4, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }
        int damage = legacy(meta, "damage"), guard = legacy(meta, "guard"), recovery = Math.max(legacy(meta, "throw"), legacy(meta, "speed"));
        addLegacy(meta, Enchantment.SHARPNESS, damage); addLegacy(meta, Registry.ENCHANTMENT.get(ENDURANCE), guard); addLegacy(meta, Registry.ENCHANTMENT.get(RECOVERY), recovery);
        for (String track : List.of("damage", "guard", "throw", "speed")) meta.getPersistentDataContainer().remove(new NamespacedKey("mandomc", "forge_" + track));
        if (meta.lore() != null) { var lore = new ArrayList<>(meta.lore()); lore.removeIf(c -> "mandomc:forge-stats".equals(c.insertion())); meta.lore(lore); }
        item.setItemMeta(meta); item.setData(DataComponentTypes.ENCHANTABLE, Enchantable.enchantable(15));
    }
    private static int legacy(org.bukkit.inventory.meta.ItemMeta meta, String track) { return Math.max(0, meta.getPersistentDataContainer().getOrDefault(new NamespacedKey("mandomc", "forge_" + track), PersistentDataType.INTEGER, 0)); }
    private static void addLegacy(org.bukkit.inventory.meta.ItemMeta meta, Enchantment enchantment, int level) {
        if (enchantment != null && level > meta.getEnchantLevel(enchantment)) meta.addEnchant(enchantment, Math.min(level, enchantment.getMaxLevel()), true);
    }
    private void refresh(Inventory inventory) {
        for (int slot = 0; slot < inventory.getSize(); slot++) { var item = inventory.getItem(slot); if (maintain(item)) inventory.setItem(slot, item); }
    }
    private boolean electrum(ItemStack fuel) {
        return "electrum_dust".equals(items.identify(fuel));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void prepareEnchant(org.bukkit.event.enchantment.PrepareItemEnchantEvent event) {
        if (event.getEnchanter().getGameMode() == GameMode.CREATIVE) return;
        var fuel = ((EnchantingInventory) event.getInventory()).getSecondary();
        if (fuel != null && !fuel.isEmpty() && !electrum(fuel)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void enchant(EnchantItemEvent event) {
        var fuel = ((EnchantingInventory) event.getInventory()).getSecondary();
        if (event.getEnchanter().getGameMode() != GameMode.CREATIVE && !electrum(fuel)) {
            event.setCancelled(true);
            return;
        }
        event.getEnchantsToAdd().keySet().removeIf(enchant -> !tableAllowed(enchant));
        if (event.getEnchantsToAdd().isEmpty()) event.setCancelled(true);
        else Bukkit.getScheduler().runTask(plugin, () -> { refresh(event.getInventory()); refresh(event.getEnchanter().getInventory()); });
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void open(InventoryOpenEvent event) { refresh(event.getInventory()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true) public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) return;
        Bukkit.getScheduler().runTask(plugin, () -> { refresh(player.getInventory()); var item = player.getItemOnCursor(); if (maintain(item)) player.setItemOnCursor(item); });
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void spawn(ItemSpawnEvent event) {
        var item = event.getEntity().getItemStack(); if (maintain(item)) event.getEntity().setItemStack(item);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void loot(LootGenerateEvent event) { event.getLoot().forEach(this::maintain); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void fish(PlayerFishEvent event) {
        if (event.getCaught() instanceof org.bukkit.entity.Item caught) { var item = caught.getItemStack(); if (maintain(item)) caught.setItemStack(item); }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true) public void trade(VillagerAcquireTradeEvent event) {
        var item = event.getRecipe().getResult();
        if (item.getItemMeta() instanceof EnchantmentStorageMeta meta && meta.getStoredEnchants().keySet().stream().anyMatch(e -> removed.contains(e.getKey()))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void grindstone(PrepareGrindstoneEvent event) { var result = event.getResult(); if (maintain(result)) event.setResult(result); }
    @EventHandler(priority = EventPriority.HIGHEST) public void anvil(PrepareAnvilEvent event) { var result = event.getResult(); if (maintain(result)) event.setResult(result); }
    @Override public void close() { task.cancel(); items.presentation(item -> { }); HandlerList.unregisterAll(this); }
}
