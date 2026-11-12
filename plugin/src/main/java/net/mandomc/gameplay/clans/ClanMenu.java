package net.mandomc.gameplay.clans;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.scheduler.BukkitTask;

/** Informational clan UI. Real owned items never enter it; only help and page arrows perform actions. */
public final class ClanMenu implements Listener, AutoCloseable {
    private static final class View implements InventoryHolder {
        final UUID player;
        final Inventory inventory;
        int page;
        View(Player player){this.player=player.getUniqueId();inventory=Bukkit.createInventory(this,54,text("Clans",NamedTextColor.GOLD));}
        @Override public Inventory getInventory(){return inventory;}
    }
    private final JavaPlugin plugin;
    private final ClanService clans;
    private final Consumer<Player> help;
    private final Map<UUID,View> views=new HashMap<>();
    private final Map<UUID,PlayerProfile> profiles=new HashMap<>();
    private final Map<UUID,Long> requested=new HashMap<>();
    private final BukkitTask ticker;
    private volatile boolean closed;
    public ClanMenu(JavaPlugin plugin,ClanService clans,Consumer<Player> help){
        this.plugin=plugin;this.clans=clans;this.help=help;
        Bukkit.getPluginManager().registerEvents(this,plugin);
        ticker=Bukkit.getScheduler().runTaskTimer(plugin,()->{
            for(var view:List.copyOf(views.values()))render(view);
        },20,20);
    }
    private static Component text(String value,NamedTextColor color){return Component.text(value,color).decoration(TextDecoration.ITALIC,false);}
    private static ItemStack item(Material type,String name,String... lore){
        var item=new ItemStack(type);var meta=item.getItemMeta();meta.displayName(text(name,NamedTextColor.GOLD));
        meta.lore(Arrays.stream(lore).map(line->text(line,NamedTextColor.GRAY)).toList());item.setItemMeta(meta);return item;
    }
    public void open(Player player){
        var cursor=player.getItemOnCursor();
        if(cursor!=null&&!cursor.isEmpty()){player.sendMessage(clans.message("menu-busy"));return;}
        var top=player.getOpenInventory().getTopInventory();
        if(top.getHolder() instanceof View existing&&existing.player.equals(player.getUniqueId())){render(existing);return;}
        // Opening from another workstation can return ingredients and drop them when storage is full.
        if(!(top instanceof CraftingInventory crafting)
                || Arrays.stream(crafting.getMatrix()).anyMatch(stack->stack!=null&&!stack.isEmpty())){
            player.sendMessage(clans.message("menu-busy"));return;
        }
        var view=new View(player);views.put(view.player,view);render(view);
        player.openInventory(view.inventory);
    }
    public String playerName(UUID id){
        var online=Bukkit.getPlayer(id);if(online!=null)return online.getName();
        var profile=profiles.get(id);return profile!=null&&profile.getName()!=null?profile.getName():Bukkit.getOfflinePlayer(id).getName();
    }
    private String worldName(String key){return switch(key){
        case "minecraft:mandalore_review"->"Mandalore";case "minecraft:tatooine_review","minecraft:tatooine"->"Tatooine";
        case "minecraft:spawnworld"->"Batuu";case "minecraft:hoth"->"Hoth";case "minecraft:ilum"->"Ilum";
        default->{var world=Bukkit.getWorld(NamespacedKey.fromString(key));yield world==null?"Unknown world":world.getName();}
    };}
    private void set(View view,int slot,ItemStack item){if(!item.equals(view.inventory.getItem(slot)))view.inventory.setItem(slot,item);}
    private void profile(UUID id){
        long now=System.currentTimeMillis();if(now-requested.getOrDefault(id,0L)<600000)return;
        requested.put(id,now);var seed=profiles.computeIfAbsent(id,Bukkit::createPlayerProfile);
        seed.update().thenApply(updated->(PlayerProfile)updated).completeOnTimeout(seed,3,TimeUnit.SECONDS).whenComplete((updated,error)->{
            if(closed||!plugin.isEnabled())return;
            Bukkit.getScheduler().runTask(plugin,()->{
                if(closed)return;if(error==null&&updated!=null)profiles.put(id,updated);
                for(var view:List.copyOf(views.values()))render(view);
            });
        });
    }
    private void render(View view){
        var player=Bukkit.getPlayer(view.player);if(player==null){views.remove(view.player);return;}
        var clan=clans.member(player);var background=item(Material.GRAY_STAINED_GLASS_PANE," ");
        var entries=new HashMap<Integer,ItemStack>();
        entries.put(49,item(Material.BOOK,"Clan help","Click for /c help.","Use explicit commands for management."));
        if(clan==null){
            entries.put(4,item(Material.WHITE_BANNER,"No clan","Create or join a clan to build a shared base."));
            entries.put(20,item(Material.WRITABLE_BOOK,"Create a clan","/c create <name>","Up to "+clans.settings().members()+" players and "+clans.settings().chunks()+" chunks."));
            entries.put(24,item(Material.PLAYER_HEAD,"Join a clan","An officer or leader can invite you.","/c join <clan>","New members start as Recruit."));
        }else{
            var role=clans.state().role(clan,view.player);
            entries.put(4,item(Material.WHITE_BANNER,clan.name,clan.abandonedAt==0?"Active clan":"Public loot: disbanding cannot be cancelled."));
            entries.put(10,item(Material.COMPASS,"Your role: "+role.label(),"Leader > Officer > Member > Recruit","Recruits cannot use or break clan storage."));
            entries.put(12,item(Material.PAPER,"Members: "+clan.members.size()+"/"+clans.settings().members(),clan.members.size()>clans.settings().members()?"Over limit: new joins are paused.":"Roster below shows roles and online status."));
            long count=clans.state().claims.values().stream().filter(clan.id::equals).count();
            entries.put(14,item(Material.GRASS_BLOCK,"Claims: "+count+"/"+clans.settings().chunks(),"New claims must share an edge.","Leave a full empty chunk from rivals/zones.","/c map shows current land."));
            entries.put(16,item(Material.ENDER_PEARL,"Clan home",clan.home==null?"Not set: leader uses /c sethome.":worldName(clan.home.world())+" "+(int)Math.floor(clan.home.x())+", "+(int)Math.floor(clan.home.y())+", "+(int)Math.floor(clan.home.z()),"/c home uses normal travel rules."));
            var rivals=new ArrayList<String>();long now=System.currentTimeMillis();
            var rivalEntries=new ArrayList<>(clans.state().rivalries.entrySet());
            rivalEntries.sort(Comparator.<Map.Entry<ClanState.Pair,ClanState.Rivalry>>comparingInt(entry->entry.getValue().activeUntil()>now?0:1)
                    .thenComparingLong(entry->-Math.abs((long)entry.getValue().score())));
            for(var entry:rivalEntries){
                var pair=entry.getKey();if(!pair.first().equals(clan.id)&&!pair.second().equals(clan.id))continue;
                var id=pair.first().equals(clan.id)?pair.second():pair.first();var rival=clans.state().clans.get(id);if(rival==null)continue;
                var raid=entry.getValue();String status=raid.activeUntil()>now
                        ? (clan.id.equals(raid.activeAttacker())?"Raiding ":"Under raid by ")+rival.name+" ("+Math.max(1,(raid.activeUntil()-now+59999)/60000)+"m)"
                        : rival.name+": "+clans.state().score(clan.id,id)+" warpoints";
                rivals.add(status);
            }
            entries.put(22,item(Material.IRON_SWORD,"Warpoints & raids",rivals.isEmpty()?new String[]{"No rival warpoints or active raids.","+10 starts explosive raid eligibility.","/c warpoints <clan>"}:rivals.stream().limit(6).toArray(String[]::new)));
            var members=new ArrayList<>(clan.members);
            members.sort(Comparator.comparingInt((UUID id)->clans.state().role(clan,id).ordinal()).reversed().thenComparing(UUID::toString));
            int pages=Math.max(1,(members.size()+17)/18);view.page=Math.max(0,Math.min(view.page,pages-1));
            for(int i=0;i<18&&view.page*18+i<members.size();i++){
                UUID id=members.get(view.page*18+i);var online=Bukkit.getPlayer(id);PlayerProfile owner;
                if(online!=null){owner=online.getPlayerProfile();profiles.put(id,owner);}else{profile(id);owner=profiles.get(id);}
                String name=online!=null?online.getName():owner!=null&&owner.getName()!=null?owner.getName():"Player "+id.toString().substring(0,8);
                var head=item(Material.PLAYER_HEAD,name,"Role: "+clans.state().role(clan,id).label(),online!=null&&player.canSee(online)?"Online":"Offline","Use /c help for role commands.");
                if(owner!=null){var meta=(SkullMeta)head.getItemMeta();meta.setOwnerProfile(owner);head.setItemMeta(meta);}
                entries.put(27+i,head);
            }
            if(view.page>0)entries.put(45,item(Material.ARROW,"Previous members","Page "+(view.page+1)+"/"+pages));
            if(view.page+1<pages)entries.put(53,item(Material.ARROW,"More members","Page "+(view.page+1)+"/"+pages));
        }
        for(int slot=0;slot<54;slot++)set(view,slot,entries.getOrDefault(slot,background));
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof View view))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player player)||!view.player.equals(player.getUniqueId())
                ||(event.getClick()!=ClickType.LEFT&&event.getClick()!=ClickType.RIGHT))return;
        if(event.getRawSlot()==49)help.accept(player);
        else if(event.getRawSlot()==45&&event.getCurrentItem()!=null&&event.getCurrentItem().getType()==Material.ARROW){view.page--;render(view);}
        else if(event.getRawSlot()==53&&event.getCurrentItem()!=null&&event.getCurrentItem().getType()==Material.ARROW){view.page++;render(view);}
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent event){
        if(event.getView().getTopInventory().getHolder() instanceof View)event.setCancelled(true);
    }
    @EventHandler public void close(InventoryCloseEvent event){
        if(event.getInventory().getHolder() instanceof View view)views.remove(view.player,view);
    }
    @EventHandler public void quit(PlayerQuitEvent event){views.remove(event.getPlayer().getUniqueId());}
    @Override public void close(){
        closed=true;ticker.cancel();HandlerList.unregisterAll(this);
        for(var view:List.copyOf(views.values())){
            var player=Bukkit.getPlayer(view.player);if(player!=null&&player.getOpenInventory().getTopInventory()==view.inventory)player.closeInventory();
        }
        views.clear();profiles.clear();requested.clear();
    }
}
