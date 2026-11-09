package net.mandomc.verification;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.commands.ItemCommand;
import net.mandomc.gameplay.items.ItemBrowser;
import net.mandomc.gameplay.items.ItemCatalog;
import net.mandomc.integrations.weapons.WeaponMechanicsItems;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;

/** Live Paper APIs with isolated inventories and temporary config files; no user items are modified. */
public final class ItemsProbe extends JavaPlugin {
    private int checks;
    private ItemBrowser browser;
    private ItemCatalog catalog;
    private ItemCommand delivery;
    private TestPlayer viewer;
    private TestPlayer target;
    private Path fixture;

    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> step(this::verifyItems), 60L);
    }

    private void verifyItems() throws Exception {
        var plugin = (MandoPlugin) Bukkit.getPluginManager().getPlugin("MandoMC");
        check(plugin.items().ids().size() == 6, "six shipped custom items, no vendor samples");
        check(Bukkit.getPluginCommand("mmc") == Bukkit.getPluginCommand("mandomc"), "mmc alias targets the same root");
        check(!Bukkit.getPluginManager().getPermission("mandomc.items.give").getDefault().getValue(false),
                "give permission is not granted to non-ops");
        var field = MandoPlugin.class.getDeclaredField("itemCommands");
        field.setAccessible(true);
        delivery = (ItemCommand) field.get(plugin);
        for (String part : List.of("helmet", "chestplate", "leggings", "boots")) {
            String id = "robes_" + part;
            var item = plugin.items().create(id);
            var vanilla = new ItemStack(item.getType());
            var meta = item.getItemMeta();
            check(plugin.items().identify(item).equals(id), "persistent identity " + id);
            check(meta.getItemModel().equals(NamespacedKey.fromString("mandomc:armor/" + id)), "inventory model " + id);
            check(meta.getEquippable().getModel().equals(NamespacedKey.fromString("mandomc:desert_robes")), "worn model " + id);
            check(meta.getEquippable().getSlot() == vanilla.getType().getEquipmentSlot(), "native armor slot " + id);
            check(item.getData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE).equipSound().equals(
                    vanilla.getData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE).equipSound()), "native equip sound " + id);
            check(meta.getEquippable().isDamageOnHurt() && meta.getEquippable().isSwappable(), "native wear and equip behavior " + id);
            var plain = PlainTextComponentSerializer.plainText();
            check(plain.serialize(meta.lore().get(0)).equals("Ėĉ") && plain.serialize(meta.lore().get(1)).isEmpty(),
                    "uncommon/armor badges followed by blank line " + id);
            check(item.getMaxStackSize() == 1 && !meta.isUnbreakable(), "native stack size and durability " + id);
            var edited = (LeatherArmorMeta) meta;
            edited.setColor(Color.BLUE);
            ((org.bukkit.inventory.meta.Damageable) edited).setDamage(3);
            item.setItemMeta(edited);
            item.addEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION, 1);
            var restored = ItemStack.deserializeBytes(item.serializeAsBytes());
            check(plugin.items().identify(restored).equals(id) && restored.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.PROTECTION) == 1,
                    "identity and enchantment persist through item serialization " + id);
            check(((LeatherArmorMeta) restored.getItemMeta()).getColor().equals(Color.BLUE), "dye preserved " + id);
            check(((org.bukkit.inventory.meta.Damageable) plugin.items().create(id).getItemMeta()).getDamage() == 0, "template remains pristine " + id);
        }
        check(plugin.items().identify(new ItemStack(Material.LEATHER_HELMET)) == null, "vanilla leather is not a custom robe");
        check(plugin.items().create("e11").getType() == Material.DIAMOND_HOE, "E11 generated through vendor API");
        check(plugin.items().create("standard_plasma_cells").getMaxStackSize() == 64, "vendor ammo max stack 64");

        fixture = Files.createTempDirectory("mandomc-items-probe-");
        Files.createDirectory(fixture.resolve("items"));
        Files.copy(plugin.getDataFolder().toPath().resolve("items/catalog.yml"), fixture.resolve("items/catalog.yml"));
        String original = Files.readString(plugin.getDataFolder().toPath().resolve("items/definitions/robes.yml"));
        Files.writeString(fixture.resolve("items/definitions/robes.yml"), original);
        catalog = new ItemCatalog(fixture, new WeaponMechanicsItems()::generate);
        catalog.reload();
        Files.writeString(fixture.resolve("items/definitions/robes.yml"), original.replace("LEATHER_HELMET", "INVALID"));
        expectRejected(catalog::reload, "invalid material rejects reload");
        check(catalog.create("robes_helmet") != null, "failed reload keeps working catalog");
        Files.writeString(fixture.resolve("items/definitions/robes.yml"), original);
        Files.writeString(fixture.resolve("items/duplicate.yml"), original);
        expectRejected(catalog::reload, "duplicate IDs reject reload");
        Files.delete(fixture.resolve("items/duplicate.yml"));
        var extra = new StringBuilder("schema-version: 1\nitems:\n");
        for (int i = 0; i < 47; i++) {
            extra.append("  test_").append(String.format("%02d", i)).append(":\n")
                    .append("    material: PAPER\n    name: '<yellow>Test item'\n    rarity: uncommon\n    category: test\n")
                    .append("    lore: ['<gray>Pagination fixture']\n    custom-model-data: 1\n    item-model: mandomc:armor/robes_helmet\n");
        }
        var settings = YamlConfiguration.loadConfiguration(fixture.resolve("items/catalog.yml").toFile());
        settings.set("categories.test", "ĉ");
        settings.save(fixture.resolve("items/catalog.yml").toFile());
        Files.writeString(fixture.resolve("items/pages.yml"), extra);
        catalog.reload();
        check(catalog.ids("test").size() == 47, "pagination fixture spans two pages");
        viewer = new TestPlayer("ProbeViewer");
        target = new TestPlayer("ProbeRecipient");
        browser = new ItemBrowser(this, catalog, delivery,
                id -> id.equals(target.id) && target.online ? target.player : null);
        // Root command runs against a simulated sender and inventory.
        var root = Bukkit.getPluginCommand("mandomc");
        root.execute(viewer.player, "mmc", new String[] {"give", "robes_helmet"});
        check(viewer.storage.getItem(0) != null, "self-give command grants one robe");
        viewer.storage.clear();
        viewer.allowed = false;
        root.execute(viewer.player, "mmc", new String[] {"give", "robes_helmet"});
        check(viewer.storage.isEmpty(), "unauthorized command grants nothing");
        viewer.allowed = true;
        browser.open(viewer.player, target.player);
        check(viewer.view.getTopInventory().getSize() == 54, "organized browser opens for another target");
        int slot = categorySlot("test");
        click(slot, ClickType.LEFT);
        Bukkit.getScheduler().runTaskLater(this, () -> step(this::verifyPages), 2L);
    }

    private void verifyPages() {
        check(viewer.view.getTopInventory().getItem(50) != null, "next button on populated first page");
        check(viewer.view.getTopInventory().getItem(44) != null, "45 content slots used");
        click(50, ClickType.LEFT);
        Bukkit.getScheduler().runTaskLater(this, () -> step(() -> {
            var top = viewer.view.getTopInventory();
            check(top.getItem(0) != null && top.getItem(1) != null && top.getItem(2) == null, "second page contains exactly two items");
            check(top.getItem(48) != null && top.getItem(50) == null, "last page has previous but no next button");
            browser.open(viewer.player, target.player);
            click(categorySlot("ammo"), ClickType.LEFT);
            Bukkit.getScheduler().runTaskLater(this, () -> step(this::verifyClicks), 2L);
        }), 2L);
    }

    private void verifyClicks() {
        click(0, ClickType.LEFT);
        check(target.storage.getItem(0).getAmount() == 1, "click gives one to selected recipient");
        check(viewer.storage.isEmpty(), "recipient menu does not give to sender");
        target.storage.clear();
        click(0, ClickType.SHIFT_LEFT);
        check(target.storage.getItem(0).getAmount() == 64, "shift-click gives native maximum stack");
        target.storage.clear();
        click(0, ClickType.NUMBER_KEY);
        click(0, ClickType.DOUBLE_CLICK);
        click(0, ClickType.MIDDLE);
        check(target.storage.isEmpty(), "hotbar swaps, double clicks and creative cloning grant nothing");
        var drag = new InventoryDragEvent(viewer.view, null, new ItemStack(Material.STONE), false,
                Map.of(0, new ItemStack(Material.STONE)));
        browser.drag(drag);
        check(drag.isCancelled(), "dragging cannot alter menu contents");
        for (int i = 0; i < 36; i++) target.storage.setItem(i, new ItemStack(Material.STONE, 64));
        click(0, ClickType.SHIFT_LEFT);
        check(target.storage.getItem(0).getType() == Material.STONE, "full inventory refuses delivery without replacing items");
        target.storage.clear();
        viewer.allowed = false;
        click(0, ClickType.LEFT);
        check(target.storage.isEmpty(), "revoked permission blocks open-menu grants");
        viewer.allowed = true;
        target.online = false;
        click(0, ClickType.LEFT);
        check(target.storage.isEmpty(), "offline target receives no items");
        getLogger().info("ITEMS PROBE PASSED: " + checks + " assertions");
        cleanup();
    }

    private int categorySlot(String name) {
        for (int i = 0; i < 45; i++) {
            var item = viewer.view.getTopInventory().getItem(i);
            if (item != null && PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()).equals(name)) return i;
        }
        throw new IllegalStateException("Missing category " + name);
    }

    private void click(int slot, ClickType click) {
        var event = new InventoryClickEvent(viewer.view, InventoryType.SlotType.CONTAINER, slot, click, InventoryAction.NOTHING, 0);
        browser.click(event);
        check(event.isCancelled(), "menu click cancelled: " + click);
    }

    private void cleanup() {
        if (browser != null) browser.close();
        if (fixture != null && Files.exists(fixture)) {
            try (var paths = Files.walk(fixture)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            } catch (Exception error) { getLogger().warning("Fixture cleanup failed: " + error.getMessage()); }
        }
    }
    @Override public void onDisable() { cleanup(); }
    private interface Checked { void run() throws Exception; }
    private void step(Checked action) { try { action.run(); } catch (Throwable error) {
        getLogger().log(java.util.logging.Level.SEVERE, "ITEMS PROBE FAILED", error); cleanup();
    } }
    private void expectRejected(Checked action, String message) throws Exception {
        try { action.run(); } catch (IllegalArgumentException expected) { check(true, message); return; }
        throw new IllegalStateException(message);
    }
    private void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        checks++; getLogger().info("PASS " + message);
    }

    private static final class TestPlayer {
        private final UUID id = UUID.randomUUID();
        private final Inventory storage = Bukkit.createInventory(null, 36);
        private boolean allowed = true;
        private boolean online = true;
        private InventoryView view;
        private final Player player;
        private TestPlayer(String name) {
            var inventory = (PlayerInventory) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {PlayerInventory.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("getStorageContents")) return storage.getContents();
                        try { return Inventory.class.getMethod(method.getName(), method.getParameterTypes()).invoke(storage, args); }
                        catch (NoSuchMethodException ignored) { return fallback(method.getReturnType()); }
                    });
            player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "getUniqueId" -> id;
                    case "getName" -> name;
                    case "isOnline" -> online;
                    case "hasPermission" -> allowed;
                    case "getInventory" -> inventory;
                    case "getOpenInventory" -> view;
                    case "openInventory" -> { view = makeView((Inventory) args[0]); yield view; }
                    case "closeInventory" -> { view = makeView(Bukkit.createInventory(null, 9)); yield null; }
                    case "toString" -> name;
                    case "hashCode" -> id.hashCode();
                    case "equals" -> proxy == args[0];
                    default -> fallback(method.getReturnType());
                };
            });
            view = makeView(Bukkit.createInventory(null, 9));
        }
        private InventoryView makeView(Inventory top) {
            return (InventoryView) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {InventoryView.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getTopInventory" -> top;
                        case "getBottomInventory" -> storage;
                        case "getPlayer" -> player;
                        case "getType" -> InventoryType.CHEST;
                        case "convertSlot" -> (int) args[0] < 54 ? args[0] : (int) args[0] - 54;
                        case "getInventory" -> (int) args[0] < 54 ? top : storage;
                        case "getItem" -> (int) args[0] < 54 ? top.getItem((int) args[0]) : storage.getItem((int) args[0] - 54);
                        default -> fallback(method.getReturnType());
                    });
        }
        private static Object fallback(Class<?> type) {
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == double.class) return 0d;
            if (type == float.class) return 0f;
            if (type == long.class) return 0L;
            return null;
        }
    }
}
