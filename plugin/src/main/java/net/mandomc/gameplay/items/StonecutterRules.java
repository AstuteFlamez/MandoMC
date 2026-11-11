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
    private final String denied;
    private final Map<String, RecipeDefinition> recipes;
    public StonecutterRules(JavaPlugin plugin, ItemCatalog items, List<RecipeDefinition> definitions, String denied) {
        this.denied = denied;
        this.items = items;
        recipes = definitions.stream().filter(RecipeDefinition::stonecutting)
                .collect(Collectors.toUnmodifiableMap(r -> "refinish_" + r.id(), r -> r));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    private RecipeDefinition owned(org.bukkit.inventory.StonecuttingRecipe recipe) {
        return recipe.getKey().getNamespace().equals("mandomc") ? recipes.get(recipe.getKey().getKey()) : null;
    }
    private boolean allowed(RecipeDefinition recipe, ItemStack input) {
        var expected = items.create(recipe.ingredients().get('I'));
        return input != null && !input.isEmpty() && expected != null && input.isSimilar(expected);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void select(PlayerStonecutterRecipeSelectEvent event) {
        var recipe = owned(event.getStonecuttingRecipe());
        if (recipe == null || allowed(recipe, event.getStonecutterInventory().getInputItem())) return;
        event.getStonecutterInventory().setResult(null);
        event.setCancelled(true);
        event.getPlayer().sendMessage(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(denied,
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("item",
                        items.create(recipe.ingredients().get('I')).getItemMeta().displayName())));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void take(InventoryClickEvent event) {
        if (!(event.getView() instanceof StonecutterView view) || event.getRawSlot() != 1) return;
        int selected = view.getSelectedRecipeIndex(); var visible = view.getRecipes();
        if (selected < 0 || selected >= visible.size()) return;
        var recipe = owned(visible.get(selected));
        if (recipe == null) return;
        var input = view.getTopInventory().getInputItem();
        var expected = items.create(recipe.result());
        if (!allowed(recipe, input) || event.getCurrentItem() == null || !event.getCurrentItem().isSimilar(expected)) {
            event.setCancelled(true); view.getTopInventory().setResult(null);
        }
    }
    @Override public void close() { HandlerList.unregisterAll(this); }
}
