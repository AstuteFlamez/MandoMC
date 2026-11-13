package net.mandomc.gameplay.boba;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.mandomc.gameplay.items.ItemCatalog;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** Native swept flame packets reuse Boba's smoke/flame pair, 10-block/s speed and nine-block reach.
 * One accepted burst purchases all its packets, never one ammo unit per particle or damage tick. */
public final class PlayerFlamethrower implements Listener, AutoCloseable {
    private static final NamespacedKey NEXT_USE=new NamespacedKey("mandomc","flamethrower_next_use_ms");
    private record Packet(Location at,Vector direction,double traveled) { }
    private static final class Burst {
        final Player owner; final World world; final int start; final List<Packet> packets=new ArrayList<>();
        final Map<UUID,Integer> hits=new HashMap<>();
        Burst(Player owner,int start) { this.owner=owner;this.world=owner.getWorld();this.start=start; }
    }
    private record Burn(UUID owner,World world,long until) { }
    private final JavaPlugin plugin;
    private final ItemCatalog items;
    private final BobaEquipmentSettings settings;
    private final Predicate<Location> allowed;
    private final BiConsumer<Player,String> message;
    private final Map<UUID,Burst> bursts=new HashMap<>();
    private final Map<UUID,Burn> burns=new HashMap<>();
    private final BukkitTask task;
    private Player hitting;private LivingEntity victim;private boolean accepted;
    private boolean closed;
    public PlayerFlamethrower(JavaPlugin plugin,ItemCatalog items,BobaEquipmentSettings settings,
            Predicate<Location> allowed,BiConsumer<Player,String> message) {
        this.plugin=plugin;this.items=items;this.settings=settings;this.allowed=allowed;this.message=message;
        Bukkit.getPluginManager().registerEvents(this,plugin);
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);
    }
    private boolean wielding(Player p) { return BobaEquipment.FLAMETHROWER.equals(items.identify(p.getInventory().getItemInMainHand())); }
    private boolean valid(Player p) {
        return !closed&&p.isOnline()&&!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR&&allowed.test(p.getLocation());
    }
    public boolean activate(Player player) {
        if(!valid(player)||!wielding(player)) { message.accept(player,"flame-denied");return false; }
        long now=System.currentTimeMillis();
        if(bursts.containsKey(player.getUniqueId())||now<player.getPersistentDataContainer().getOrDefault(NEXT_USE,PersistentDataType.LONG,0L)) {
            message.accept(player,"cooldown");return false;
        }
        var origin=muzzle(player);
        if(!allowed.test(origin)||blocked(player.getEyeLocation(),origin)) { message.accept(player,"flame-denied");return false; }
        if(!BobaInventory.fireCharge(player.getInventory())) { message.accept(player,"no-ammo");return false; }
        player.getPersistentDataContainer().set(NEXT_USE,PersistentDataType.LONG,now+settings.cooldownTicks()*50L);
        var burst=new Burst(player,Bukkit.getCurrentTick());
        burst.packets.add(new Packet(origin,player.getEyeLocation().getDirection(),0));
        bursts.put(player.getUniqueId(),burst);
        player.setCooldown(Material.BLAZE_ROD,settings.cooldownTicks());
        player.getWorld().playSound(origin,"mandomc:mobs.flamethrower",1,1);
        return true;
    }
    public static Location muzzle(Player p) {
        var eye=p.getEyeLocation();var forward=eye.getDirection();
        var right=new Vector(-forward.getZ(),0,forward.getX());
        if(right.lengthSquared()>.0001)right.normalize();
        if(p.getMainHand()==org.bukkit.inventory.MainHand.LEFT)right.multiply(-1);
        return eye.add(forward.multiply(.35)).add(right.multiply(.28)).add(0,-.25,0);
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void use(PlayerInteractEvent e) {
        if(e.getHand()!=EquipmentSlot.HAND||!e.getAction().isRightClick()||!wielding(e.getPlayer()))return;
        if(e.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_AIR&&e.useItemInHand()==Event.Result.DENY)return;
        e.setUseInteractedBlock(Event.Result.DENY);e.setUseItemInHand(Event.Result.DENY);
        activate(e.getPlayer());
    }
    private boolean blocked(Location start,Location end) {
        var delta=end.toVector().subtract(start.toVector());double length=delta.length();
        return length>.00001&&start.getWorld().rayTraceBlocks(start,delta.multiply(1/length),length,FluidCollisionMode.NEVER,false)!=null;
    }
    private boolean permittedPath(Location start,Location end) {
        var delta=end.toVector().subtract(start.toVector());
        int steps=Math.max(1,(int)Math.ceil(delta.length()/.2));
        for(int i=0;i<=steps;i++)if(!allowed.test(start.clone().add(delta.clone().multiply(i/(double)steps))))return false;
        return true;
    }
    private void tick() {
        int now=Bukkit.getCurrentTick();
        for(var it=bursts.values().iterator();it.hasNext();) {
            var b=it.next();var owner=b.owner;int age=now-b.start;
            if(!valid(owner)||owner.getWorld()!=b.world||!wielding(owner)) { it.remove();continue; }
            if(age<settings.burstTicks()&&age%settings.emitInterval()==0) {
                var origin=muzzle(owner);
                if(!blocked(owner.getEyeLocation(),origin)&&permittedPath(owner.getEyeLocation(),origin))
                    b.packets.add(new Packet(origin,owner.getEyeLocation().getDirection(),0));
            }
            for(var pit=b.packets.listIterator();pit.hasNext();) {
                var packet=pit.next();double step=Math.min(settings.projectileSpeed(),settings.range()-packet.traveled);
                if(step<=.00001) { pit.remove();continue; }
                var hit=b.world.rayTraceBlocks(packet.at,packet.direction,step,FluidCollisionMode.NEVER,false);
                var end=hit==null?packet.at.clone().add(packet.direction.clone().multiply(step)):hit.getHitPosition().toLocation(b.world);
                if(!permittedPath(packet.at,end)) { pit.remove();continue; }
                // Swept collision includes thin walls and fast targets; visual density never controls damage count.
                for(var entity:b.world.getNearbyEntities(packet.at.toVector().getMidpoint(end.toVector()).toLocation(b.world),
                        step+settings.radius(),step+settings.radius(),step+settings.radius())) {
                    if(!(entity instanceof LivingEntity target)||target==owner||target instanceof ArmorStand||target.isDead()
                            ||target instanceof Player p&&(p.getGameMode()==GameMode.CREATIVE||p.getGameMode()==GameMode.SPECTATOR)
                            ||!allowed.test(target.getLocation())||!b.world.getPVP()&&target instanceof Player)continue;
                    var box=target.getBoundingBox().expand(settings.radius());
                    boolean contact=box.contains(packet.at.toVector())||box.rayTrace(packet.at.toVector(),packet.direction,
                            Math.max(.00001,packet.at.distance(end)))!=null;
                    var center=target.getBoundingBox().getCenter().toLocation(b.world);
                    if(!contact||blocked(packet.at,center)||!permittedPath(packet.at,center)
                            ||now<b.hits.getOrDefault(target.getUniqueId(),Integer.MIN_VALUE))continue;
                    b.hits.put(target.getUniqueId(),now+settings.damageInterval());
                    damage(owner,target);
                }
                b.world.spawnParticle(Particle.FLAME,end,1,.1,.1,.1,0);
                b.world.spawnParticle(Particle.LARGE_SMOKE,end,1,.1,.1,.1,0);
                if(hit!=null) {
                    b.world.spawnParticle(Particle.FLAME,end,3,.1,.1,.1,.05);
                    b.world.spawnParticle(Particle.LARGE_SMOKE,end,3,.1,.1,.1,.05);pit.remove();
                } else pit.set(new Packet(end,packet.direction,packet.traveled+step));
            }
            if(age>=settings.burstTicks()&&b.packets.isEmpty())it.remove();
        }
        burns.entrySet().removeIf(e->System.currentTimeMillis()>=e.getValue().until);
    }
    private void damage(Player owner,LivingEntity target) {
        hitting=owner;victim=target;accepted=false;
        try {
            // Real attributed damage: native armor, invulnerability, friendly-fire and protection listeners own the result.
            target.damage(settings.damage(),owner);
            if(accepted&&settings.burnTicks()>0) {
                var event=new EntityCombustByEntityEvent(owner,target,settings.burnTicks()/20f);
                Bukkit.getPluginManager().callEvent(event);
                if(!event.isCancelled()) {
                    int ticks=Math.max(0,(int)(event.getDuration()*20));
                    target.setFireTicks(Math.max(target.getFireTicks(),ticks));
                    burns.put(target.getUniqueId(),new Burn(owner.getUniqueId(),owner.getWorld(),System.currentTimeMillis()+ticks*50L));
                }
            }
        } finally { hitting=null;victim=null; }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void damageResult(EntityDamageByEntityEvent e) {
        if(hitting!=null&&e.getDamager()==hitting&&e.getEntity()==victim&&e.getFinalDamage()>0)accepted=true;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void burnDamage(EntityDamageEvent e) {
        if(e.getCause()!=EntityDamageEvent.DamageCause.FIRE_TICK||e.getDamageSource().getCausingEntity()!=null)return;
        var burn=burns.get(e.getEntity().getUniqueId());if(burn==null)return;
        var owner=Bukkit.getPlayer(burn.owner);
        // No delayed environmental damage after a victim/attacker enters protection or the source leaves.
        if(owner==null||owner.getWorld()!=burn.world||e.getEntity().getWorld()!=burn.world
                ||!allowed.test(owner.getLocation())||!allowed.test(e.getEntity().getLocation())) {
            e.setCancelled(true);e.getEntity().setFireTicks(0);burns.remove(e.getEntity().getUniqueId());
        } else if(e.getEntity() instanceof LivingEntity target) {
            // Replace only this owned environmental tick with real attributed fire damage. Native PvP/team,
            // armor and protection checks therefore still run if relationships change after ignition.
            e.setCancelled(true);
            target.damage(e.getDamage(),org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.ON_FIRE)
                    .withCausingEntity(owner).withDirectEntity(owner).build());
        }
    }
    public void clear(Player p) {
        bursts.remove(p.getUniqueId());
        burns.entrySet().removeIf(e->{
            if(!e.getValue().owner.equals(p.getUniqueId())&&!e.getKey().equals(p.getUniqueId()))return false;
            var entity=Bukkit.getEntity(e.getKey());if(entity!=null)entity.setFireTicks(0);return true;
        });
    }
    public int activeBursts() { return bursts.size(); }
    @Override public void close() {
        closed=true;task.cancel();bursts.clear();
        burns.keySet().forEach(id->{var e=Bukkit.getEntity(id);if(e!=null)e.setFireTicks(0);});burns.clear();HandlerList.unregisterAll(this);
    }
}
