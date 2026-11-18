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
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;
import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.StonecutterView;
import org.bukkit.plugin.java.JavaPlugin;

/** Show only eligible transformations; native consumption and exact selection/take guards remain authoritative. */
public final class StonecutterRules implements Listener, AutoCloseable {
    private final ItemCatalog items;
    private final JavaPlugin plugin;
    private final net.mandomc.integrations.paper.StonecutterOptions options;
    private record Seen(StonecutterView view,ItemStack input) { }
    private final Map<java.util.UUID,Seen> viewers=new java.util.HashMap<>();
    private final String denied;
    private final Map<String, RecipeDefinition> recipes;
    public StonecutterRules(JavaPlugin plugin, ItemCatalog items, List<RecipeDefinition> definitions, String denied) {
        this.denied = denied;this.plugin=plugin;options=new net.mandomc.integrations.paper.StonecutterOptions();
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
    private void refresh(StonecutterView view) {
        if(!(view.getPlayer() instanceof Player player))return;
        var input=view.getTopInventory().getInputItem();
        if(input==null)input=new ItemStack(org.bukkit.Material.AIR);
        var previous=viewers.get(player.getUniqueId());
        if(previous!=null && previous.view()==view && previous.input().isSimilar(input))return;
        var current=input.clone();viewers.put(player.getUniqueId(),new Seen(view,current));
        options.filter(player,view,current,key->{
            var recipe=key.startsWith("mandomc:")?recipes.get(key.substring("mandomc:".length())):null;
            return recipe==null || allowed(recipe,current);
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void prepare(PrepareResultEvent event) {
        if(event.getView() instanceof StonecutterView view) {
            var before=viewers.get(view.getPlayer().getUniqueId());refresh(view);
            if(before!=viewers.get(view.getPlayer().getUniqueId()))event.setResult(null);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void opened(InventoryOpenEvent event) {
        if(event.getView() instanceof StonecutterView view)
            plugin.getServer().getScheduler().runTask(plugin,()->{if(view.getPlayer().getOpenInventory()==view)refresh(view);});
    }
    @EventHandler public void closed(InventoryCloseEvent event) {
        if(event.getPlayer() instanceof Player player && event.getView() instanceof StonecutterView
                && viewers.remove(player.getUniqueId())!=null)options.restore(player);
    }
    @EventHandler public void quit(PlayerQuitEvent event){viewers.remove(event.getPlayer().getUniqueId());}
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
    @Override public void close() {
        for(var id:List.copyOf(viewers.keySet())) {var player=org.bukkit.Bukkit.getPlayer(id);if(player!=null){options.restore(player);player.closeInventory();}}
        viewers.clear();HandlerList.unregisterAll(this);
    }
}
