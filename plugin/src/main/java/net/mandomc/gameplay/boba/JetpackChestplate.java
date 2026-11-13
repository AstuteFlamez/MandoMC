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

/** Native enchantment is the sole ability marker; the native Agrinium Anvil owns XP and consumption. */
public final class JetpackChestplate implements Listener, AutoCloseable {
    public static final NamespacedKey KEY=new NamespacedKey("mandomc","jetpack");
    private static final NamespacedKey PRIOR_GLIDER=new NamespacedKey("mandomc","jetpack_prior_glider");
    private final JavaPlugin plugin;
    private final ItemCatalog items;
    private final Enchantment enchantment;
    private final JetpackTooltip tooltip;
    private final java.util.List<net.kyori.adventure.text.Component> ingredientLore;
    private final Set<NamespacedKey> recipes=new HashSet<>();
    public JetpackChestplate(JavaPlugin plugin,ItemCatalog items) {
        this.plugin=plugin;this.items=items;
        tooltip=new JetpackTooltip(items);
        ingredientLore=items.create(BobaEquipment.JETPACK).getItemMeta().lore();
        enchantment=Registry.ENCHANTMENT.get(KEY);
        if(enchantment==null)throw new IllegalStateException("Install server/datapacks/mandomc-jetpack in the primary world's datapacks directory before startup: missing mandomc:jetpack enchantment");
        // Retire the former crafting-grid routes; an Agrinium Anvil now owns upgrades.
        for(var material:Material.values())if(material.name().endsWith("_CHESTPLATE")&&material.isItem())
            recipes.add(new NamespacedKey(plugin,"jetpack_"+material.name().toLowerCase(Locale.ROOT)));
        recipes.add(new NamespacedKey(plugin,"jetpack_book_ii"));
        recipes.forEach(Bukkit::removeRecipe);
        Bukkit.getPluginManager().registerEvents(this,plugin);

    }
    public int level(ItemStack item) { return item!=null&&item.getType().name().endsWith("_CHESTPLATE")?Math.clamp(item.getEnchantmentLevel(enchantment),0,2):0; }
    public boolean upgraded(ItemStack item) { return level(item)>0; }
    private boolean token(ItemStack item) { return item!=null&&item.hasItemMeta()&&BobaEquipment.JETPACK.equals(item.getItemMeta().getPersistentDataContainer().get(ItemCatalog.ITEM_ID,PersistentDataType.STRING)); }
    public int bookLevel(ItemStack item) {
        if(item==null)return 0;
        if(item.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta meta) {
            int level=meta.getStoredEnchantLevel(enchantment);
            if(level>0)return level<=2&&meta.getStoredEnchants().size()==1?level:0;
            return token(item)&&meta.getStoredEnchants().isEmpty()?1:0;
        }
        return token(item)&&item.getType()==Material.ECHO_SHARD?1:0;
    }
    private void writeBook(ItemStack item,int level) {
        item.setType(Material.ENCHANTED_BOOK);
        var meta=(org.bukkit.inventory.meta.EnchantmentStorageMeta)item.getItemMeta();
        for(var old:meta.getStoredEnchants().keySet())meta.removeStoredEnchant(old);
        meta.addStoredEnchant(enchantment,level,true);
        meta.displayName(net.kyori.adventure.text.Component.text("Jetpack "+(level==2?"II":"I"),net.kyori.adventure.text.format.NamedTextColor.YELLOW)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,false));
        meta.setItemModel(NamespacedKey.minecraft("enchanted_book"));meta.setCustomModelData(null);
        meta.addItemFlags(ItemFlag.HIDE_STORED_ENCHANTS);meta.lore(ingredientLore);item.setItemMeta(meta);
        item.resetData(DataComponentTypes.GLIDER);item.resetData(DataComponentTypes.EQUIPPABLE);
    }
    public ItemStack book(int level) {
        if(level<1||level>2)throw new IllegalArgumentException("Jetpack level must be I or II");
        var item=items.create(BobaEquipment.JETPACK);writeBook(item,level);return item;
    }
    public ItemStack upgrade(ItemStack chest) { return upgrade(chest,1); }
    public ItemStack upgrade(ItemStack chest,int bookLevel) {
        if(chest==null||!chest.getType().name().endsWith("_CHESTPLATE")||bookLevel<1||bookLevel>2)return null;
        int current=level(chest);int target=current==bookLevel?current+1:Math.max(current,bookLevel);
        if(target>2||target<=current)return null;
        var result=chest.clone();result.setAmount(1);var meta=result.getItemMeta();
        if(!meta.getPersistentDataContainer().has(PRIOR_GLIDER))
            meta.getPersistentDataContainer().set(PRIOR_GLIDER,PersistentDataType.BYTE,(byte)(result.hasData(DataComponentTypes.GLIDER)?1:0));
        meta.addEnchant(enchantment,target,true);result.setItemMeta(meta);result.setData(DataComponentTypes.GLIDER);
        tooltip.update(result,enchantment);return result;
    }
    /** Validated combination helper retained for compatibility; live crafting routes are retired. */
    public ItemStack result(ItemStack[] matrix) {
        ItemStack chest=null;var levels=new ArrayList<Integer>();
        for(var item:matrix) {
            if(item==null||item.getType().isAir())continue;
            int level=bookLevel(item);
            if(level>0)levels.add(level);
            else if(item.getType().name().endsWith("_CHESTPLATE")) { if(chest!=null)return null;chest=item; }
            else return null;
        }
        if(chest==null)return levels.size()==2&&levels.get(0)==1&&levels.get(1)==1?book(2):null;
        return levels.size()==1?upgrade(chest,levels.getFirst()):null;
    }
    private boolean ownRecipe(org.bukkit.inventory.Recipe recipe) { return recipe instanceof Keyed keyed&&recipes.contains(keyed.getKey()); }
    /** Remove only the glide flag introduced by this upgrade after native disenchanting/repair. */
    public ItemStack cleanRemovedEnchantment(ItemStack item) {
        if(item==null||item.getType().isAir()||upgraded(item))return item;
        var meta=item.getItemMeta();var prior=meta.getPersistentDataContainer().get(PRIOR_GLIDER,PersistentDataType.BYTE);
        if(prior==null) { var result=item.clone();tooltip.update(result,enchantment);return result; }
        var result=item.clone();meta=result.getItemMeta();meta.getPersistentDataContainer().remove(PRIOR_GLIDER);result.setItemMeta(meta);
        if(prior==0)result.resetData(DataComponentTypes.GLIDER);
        tooltip.update(result,enchantment);return result;
    }
    /** Old offhand packs are ingredients now; remove only components the previous gear implementation owned. */
    public boolean maintain(ItemStack item) {
        if(item==null||item.getType().isAir())return false;
        if(!token(item)&&!upgraded(item)&&!item.getItemMeta().getPersistentDataContainer().has(PRIOR_GLIDER))return false;
        var before=item.clone();
        if(token(item)) {
            int level=bookLevel(item);
            if(level>0)writeBook(item,level);
            else {
                // A grindstone turns an enchanted book into an ordinary book; never recreate its enchantment.
                var meta=item.getItemMeta();meta.getPersistentDataContainer().remove(ItemCatalog.ITEM_ID);
                meta.displayName(null);meta.lore(null);meta.setItemModel(null);meta.setCustomModelData(null);
                meta.removeItemFlags(ItemFlag.HIDE_STORED_ENCHANTS);item.setItemMeta(meta);
            }
        }
        else if(upgraded(item))tooltip.update(item,enchantment);
        else { var clean=cleanRemovedEnchantment(item);item.setItemMeta(clean.getItemMeta());
            if(!clean.hasData(DataComponentTypes.GLIDER))item.resetData(DataComponentTypes.GLIDER); }
        return !before.equals(item);
    }
    public void migrateIngredient(ItemStack item) { maintain(item); }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void spawn(org.bukkit.event.entity.ItemSpawnEvent event) {
        var item=event.getEntity().getItemStack();if(maintain(item))event.getEntity().setItemStack(item);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void opened(InventoryOpenEvent event) {
        var inventory=event.getInventory();for(int slot=0;slot<inventory.getSize();slot++) {
            var item=inventory.getItem(slot);if(item!=null&&maintain(item))inventory.setItem(slot,item);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent event) {
        if(ownRecipe(event.getRecipe()))event.getInventory().setResult(null);
        else event.getInventory().setResult(cleanRemovedEnchantment(event.getInventory().getResult()));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void craft(CraftItemEvent event) {
        if(ownRecipe(event.getRecipe()))event.setCancelled(true);
        else event.setCurrentItem(cleanRemovedEnchantment(event.getCurrentItem()));
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void grindstone(PrepareGrindstoneEvent event) { event.setResult(cleanRemovedEnchantment(event.getResult())); }
    private boolean agriniumAnvil(org.bukkit.inventory.view.AnvilView view) {
        var location=view.getTopInventory().getLocation();
        if(location==null||!(view.getPlayer() instanceof org.bukkit.entity.Player player)
                ||player.getWorld()!=location.getWorld()||player.getLocation().distanceSquared(location.clone().add(.5,.5,.5))>64)return false;
        return switch(location.getBlock().getType()) {
            case ANVIL,CHIPPED_ANVIL,DAMAGED_ANVIL->true;
            default->false;
        };
    }
    private boolean jetpackInputs(org.bukkit.inventory.view.AnvilView view) {
        return bookLevel(view.getTopInventory().getItem(0))>0||bookLevel(view.getTopInventory().getItem(1))>0;
    }
    private ItemStack anvilResult(org.bukkit.inventory.view.AnvilView view) {
        if(!agriniumAnvil(view))return null;
        var inventory=view.getTopInventory();var left=inventory.getItem(0);var right=inventory.getItem(1);
        if(left==null||left.getAmount()!=1||right==null||bookLevel(right)==0)return null;
        ItemStack result;
        if(bookLevel(left)==1&&bookLevel(right)==1)result=book(2);
        else if(left.getType().name().endsWith("_CHESTPLATE"))result=upgrade(left,bookLevel(right));
        else return null;
        if(result==null)return null;
        // Preserve normal anvil prior-work growth; fuel efficiency is the only level-II ability change.
        int work=Math.max(Objects.requireNonNullElse(left.getData(DataComponentTypes.REPAIR_COST),0),Objects.requireNonNullElse(right.getData(DataComponentTypes.REPAIR_COST),0));
        result.setData(DataComponentTypes.REPAIR_COST,(int)Math.min(Integer.MAX_VALUE,2L*work+1));
        if(result.getType().name().endsWith("_CHESTPLATE")) {
            String name=view.getRenameText();
            String old=left.getItemMeta().hasDisplayName()
                    ?net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(left.getItemMeta().displayName())
                    :left.getI18NDisplayName();
            if(name!=null&&!name.equals(old)) {
                var meta=result.getItemMeta();meta.displayName(name.isBlank()?null:net.kyori.adventure.text.Component.text(name)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC,false));result.setItemMeta(meta);
            }
        }
        return result;
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void anvil(PrepareAnvilEvent event) {
        var view=event.getView();
        if(!jetpackInputs(view)) { event.setResult(cleanRemovedEnchantment(event.getResult()));return; }
        // Existing global survival rules run earlier at this priority. Only pure Jetpack operations qualify.
        var result=anvilResult(view);event.setResult(result);
        if(result!=null) { view.setRepairCost(Math.max(1,view.getRepairCost()));view.setRepairItemCountCost(1); }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void takeAnvil(InventoryClickEvent event) {
        if(event.getRawSlot()!=2||!(event.getView() instanceof org.bukkit.inventory.view.AnvilView view)||!jetpackInputs(view))return;
        var expected=anvilResult(view);
        if(expected==null||!expected.equals(event.getCurrentItem()))event.setCancelled(true);
        // Native anvil pickup owns XP, input consumption, full-inventory handling and anvil wear.
    }
    @EventHandler public void join(PlayerJoinEvent event) { }
    @Override public void close() { recipes.forEach(Bukkit::removeRecipe);HandlerList.unregisterAll(this); }
}
