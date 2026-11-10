package net.mandomc.gameplay.travel;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;
import net.mandomc.gameplay.arrival.ArrivalDestinations;
import net.mandomc.gameplay.ui.MenuItems;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Legacy warp menu/aliases with only current worlds, delegating every trip to TeleportService. */
public final class WarpService implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final TeleportService travel;
    private final ArrivalDestinations arrivals;
    private final ConfigurationSection config;
    private final Map<UUID,Inventory> menus=new HashMap<>();
    private final int size;
    private final Map<Integer,String> slots=new HashMap<>();
    public WarpService(JavaPlugin plugin,TeleportService travel,ArrivalDestinations arrivals,ConfigurationSection config) {
        this.plugin=plugin;this.travel=travel;this.arrivals=arrivals;this.config=config;
        if(config.getInt("schema-version")!=1) throw new IllegalArgumentException("travel/warps.yml: expected schema-version 1");
        size=config.getInt("gui-size",54);
        if(size<9 || size>54 || size%9!=0) throw new IllegalArgumentException("travel/warps.yml: gui-size must be 9..54 in rows of nine");
        var section=config.getConfigurationSection("warps");
        if(section==null || !section.getKeys(false).equals(java.util.Set.copyOf(ids())))
            throw new IllegalArgumentException("travel/warps.yml: configure exactly spawn, hoth and tatooine");
        for(String id:ids()) {
            int slot=config.getInt("warps."+id+".slot",-1);
            Material material=Material.matchMaterial(config.getString("warps."+id+".material",""));
            if(slot<0 || slot>=size || slots.put(slot,id)!=null || material==null || !material.isItem() || material.isAir())
                throw new IllegalArgumentException("travel/warps.yml: invalid/duplicate slot or material for "+id);
            String model=config.getString("warps."+id+".item-model","");
            if(!model.isEmpty() && org.bukkit.NamespacedKey.fromString(model)==null) throw new IllegalArgumentException("travel/warps.yml: missing/invalid item-model for "+id);
            for(String key:List.of("name","description")) require("warps."+id+"."+key);
        }
        require("gui-title");
        for(String key:List.of("players-only","unknown","unavailable","click")) require("messages."+key);
        var command=Objects.requireNonNull(plugin.getCommand("warp"));
        command.setExecutor((sender,ignored,label,args)-> {
            if(!(sender instanceof Player p)) {sender.sendMessage(message("players-only"));return true;}
            if(args.length==0) open(p);
            else if(args.length==1) request(p,args[0]);
            else return false;
            return true;
        });
        command.setTabCompleter((sender,ignored,label,args)->args.length==1
                ?List.of("batuu","hoth","tatooine").stream().filter(id->id.startsWith(args[0].toLowerCase(Locale.ROOT))).toList():List.of());
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
    }
    private void require(String path) {
        if(!config.isString(path) || config.getString(path).isBlank()) throw new IllegalArgumentException("travel/warps.yml: missing "+path);
    }
    public static List<String> ids() {return List.of("spawn","hoth","tatooine");}
    public static String canonical(String input) {
        return switch(input.toLowerCase(Locale.ROOT)) {
            case "batuu","spawn","spawnworld","blackspireoutpost","black_spire_outpost"->"spawn";
            case "hoth"->"hoth";
            case "tatooine"->"tatooine";
            default->null;
        };
    }
    private Component text(String path) {return MiniMessage.miniMessage().deserialize(config.getString(path)).decoration(TextDecoration.ITALIC,false);}
    private Component message(String key) {return text("messages."+key);}
    public Location destination(String id) {
        return switch(id) {case "spawn"->arrivals.spawn();case "hoth"->arrivals.protectedWorld("minecraft:hoth");
            case "tatooine"->arrivals.adventureWorld("minecraft:tatooine");
            default->throw new IllegalStateException("Unknown public warp: "+id);};
    }
    public boolean request(Player player,String input) {
        String id=canonical(input);
        if(id==null) {player.sendMessage(message("unknown"));return false;}
        if(!player.hasPermission("mandomc.travel.warp")) {player.sendMessage(message("unavailable"));return false;}
        return travel.request(player,()->destination(id));
    }
    public Component badges(String id) {
        String fallback = id.equals("hoth") ? "<white>ĕġ" : "<white>đġ";
        return MiniMessage.miniMessage().deserialize(config.getString("warps." + id + ".badges", fallback))
                .decoration(TextDecoration.ITALIC, false);
    }
    public void open(Player player) {
        Inventory inventory=plugin.getServer().createInventory(null,size,text("gui-title"));
        slots.forEach((slot,id)-> {
            String path="warps."+id+".";
            var item=new ItemStack(Material.matchMaterial(config.getString(path+"material")));
            var meta=item.getItemMeta();meta.displayName(text(path+"name"));
            String model=config.getString(path+"item-model", "");
            if(!model.isEmpty()) meta.setItemModel(org.bukkit.NamespacedKey.fromString(model));
            meta.lore(List.of(badges(id), Component.empty(), text(path+"description"),Component.empty(),message("click")));item.setItemMeta(meta);
            inventory.setItem(slot,item);
        });
        MenuItems.fill(inventory);
        player.openInventory(inventory);menus.put(player.getUniqueId(),inventory);
    }
    private boolean owns(Inventory inventory) {return menus.containsValue(inventory);}
    @EventHandler public void click(InventoryClickEvent e) {
        if(!owns(e.getView().getTopInventory()))return;
        e.setCancelled(true);
        if(!(e.getWhoClicked() instanceof Player p) || !e.isLeftClick() || e.isShiftClick())return;
        String id=slots.get(e.getRawSlot());
        if(id!=null) {p.closeInventory();request(p,id);}
    }
    @EventHandler public void drag(InventoryDragEvent e) {if(owns(e.getView().getTopInventory()))e.setCancelled(true);}
    @EventHandler public void closed(InventoryCloseEvent e) {menus.remove(e.getPlayer().getUniqueId(),e.getInventory());}
    public void close() {
        for(var inventory:List.copyOf(menus.values()))
            for(var viewer:List.copyOf(inventory.getViewers()))viewer.closeInventory();
        menus.clear();HandlerList.unregisterAll(this);
    }
}
