package net.mandomc.gameplay.clans;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.config.AtomicData;
import net.mandomc.gameplay.territory.*;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Clan lifecycle, pairwise raid authority and persistent activity timestamps. */
public final class ClanService implements Listener, AutoCloseable {
    private final JavaPlugin plugin;
    private final TerritoryService territory;
    private final ClanSettings settings;
    private final ConfigurationSection language;
    private final ExecutorService io=Executors.newSingleThreadExecutor(r->new Thread(r,"MandoMC-clan-storage"));
    private final Path path;
    private final ClanState state;
    private final LandRestoration land;
    private final BukkitTask ticker;
    private final ClanProtection protection;
    private boolean failed, closed;
    public ClanService(JavaPlugin plugin,TerritoryService territory,ConfigurationSection config,
                       ConfigurationSection language) throws Exception {
        this.plugin=plugin;this.territory=territory;this.language=language;settings=ClanSettings.read(config);
        path=plugin.getDataFolder().toPath().resolve("data/clans.yml");
        state=ClanStorage.load(path);
        land=new LandRestoration(plugin,io,settings.resetMillis(),settings.restoreBudget(),c->state.owner(c)!=null);
        for(var chunk:state.claims.keySet())if(!land.hasBaseline(chunk))
            throw new IllegalStateException("Claim has no original terrain baseline: "+chunk);
        Bukkit.getServicesManager().register(ClanService.class,this,plugin,ServicePriority.Normal);
        Bukkit.getPluginManager().registerEvents(this,plugin);
        protection=new ClanProtection(this,territory);
        Bukkit.getPluginManager().registerEvents(protection,plugin);
        ticker=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,20,1200);
    }
    public ClanState state(){return state;}
    public ClanSettings settings(){return settings;}
    public LandRestoration land(){return land;}
    public boolean available(){return !failed&&!closed;}
    public ClanState.Clan member(Player p){return state.member(p.getUniqueId());}
    public ClanState.Clan owner(Location l){return state.owner(LandRestoration.chunk(l));}
    public String contestant(Player player) {
        var clan=member(player);return clan==null?player.getUniqueId().toString():clan.id.toString();
    }
    public String contestantName(Player player) {var clan=member(player);return clan==null?player.getName():clan.name;}
    public Component message(String key,String... values) {
        var tags=new ArrayList<net.kyori.adventure.text.minimessage.tag.resolver.TagResolver>();
        for(int i=0;i<values.length;i+=2)tags.add(Placeholder.unparsed(values[i],values[i+1]));
        return MiniMessage.miniMessage().deserialize(language.getString(key,key),tags.toArray(net.kyori.adventure.text.minimessage.tag.resolver.TagResolver[]::new));
    }
    public boolean own(Player p,Location l) {var owner=owner(l);return owner==null||owner.members.contains(p.getUniqueId());}
    public boolean friendly(Entity source,Entity target) {
        if(settings.friendlyFire()||!(target instanceof Player victim))return false;
        Player player=attacker(source);if(player==null||player.equals(victim))return false;
        var clan=member(player);return clan!=null&&clan.members.contains(victim.getUniqueId());
    }
    public boolean canEdit(Player p,Location l) {
        var owner=owner(l);
        return available() && !land.restoring(LandRestoration.chunk(l)) && (owner==null || owner.members.contains(p.getUniqueId()));
    }
    public boolean canContainer(Player p,Location l) {
        if(l.getBlock().getState() instanceof org.bukkit.block.Chest chest
                &&chest.getInventory().getHolder() instanceof org.bukkit.block.DoubleChest doubleChest) {
            var left=containerLocation(doubleChest.getLeftSide());
            var right=containerLocation(doubleChest.getRightSide());
            if(left!=null&&!canContainerSingle(p,left))return false;
            if(right!=null&&!canContainerSingle(p,right))return false;
        }
        return canContainerSingle(p,l);
    }
    public boolean splitContainer(Location l) {
        if(l.getBlock().getState() instanceof org.bukkit.block.Chest chest
                &&chest.getInventory().getHolder() instanceof org.bukkit.block.DoubleChest doubleChest) {
            var left=containerLocation(doubleChest.getLeftSide());
            var right=containerLocation(doubleChest.getRightSide());
            return left!=null&&right!=null&&owner(left)!=owner(right);
        }
        return false;
    }
    private Location containerLocation(org.bukkit.inventory.InventoryHolder holder) {
        // Chest#getInventory returns the combined inventory even when asked through one half.
        return holder instanceof org.bukkit.block.BlockState block ? block.getLocation() : holder.getInventory().getLocation();
    }
    private boolean canContainerSingle(Player p,Location l) {
        if(!available())return false;
        var owner=owner(l);if(owner==null)return true;
        if(owner.abandonedAt!=0||owner.members.contains(p.getUniqueId()))return true;
        var attacker=member(p);
        return attacker!=null&&state.canRaid(attacker.id,owner.id,System.currentTimeMillis(),settings.raidThreshold());
    }
    public static Player attacker(Entity source) {
        if(source instanceof Player p)return p;
        if(source instanceof Projectile projectile && projectile.getShooter() instanceof Player p)return p;
        if(source instanceof TNTPrimed tnt && tnt.getSource()!=source)return attacker(tnt.getSource());
        if(source instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player p)return p;
        return null;
    }
    public boolean blast(Entity source,Location l) {
        if(!available()||!territory.edits(l)||land.restoring(LandRestoration.chunk(l)))return false;
        var owner=owner(l);
        if(owner==null)return land.ready(LandRestoration.chunk(l));
        if(owner.abandonedAt!=0)return attacker(source)!=null; // Public explosive access during the loot window.
        var player=attacker(source);var attacker=player==null?null:member(player);
        return attacker!=null&&state.canRaid(attacker.id,owner.id,System.currentTimeMillis(),settings.raidThreshold());
    }
    public boolean wilderness(ClanState.Chunk c) {
        World world=Bukkit.getWorld(NamespacedKey.fromString(c.world()));
        if(world==null)return false;
        // Includes partial legacy cuboids at any height, rather than sampling a single Y.
        return territory.policy().wildernessChunk(c.world(),c.x(),c.z())
                && world.getWorldBorder().isInside(new Location(world,c.x()*16+.5,world.getMinHeight(),c.z()*16+.5))
                && world.getWorldBorder().isInside(new Location(world,c.x()*16+15.5,world.getMinHeight(),c.z()*16+15.5));
    }
    public CompletableFuture<Void> save() {
        String snapshot=ClanStorage.encode(state);
        return CompletableFuture.runAsync(()->{
            try{AtomicData.write(path,snapshot);}catch(Exception error){throw new CompletionException(error);}
        },io).whenComplete((ignored,error)->{
            if(error!=null){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Clan storage failed; access fails closed",error);
                if(plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,()->failed=true);}
        });
    }
    public void saved(org.bukkit.command.CommandSender sender,String key,String... values) {
        var message=message(key,values);
        save().whenComplete((ignored,error)->{if(plugin.isEnabled())Bukkit.getScheduler().runTask(plugin,
                ()->sender.sendMessage(error==null?message:message("failed")));});
    }
    private void tick() {
        if(!available())return;
        long now=System.currentTimeMillis(); boolean dirty=false;
        for(var clan:List.copyOf(state.clans.values())) {
            if(clan.abandonedAt==0) {
                // Check elapsed inactivity BEFORE returning members can refresh it.
                if(now-clan.lastActive>=settings.inactiveMillis()) {
                    clan.abandonedAt=now;dirty=true;
                    String locations=state.claims.entrySet().stream().filter(e->e.getValue().equals(clan.id))
                            .map(e->e.getKey().world()+" X "+(e.getKey().x()*16)+" Z "+(e.getKey().z()*16))
                            .collect(java.util.stream.Collectors.joining("; "));
                    if(locations.isEmpty())locations="no claims";
                    Bukkit.broadcast(message("abandonment","clan",clan.name,"locations",locations));
                } else if(clan.members.stream().anyMatch(id->Bukkit.getPlayer(id)!=null)) {
                    clan.lastActive=now;dirty=true;
                }
            } else if(now-clan.abandonedAt>=settings.lootMillis()) {
                for(var e:List.copyOf(state.claims.entrySet()))if(e.getValue().equals(clan.id))land.release(e.getKey(),true);
                state.remove(clan);dirty=true;
                Bukkit.broadcast(message("reclaimed","clan",clan.name));
            }
        }
        for(var e:List.copyOf(state.rivalries.entrySet())) {
            var rivalry=e.getValue();
            if(rivalry.activeAttacker()!=null&&rivalry.activeUntil()<=now) {
                var first=state.clans.get(e.getKey().first());var second=state.clans.get(e.getKey().second());
                state.rivalries.put(e.getKey(),new ClanState.Rivalry(0,null,0));dirty=true;
                if(first!=null&&second!=null)Bukkit.broadcast(message("raid-ended","clan",first.name,"rival",second.name));
            }
        }
        if(dirty)save();
    }
    @EventHandler(priority=EventPriority.LOWEST)
    public void join(PlayerJoinEvent e){tick();var clan=member(e.getPlayer());if(clan!=null&&clan.abandonedAt==0){clan.lastActive=System.currentTimeMillis();save();}}
    @EventHandler(priority=EventPriority.MONITOR)
    public void quit(PlayerQuitEvent e){var clan=member(e.getPlayer());if(clan!=null&&clan.abandonedAt==0){clan.lastActive=System.currentTimeMillis();save();}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageByEntityEvent e) {
        if(settings.friendlyFire()||!(e.getEntity() instanceof Player victim))return;
        Player p=attacker(e.getDamageSource().getCausingEntity());if(p==null)p=attacker(e.getDamager());
        if(p==null||p.equals(victim))return;
        var clan=member(p);if(clan!=null&&clan.members.contains(victim.getUniqueId()))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.MONITOR)
    public void death(PlayerDeathEvent e) {
        if(!available())return;
        var killer=e.getEntity().getKiller();if(killer==null||killer.equals(e.getEntity()))return;
        var a=member(killer);var b=member(e.getEntity());
        if(a==null||b==null||a.abandonedAt!=0||b.abandonedAt!=0)return;
        state.kill(a.id,b.id,System.currentTimeMillis());save();
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void respawn(PlayerRespawnEvent e) {
        if(e.getRespawnReason()!=PlayerRespawnEvent.RespawnReason.DEATH
                ||e.getPlayer().getPersistentDataContainer().has(new NamespacedKey("mandomc","ilum_inventory")))return;
        var clan=member(e.getPlayer());var home=home(clan);
        if(home!=null)e.setRespawnLocation(home);
    }
    public Location home(ClanState.Clan clan) {
        if(clan==null||clan.abandonedAt!=0||clan.home==null)return null;
        var h=clan.home;var world=Bukkit.getWorld(NamespacedKey.fromString(h.world()));if(world==null)return null;
        var l=new Location(world,h.x(),h.y(),h.z(),h.yaw(),h.pitch());
        if(owner(l)!=clan||!world.getWorldBorder().isInside(l)||!l.getBlock().isPassable()
                ||!l.clone().add(0,1,0).getBlock().isPassable()||!l.clone().add(0,-1,0).getBlock().getType().isSolid())return null;
        if(l.getBlock().isLiquid()||l.clone().add(0,1,0).getBlock().isLiquid())return null;
        var hazards=Set.of(Material.LAVA,Material.FIRE,Material.SOUL_FIRE,Material.CACTUS,Material.MAGMA_BLOCK,
                Material.CAMPFIRE,Material.SOUL_CAMPFIRE,Material.SWEET_BERRY_BUSH,Material.POWDER_SNOW);
        if(hazards.contains(l.getBlock().getType())||hazards.contains(l.clone().add(0,1,0).getBlock().getType())
                ||hazards.contains(l.clone().add(0,-1,0).getBlock().getType()))return null;
        return l;
    }
    public void breached(Entity source,List<org.bukkit.block.Block> blocks) {
        var p=attacker(source);var a=p==null?null:member(p);if(a==null)return;
        boolean changed=false;
        for(var b:blocks){var defender=owner(b.getLocation());if(defender!=null&&state.breach(a.id,defender.id,
                System.currentTimeMillis(),settings.raidMillis(),settings.raidThreshold())) {
            Bukkit.broadcast(message("raid-started","clan",a.name,"rival",defender.name));changed=true;
        }}
        if(changed)save();
    }
    @Override public void close() {
        if(closed)return;
        closed=true;ticker.cancel();land.close();save();io.shutdown();
        try{if(!io.awaitTermination(30,TimeUnit.SECONDS))plugin.getLogger().severe("Clan IO did not finish before shutdown");}
        catch(InterruptedException e){Thread.currentThread().interrupt();}
        HandlerList.unregisterAll(this);HandlerList.unregisterAll(protection);Bukkit.getServicesManager().unregister(ClanService.class,this);
    }
}
