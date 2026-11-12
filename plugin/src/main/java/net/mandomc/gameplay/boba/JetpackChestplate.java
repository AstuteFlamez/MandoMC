package net.mandomc.gameplay.boba;

import io.papermc.paper.datacomponent.DataComponentTypes;
import java.util.*;
import net.mandomc.gameplay.items.ItemCatalog;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Native enchantment is the sole ability marker; native crafting owns ingredient consumption. */
public final class JetpackChestplate implements Listener, AutoCloseable {
    public static final NamespacedKey KEY=new NamespacedKey("mandomc","jetpack");
    private static final NamespacedKey PRIOR_GLIDER=new NamespacedKey("mandomc","jetpack_prior_glider");
    private final JavaPlugin plugin;
    private final ItemCatalog items;
    private final Enchantment enchantment;
    private final java.util.List<net.kyori.adventure.text.Component> ingredientLore;
    private final Set<NamespacedKey> recipes=new HashSet<>();
    public JetpackChestplate(JavaPlugin plugin,ItemCatalog items) {
        this.plugin=plugin;this.items=items;
        ingredientLore=items.create(BobaEquipment.JETPACK).getItemMeta().lore();
        enchantment=Registry.ENCHANTMENT.get(KEY);
        if(enchantment==null)throw new IllegalStateException("Install server/datapacks/mandomc-jetpack in the primary world's datapacks directory before startup: missing mandomc:jetpack enchantment");
        for(var material:Material.values())if(material.name().endsWith("_CHESTPLATE")&&material.isItem()) {
            var key=new NamespacedKey(plugin,"jetpack_"+material.name().toLowerCase(Locale.ROOT));
            var recipe=new ShapelessRecipe(key,upgrade(new ItemStack(material)));
            recipe.addIngredient(new RecipeChoice.MaterialChoice(Material.ECHO_SHARD));
            recipe.addIngredient(material);Bukkit.removeRecipe(key);
            if(!Bukkit.addRecipe(recipe))throw new IllegalStateException("Could not register "+key);
            recipes.add(key);
        }
        Bukkit.getPluginManager().registerEvents(this,plugin);
        for(var player:Bukkit.getOnlinePlayers())player.discoverRecipes(recipes);
    }
    public boolean upgraded(ItemStack item) { return item!=null&&item.getType().name().endsWith("_CHESTPLATE")&&item.getEnchantmentLevel(enchantment)>0; }
    public ItemStack upgrade(ItemStack chest) {
        if(chest==null||!chest.getType().name().endsWith("_CHESTPLATE")||upgraded(chest))return null;
        var result=chest.clone();result.setAmount(1);var meta=result.getItemMeta();
        meta.getPersistentDataContainer().set(PRIOR_GLIDER,PersistentDataType.BYTE,(byte)(result.hasData(DataComponentTypes.GLIDER)?1:0));
        meta.addEnchant(enchantment,1,true);result.setItemMeta(meta);result.setData(DataComponentTypes.GLIDER);return result;
    }
    /** Matches both 2x2 and 3x3 grids, without requiring legacy jetpack metadata to match an ExactChoice. */
    public ItemStack result(ItemStack[] matrix) {
        ItemStack chest=null;boolean pack=false;
        for(var item:matrix) {
            if(item==null||item.getType().isAir())continue;
            if(BobaEquipment.JETPACK.equals(items.identify(item))) { if(pack)return null;pack=true; }
            else if(item.getType().name().endsWith("_CHESTPLATE")) { if(chest!=null)return null;chest=item; }
            else return null;
        }
        return pack&&chest!=null?upgrade(chest):null;
    }
    private boolean ownRecipe(org.bukkit.inventory.Recipe recipe) { return recipe instanceof Keyed keyed&&recipes.contains(keyed.getKey()); }
    /** Remove only the glide flag introduced by this upgrade after native disenchanting/repair. */
    public ItemStack cleanRemovedEnchantment(ItemStack item) {
        if(item==null||item.getType().isAir()||upgraded(item))return item;
        var meta=item.getItemMeta();var prior=meta.getPersistentDataContainer().get(PRIOR_GLIDER,PersistentDataType.BYTE);
        if(prior==null)return item;
        var result=item.clone();meta=result.getItemMeta();meta.getPersistentDataContainer().remove(PRIOR_GLIDER);result.setItemMeta(meta);
        if(prior==0)result.resetData(DataComponentTypes.GLIDER);
        return result;
    }
    /** Old offhand packs are ingredients now; remove only components the previous gear implementation owned. */
    public void migrateIngredient(ItemStack item) {
        if(!BobaEquipment.JETPACK.equals(items.identify(item)))return;
        var equippable=item.getData(DataComponentTypes.EQUIPPABLE);
        if(equippable!=null&&equippable.slot()==EquipmentSlot.OFF_HAND) {
            item.resetData(DataComponentTypes.EQUIPPABLE);item.resetData(DataComponentTypes.GLIDER);
        }
        var meta=item.getItemMeta();var lore=ingredientLore;
        if(!Objects.equals(meta.lore(),lore)) { meta.lore(lore);item.setItemMeta(meta); }
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent event) {
        if(ownRecipe(event.getRecipe()))event.getInventory().setResult(result(event.getInventory().getMatrix()));
        else event.getInventory().setResult(cleanRemovedEnchantment(event.getInventory().getResult()));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void craft(CraftItemEvent event) {
        if(ownRecipe(event.getRecipe())) {
            var expected=result(event.getInventory().getMatrix());
            if(expected==null||!expected.equals(event.getCurrentItem()))event.setCancelled(true);
        } else event.setCurrentItem(cleanRemovedEnchantment(event.getCurrentItem()));
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void grindstone(PrepareGrindstoneEvent event) { event.setResult(cleanRemovedEnchantment(event.getResult())); }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void anvil(PrepareAnvilEvent event) { event.setResult(cleanRemovedEnchantment(event.getResult())); }
    @EventHandler public void join(PlayerJoinEvent event) { event.getPlayer().discoverRecipes(recipes); }
    @Override public void close() { recipes.forEach(Bukkit::removeRecipe);HandlerList.unregisterAll(this); }
}
