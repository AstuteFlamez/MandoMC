package net.mandomc.gameplay.items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Read-only recipe previews. All inventory transfers are blocked, including hotbar/shift/drag paths. */
public final class RecipeGuide implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final CraftingRecipes recipes;
    private final ConfigurationSection config;
    private static final int[] GRID = {10, 11, 12, 19, 20, 21, 28, 29, 30};

    private static final class Menu implements InventoryHolder {
        private final UUID viewer;
        private final Inventory inventory;
        private final Map<Integer, Consumer<Player>> actions = new HashMap<>();
        private Menu(Player player, Component title) {
            viewer = player.getUniqueId();
            inventory = Bukkit.createInventory(this, 54, title);
            net.mandomc.gameplay.ui.MenuItems.fill(inventory);
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    public RecipeGuide(JavaPlugin plugin, CraftingRecipes recipes, ConfigurationSection config) {
        this.plugin = plugin;
        this.recipes = recipes;
        this.config = config;
        for (String key : List.of("title", "detail-title", "previous", "next", "back", "close", "all",
                "category", "hint", "ingredient-hint", "crafting", "unavailable", "player-only")) {
            if (!config.isString("guide." + key)) throw new IllegalArgumentException("items/recipes.yml: missing guide." + key);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        var command = Objects.requireNonNull(plugin.getCommand("recipes"));
        command.setExecutor((sender, ignored, label, args) -> {
            if (!(sender instanceof Player player)) sender.sendMessage(text("player-only", ""));
            else if (args.length == 0) open(player, null, 0);
            else if (args.length == 1) {
                var matches = recipes.definitions().stream().filter(r -> r.result().equals(args[0])).toList();
                if (matches.isEmpty()) player.sendMessage(text("unavailable", ""));
                else detail(player, matches.getFirst(), p -> open(p, null, 0));
            } else return false;
            return true;
        });
        command.setTabCompleter((sender, ignored, label, args) -> args.length == 1
                ? recipes.definitions().stream().map(RecipeDefinition::result).distinct().sorted()
                        .filter(id -> id.startsWith(args[0])).toList() : List.of());
    }

    private void open(Player player, String category, int requestedPage) {
        var choices = recipes.definitions().stream().filter(r -> category == null || r.category().equals(category)).toList();
        int page = ItemBrowser.clampPage(requestedPage, choices.size());
        var menu = new Menu(player, text("title", ""));
        for (int slot = 0, i = page * 45; slot < 45 && i < choices.size(); slot++, i++) {
            var recipe = choices.get(i);
            var icon = recipes.result(recipe);
            var meta = icon.getItemMeta();
            var lore = meta.lore() == null ? new ArrayList<Component>() : new ArrayList<>(meta.lore());
            lore.add(Component.empty());
            lore.add(text("hint", ""));
            meta.lore(lore);
            icon.setItemMeta(meta);
            menu.inventory.setItem(slot, icon);
            menu.actions.put(slot, p -> detail(p, recipe, v -> open(v, category, page)));
        }
        var categories = recipes.definitions().stream().map(RecipeDefinition::category).distinct().sorted().toList();
        button(menu, 45, Material.CHEST, category == null ? "all" : "category", category == null ? "" : category, p -> {
            int next = category == null ? 0 : categories.indexOf(category) + 1;
            open(p, next >= categories.size() ? null : categories.get(next), 0);
        });
        if (page > 0) button(menu, 48, Material.ARROW, "previous", "", p -> open(p, category, page - 1));
        if (page + 1 < ItemBrowser.pageCount(choices.size())) {
            button(menu, 50, Material.ARROW, "next", "", p -> open(p, category, page + 1));
        }
        button(menu, 53, Material.BARRIER, "close", "", Player::closeInventory);
        player.openInventory(menu.inventory);
    }

    private void detail(Player player, RecipeDefinition recipe, Consumer<Player> back) {
        var menu = new Menu(player, text("detail-title", ""));
        for (int slot : GRID) {
            menu.inventory.setItem(slot, null);
        }
        for (int y = 0; y < recipe.shape().size(); y++) {
            for (int x = 0; x < recipe.shape().get(y).length(); x++) {
                char symbol = recipe.shape().get(y).charAt(x);
                if (symbol == ' ') continue;
                String ingredient = recipe.ingredients().get(symbol);
                int slot = GRID[y * 3 + x];
                menu.inventory.setItem(slot, recipes.ingredient(ingredient));
                menu.actions.put(slot, p -> {
                    var matches = recipes.definitions().stream().filter(r -> r.result().equals(ingredient)).toList();
                    if (matches.isEmpty()) {
                        String source = config.getString("sources." + ingredient);
                        p.sendMessage(source == null ? text("unavailable", "") : render(source));
                    } else detail(p, matches.getFirst(), v -> detail(v, recipe, back));
                });
            }
        }
        menu.inventory.setItem(25, recipes.result(recipe));
        button(menu, 23, Material.CRAFTING_TABLE, "crafting", "", p -> {});
        button(menu, 49, Material.BOOK, "ingredient-hint", "", p -> {});
        button(menu, 45, Material.ARROW, "back", "", back);
        button(menu, 53, Material.BARRIER, "close", "", Player::closeInventory);
        player.openInventory(menu.inventory);
    }

    private void button(Menu menu, int slot, Material material, String key, String value, Consumer<Player> action) {
        var item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(text(key, value));
        item.setItemMeta(meta);
        menu.inventory.setItem(slot, item);
        menu.actions.put(slot, action);
    }

    private Component text(String key, String value) {
        return render(Objects.requireNonNull(config.getString("guide." + key, key.equals("leave-empty") ? "<gray>Leave empty" : null), "items/recipes.yml: guide." + key)
                .replace("<value>", value));
    }

    private Component render(String value) {
        return MiniMessage.miniMessage().deserialize(value).decoration(TextDecoration.ITALIC, false);
    }

    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !player.getUniqueId().equals(menu.viewer)) return;
        if (!event.isLeftClick() && !event.isRightClick()) return;
        var action = menu.actions.get(event.getRawSlot());
        if (action == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory() == menu.inventory
                    && player.hasPermission("mandomc.recipes")) action.accept(player);
        });
    }

    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    @Override public void close() {
        for (var player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu) player.closeInventory();
        }
        org.bukkit.event.HandlerList.unregisterAll(this);
    }
}
