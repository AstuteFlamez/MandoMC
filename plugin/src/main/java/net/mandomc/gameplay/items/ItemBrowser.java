package net.mandomc.gameplay.items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Server-owned menu actions; display stacks are never moved to the player inventory. */
public final class ItemBrowser implements Listener {
    public static final int PAGE_SIZE = 45;
    public interface Feedback {
        Component text(String key, String value, String player);
        void give(Player sender, Player target, String id, boolean stack);
    }
    private final JavaPlugin plugin;
    private final ItemCatalog catalog;
    private final Feedback feedback;
    private final java.util.function.Function<UUID, Player> players;

    private static final class Menu implements InventoryHolder {
        private final UUID viewer;
        private final UUID target;
        private final String category;
        private final boolean categories;
        private final int page;
        private final int pages;
        private final Map<Integer, String> choices = new HashMap<>();
        private final Inventory inventory;

        private Menu(Player viewer, Player target, String category, boolean categories, int page, int pages, Component title) {
            this.viewer = viewer.getUniqueId();
            this.target = target.getUniqueId();
            this.category = category;
            this.categories = categories;
            this.page = page;
            this.pages = pages;
            inventory = Bukkit.createInventory(this, 54, title);
            net.mandomc.gameplay.ui.MenuItems.fill(inventory);
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    public ItemBrowser(JavaPlugin plugin, ItemCatalog catalog, Feedback feedback) {
        this(plugin, catalog, feedback, Bukkit::getPlayer);
    }

    public ItemBrowser(JavaPlugin plugin, ItemCatalog catalog, Feedback feedback,
            java.util.function.Function<UUID, Player> players) {
        this.players = players;
        this.plugin = plugin;
        this.catalog = catalog;
        this.feedback = feedback;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static int pageCount(int count) { return Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE); }
    public static int clampPage(int page, int count) { return Math.max(0, Math.min(page, pageCount(count) - 1)); }

    public void open(Player viewer, Player target) { open(viewer, target, null, true, 0); }

    private void open(Player viewer, Player target, String category, boolean categories, int requestedPage) {
        if (!viewer.hasPermission("mandomc.items.give")) {
            viewer.closeInventory();
            viewer.sendMessage(feedback.text("denied", "", ""));
            return;
        }
        if (!target.isOnline()) {
            viewer.closeInventory();
            viewer.sendMessage(feedback.text("player-required", "", ""));
            return;
        }
        List<String> choices;
        if (categories) {
            choices = new ArrayList<>();
            choices.add("*");
            choices.addAll(catalog.categories());
        } else choices = catalog.ids(category);
        int page = clampPage(requestedPage, choices.size());
        var menu = new Menu(viewer, target, category, categories, page, pageCount(choices.size()),
                feedback.text(categories ? "gui-categories" : "gui-items", category == null ? "All" : category, target.getName()));
        for (int slot = 0, index = page * PAGE_SIZE; slot < PAGE_SIZE && index < choices.size(); slot++, index++) {
            String choice = choices.get(index);
            ItemStack icon;
            if (categories) {
                icon = button(choice.equals("*") ? Material.CHEST : categoryMaterial(choice),
                        feedback.text("gui-category", choice.equals("*") ? "All items" : choice, target.getName()));
            } else {
                try {
                    icon = catalog.create(choice);
                    if (icon == null) continue;
                    var meta = icon.getItemMeta();
                    var lore = meta.lore() == null ? new ArrayList<Component>() : new ArrayList<>(meta.lore());
                    lore.add(Component.empty());
                    lore.add(feedback.text("gui-click", "", target.getName()));
                    meta.lore(lore);
                    icon.setItemMeta(meta);
                } catch (RuntimeException error) {
                    plugin.getLogger().warning("Item preview unavailable for " + choice + ": " + error.getMessage());
                    icon = button(Material.BARRIER, feedback.text("unavailable", choice, ""));
                }
            }
            menu.choices.put(slot, choice);
            menu.inventory.setItem(slot, icon);
        }
        if (!categories) menu.inventory.setItem(45, button(Material.CHEST, feedback.text("gui-back", "", "")));
        if (page > 0) menu.inventory.setItem(48, button(Material.ARROW, feedback.text("gui-previous", "", "")));
        menu.inventory.setItem(49, button(Material.PAPER, feedback.text("gui-page", (page + 1) + "/" + menu.pages, target.getName())));
        if (page + 1 < menu.pages) menu.inventory.setItem(50, button(Material.ARROW, feedback.text("gui-next", "", "")));
        menu.inventory.setItem(53, button(Material.BARRIER, feedback.text("gui-close", "", "")));
        viewer.openInventory(menu.inventory);
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true); // Also blocks shift-transfer, hotbar swaps, double-click collection and creative cloning.
        if (!(event.getWhoClicked() instanceof Player viewer)) return;
        if (!viewer.getUniqueId().equals(menu.viewer) || !viewer.hasPermission("mandomc.items.give")) {
            later(viewer, Player::closeInventory);
            return;
        }
        var target = players.apply(menu.target);
        if (target == null) {
            viewer.sendMessage(feedback.text("player-required", "", ""));
            later(viewer, Player::closeInventory);
            return;
        }
        if (!java.util.Set.of(org.bukkit.event.inventory.ClickType.LEFT, org.bukkit.event.inventory.ClickType.RIGHT,
                org.bukkit.event.inventory.ClickType.SHIFT_LEFT, org.bukkit.event.inventory.ClickType.SHIFT_RIGHT)
                .contains(event.getClick())) return;
        int slot = event.getRawSlot();
        if (menu.choices.containsKey(slot)) {
            String choice = menu.choices.get(slot);
            if (menu.categories) {
                later(viewer, p -> open(p, target, choice.equals("*") ? null : choice, false, 0));
            } else feedback.give(viewer, target, choice, event.isShiftClick());
        } else if (slot == 45 && !menu.categories) {
            later(viewer, p -> open(p, target));
        } else if (slot == 48 && menu.page > 0) {
            later(viewer, p -> open(p, target, menu.category, menu.categories, menu.page - 1));
        } else if (slot == 50 && menu.page + 1 < menu.pages) {
            later(viewer, p -> open(p, target, menu.category, menu.categories, menu.page + 1));
        } else if (slot == 53) later(viewer, Player::closeInventory);
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    private void later(Player player, java.util.function.Consumer<Player> action) {
        var expected = player.getOpenInventory().getTopInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory() == expected) action.accept(player);
        });
    }

    public void close() {
        for (var player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) player.closeInventory();
        }
    }

    private static Material categoryMaterial(String category) {
        return switch (category) {
            case "armor" -> Material.LEATHER_CHESTPLATE;
            case "weapons" -> Material.DIAMOND_HOE;
            case "ammo" -> Material.ECHO_SHARD;
            default -> Material.BOOK;
        };
    }

    private static ItemStack button(Material material, Component name) {
        var item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
