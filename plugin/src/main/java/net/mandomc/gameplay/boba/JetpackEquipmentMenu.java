package net.mandomc.gameplay.boba;

import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.mandomc.commands.RootCommand;
import net.mandomc.gameplay.items.ItemCatalog;
import net.mandomc.gameplay.ui.MenuItems;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;

/** Small native equipment flow. The only authoritative equipped item remains in the player's offhand. */
public final class JetpackEquipmentMenu implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final ItemCatalog items;
    private final BobaEquipment equipment;
    private final ConfigurationSection language;
    private final Set<Inventory> menus=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Inventory> busy=Collections.newSetFromMap(new IdentityHashMap<>());
    public JetpackEquipmentMenu(JavaPlugin plugin,ItemCatalog items,BobaEquipment equipment,ConfigurationSection language) {
        this.plugin=plugin;this.items=items;this.equipment=equipment;this.language=language;
        for(String key:List.of("menu-title","menu-equip","menu-remove","offhand-full","inventory-full","missing-pack"))
            if(!language.isString(key))throw new IllegalArgumentException("messages.yml: missing boba-equipment."+key);
        Bukkit.getPluginManager().registerEvents(this,plugin);
    }
    public void register(RootCommand root) {
        equipment.visualTrials().register(root);
        root.register("equipment","mandomc.equipment",(sender,args)->{if(sender instanceof Player p)open(p);},(sender,args)->List.of());
    }
    private Component text(String key) {return MiniMessage.miniMessage().deserialize(language.getString(key));}
    public void open(Player player) {
        var menu=Bukkit.createInventory(null,9,text("menu-title"));MenuItems.fill(menu);
        var button=items.create(BobaEquipment.JETPACK);var meta=button.getItemMeta();
        meta.displayName(text(equipment.equipped(player)?"menu-remove":"menu-equip"));button.setItemMeta(meta);
        menu.setItem(4,button);menus.add(menu);player.openInventory(menu);
    }
    public boolean change(Player player) {
        var inventory=player.getInventory();
        if(equipment.equipped(player)) {
            int empty=inventory.firstEmpty();
            if(empty<0||empty>=36) {player.sendActionBar(text("inventory-full"));return false;}
            var pack=inventory.getItemInOffHand();inventory.setItemInOffHand(null);inventory.setItem(empty,pack);
            equipment.detach(player);return true;
        }
        if(!inventory.getItemInOffHand().getType().isAir()) {player.sendActionBar(text("offhand-full"));return false;}
        for(int slot=0;slot<36;slot++) {
            var pack=inventory.getItem(slot);
            if(!BobaEquipment.JETPACK.equals(items.identify(pack)))continue;
            var one=pack.clone();one.setAmount(1);
            if(pack.getAmount()==1)inventory.setItem(slot,null);
            else {pack.setAmount(pack.getAmount()-1);inventory.setItem(slot,pack);}
            inventory.setItemInOffHand(one);return true;
        }
        player.sendActionBar(text("missing-pack"));return false;
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void use(PlayerInteractEvent event) {
        if(event.getHand()!=EquipmentSlot.HAND||!event.getAction().isRightClick()
                ||!BobaEquipment.JETPACK.equals(items.identify(event.getItem())))return;
        if(event.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_AIR&&event.useItemInHand()==Event.Result.DENY)return;
        event.setUseItemInHand(Event.Result.DENY);event.setUseInteractedBlock(Event.Result.DENY);open(event.getPlayer());
    }
    @EventHandler public void click(InventoryClickEvent event) {
        if(!menus.contains(event.getView().getTopInventory()))return;
        event.setCancelled(true);
        if(event.getRawSlot()==4&&event.getClick()==ClickType.LEFT&&event.getWhoClicked() instanceof Player p) {
            var inventory=event.getView().getTopInventory();
            if(!busy.contains(inventory)&&change(p)) {
                busy.add(inventory);
                Bukkit.getScheduler().runTask(plugin,()->{
                    if(p.getOpenInventory().getTopInventory()==inventory)p.closeInventory();
                });
            }
        }
    }
    @EventHandler public void drag(InventoryDragEvent event) {if(menus.contains(event.getView().getTopInventory()))event.setCancelled(true);}
    @EventHandler public void close(InventoryCloseEvent event) {menus.remove(event.getInventory());busy.remove(event.getInventory());}
    @Override public void close() {
        for(var inventory:List.copyOf(menus))for(var viewer:List.copyOf(inventory.getViewers()))viewer.closeInventory();
        menus.clear();busy.clear();HandlerList.unregisterAll(this);
    }
}
