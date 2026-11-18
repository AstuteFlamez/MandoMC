package net.mandomc.gameplay.items;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import io.papermc.paper.event.player.PlayerStonecutterRecipeSelectEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.StonecutterView;
import org.bukkit.plugin.java.JavaPlugin;

/** Keeps wire-visible and server recipe indices identical; exact ownership is checked at selection and take. */
public final class StonecutterRules implements Listener, AutoCloseable {
    private final ItemCatalog items;
    private final net.mandomc.integrations.paper.StonecutterOptions options = new net.mandomc.integrations.paper.StonecutterOptions();
    private final java.util.Map<java.util.UUID, String> inputs = new java.util.HashMap<>();
    private final org.bukkit.scheduler.BukkitTask task;
    private final String denied;
    private final Map<String, RecipeDefinition> recipes;
    public StonecutterRules(JavaPlugin plugin, ItemCatalog items, List<RecipeDefinition> definitions, String denied) {
        this.denied = denied;
        this.items = items;
        recipes = definitions.stream().filter(RecipeDefinition::stonecutting)
                .collect(Collectors.toUnmodifiableMap(r -> "refinish_" + r.id(), r -> r));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (var player : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (!(player.getOpenInventory() instanceof StonecutterView view)) continue;
                var input = view.getTopInventory().getInputItem();
                String id = items.identify(input);
                String identity = java.util.Objects.requireNonNullElse(id, input == null ? "air" : input.getType().name());
                if (identity.equals(inputs.put(player.getUniqueId(), identity))) { updateResult(view); continue; }
                options.filter(player, view, input == null ? new ItemStack(org.bukkit.Material.AIR) : input,
                        key -> !key.startsWith("mandomc:refinish_") || allowed(recipes.get(key.substring("mandomc:".length())), input));
            }
        }, 1, 1);
    }
    private RecipeDefinition owned(org.bukkit.inventory.StonecuttingRecipe recipe) {
        return recipe.getKey().getNamespace().equals("mandomc") ? recipes.get(recipe.getKey().getKey()) : null;
    }
    private boolean allowed(RecipeDefinition recipe, ItemStack input) {
        if (recipe == null) return false;
        var expected = items.create(recipe.ingredients().get('I'));
        if (recipe.category().equals("lightsabers")) return input != null && expected != null
                && input.getType() == expected.getType() && recipe.ingredients().get('I').equals(items.identify(input));
        return input != null && !input.isEmpty() && expected != null && input.isSimilar(expected);
    }
    private ItemStack result(RecipeDefinition recipe, ItemStack input) {
        var target = items.create(recipe.result());
        if (!recipe.category().equals("lightsabers")) return target;
        var source = items.create(recipe.ingredients().get('I'));
        var result = input.clone(); result.setAmount(recipe.amount());
        var meta = result.getItemMeta(); var style = target.getItemMeta();
        meta.getPersistentDataContainer().set(ItemCatalog.ITEM_ID, org.bukkit.persistence.PersistentDataType.STRING, recipe.result());
        meta.setItemModel(style.getItemModel()); meta.setCustomModelDataComponent(style.getCustomModelDataComponent());
        if (java.util.Objects.equals(meta.displayName(), source.getItemMeta().displayName())) meta.displayName(style.displayName());
        meta.lore(style.lore()); result.setItemMeta(meta); return result;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void select(PlayerStonecutterRecipeSelectEvent event) {
        var recipe = owned(event.getStonecuttingRecipe());
        if (recipe == null) return;
        var input = event.getStonecutterInventory().getInputItem();
        if (allowed(recipe, input)) {
            // Paper's selection event switches by recipe key; it does not use a replaced recipe's result.
            // Refresh after native selection and validate again at pickup, including same-tick clicks.
            return;
        }
        event.getStonecutterInventory().setResult(null);
        event.setCancelled(true);
        event.getPlayer().sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(denied,
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("item",
                        items.create(recipe.ingredients().get('I')).getItemMeta().displayName())));
    }
    private ItemStack selectedResult(StonecutterView view) {
        int selected = view.getSelectedRecipeIndex(); var visible = view.getRecipes();
        if (selected < 0 || selected >= visible.size()) return null;
        var recipe = owned(visible.get(selected)); var input = view.getTopInventory().getInputItem();
        return recipe != null && allowed(recipe, input) ? result(recipe, input) : null;
    }
    private void updateResult(StonecutterView view) {
        int selected = view.getSelectedRecipeIndex(); var visible = view.getRecipes();
        if (selected < 0 || selected >= visible.size() || owned(visible.get(selected)) == null) return;
        var expected = selectedResult(view);
        if (!java.util.Objects.equals(expected, view.getTopInventory().getResult())) view.getTopInventory().setResult(expected);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void prepare(com.destroystokyo.paper.event.inventory.PrepareResultEvent event) {
        if (!(event.getView() instanceof StonecutterView view)) return;
        int selected = view.getSelectedRecipeIndex(); var visible = view.getRecipes();
        if (selected >= 0 && selected < visible.size() && owned(visible.get(selected)) != null) event.setResult(selectedResult(view));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void take(InventoryClickEvent event) {
        if (!(event.getView() instanceof StonecutterView view) || event.getRawSlot() != 1) return;
        int selected = view.getSelectedRecipeIndex(); var visible = view.getRecipes();
        if (selected < 0 || selected >= visible.size()) return;
        var recipe = owned(visible.get(selected));
        if (recipe == null) return;
        var input = view.getTopInventory().getInputItem();
        var expected = allowed(recipe, input) ? result(recipe, input) : null;
        if (!allowed(recipe, input) || event.getCurrentItem() == null || !event.getCurrentItem().isSimilar(expected)) {
            event.setCancelled(true); view.getTopInventory().setResult(null);
        }
    }
    @EventHandler public void closed(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (inputs.remove(event.getPlayer().getUniqueId()) != null && event.getPlayer() instanceof org.bukkit.entity.Player player) options.restore(player);
    }
    @Override public void close() { task.cancel(); HandlerList.unregisterAll(this); }
}
