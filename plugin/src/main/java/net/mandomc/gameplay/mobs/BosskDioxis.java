package net.mandomc.gameplay.mobs;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.*;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import net.mandomc.commands.RootCommand;

/** Main-thread grenade flight/clouds and player-only crowd control. Never emits explosion or fake damage events. */
public final class BosskDioxis implements Listener, AutoCloseable {
    private record Flight(UUID owner, ItemDisplay display, Location origin, Location destination, int start) { }
    private static final class Cloud {
        final UUID owner; final Location center; final int end;
        Cloud(UUID owner,Location center,int end){this.owner=owner;this.center=center;this.end=end;}
    }
    private record Hold(UUID source, Location anchor, int end) { }
    private record Exposure(int poisonEnd, int immuneUntil) { }
    private final JavaPlugin plugin;
    private final net.mandomc.gameplay.items.ItemCatalog items;
    private final Map<UUID,Integer> playerCooldowns=new HashMap<>();
    private final DioxisSettings settings;
    private final Function<Entity,String> mobType;
    private final Predicate<Location> allowed;
    private final Function<LivingEntity,Location> muzzle;
    private final java.util.function.Consumer<Player> combatTag;
    private final List<Flight> flights=new ArrayList<>();
    private final List<Cloud> clouds=new ArrayList<>();
    private final Map<UUID,Hold> holds=new HashMap<>();
    private final Map<UUID,Exposure> exposures=new HashMap<>();
    private final org.bukkit.scheduler.BukkitTask task;

    public BosskDioxis(JavaPlugin plugin,DioxisSettings settings,Function<Entity,String> mobType,
            Predicate<Location> allowed,java.util.function.Consumer<Player> combatTag,RootCommand root,
            Function<LivingEntity,Location> muzzle, net.mandomc.gameplay.items.ItemCatalog items) {
        this.plugin=plugin;this.items=items;this.settings=settings;this.mobType=mobType;this.allowed=allowed;this.combatTag=combatTag;this.muzzle=muzzle;
        Bukkit.getPluginManager().registerEvents(this,plugin);
        task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);
        root.register("bosskgrenade","mandomc.admin.status",(sender,args)->{
            if(!(sender instanceof ConsoleCommandSender)||args.length!=2)return;
            try {
                Entity owner=Bukkit.getEntity(UUID.fromString(args[0])),target=Bukkit.getEntity(UUID.fromString(args[1]));
                if(owner instanceof LivingEntity caster && target instanceof LivingEntity victim) launch(caster,victim);
            }catch(IllegalArgumentException ignored){ /* Malformed/stale Mythic placeholders are denied without side effects. */ }
        },(sender,args)->List.of());
    }
    public boolean launch(LivingEntity caster,LivingEntity victim) {
        if(!caster.isValid()||caster.isDead()||!"Bossk".equals(mobType.apply(caster))||!victim.isValid()||victim.isDead()
                ||caster.getWorld()!=victim.getWorld()||caster.getLocation().distanceSquared(victim.getLocation())>32*32
                ||!caster.hasLineOfSight(victim)||!allowed.test(caster.getLocation())||!allowed.test(victim.getLocation())
                ||flights.size()+clouds.size()>=32)return false;
        return startFlight(caster,muzzle.apply(caster),victim.getLocation().add(0,0.15,0));
    }
    private boolean startFlight(LivingEntity caster,Location start,Location destination) {
        if(!allowed.test(start)||!allowed.test(destination)||flights.size()+clouds.size()>=32)return false;
        long owned=flights.stream().filter(f->f.owner.equals(caster.getUniqueId())).count()
                +clouds.stream().filter(c->c.owner.equals(caster.getUniqueId())).count();
        if(owned>=2)return false;
        var item=new ItemStack(Material.ECHO_SHARD);var meta=item.getItemMeta();
        meta.setItemModel(new NamespacedKey("mandomc","projectiles/dioxis_grenade"));item.setItemMeta(meta);
        var display=start.getWorld().spawn(start,ItemDisplay.class,e->{e.setItemStack(item);e.setPersistent(false);e.setInvulnerable(true);e.setTeleportDuration(1);
            var transform=e.getTransformation();transform.getScale().set(0.5f);e.setTransformation(transform);});
        flights.add(new Flight(caster.getUniqueId(),display,start,destination,Bukkit.getCurrentTick()));
        start.getWorld().playSound(start,Sound.BLOCK_DISPENSER_LAUNCH,0.9f,0.7f);
        start.getWorld().playSound(start,Sound.ENTITY_FIREWORK_ROCKET_LAUNCH,0.65f,0.55f);return true;
    }
    public boolean launchPlayer(Player player) {
        int now=Bukkit.getCurrentTick();
        if(player.isDead()||player.getGameMode()!=GameMode.SURVIVAL&&player.getGameMode()!=GameMode.ADVENTURE&&player.getGameMode()!=GameMode.CREATIVE
                ||now<playerCooldowns.getOrDefault(player.getUniqueId(),0)||!allowed.test(player.getLocation()))return false;
        var eye=player.getEyeLocation();var direction=eye.getDirection();
        var hit=eye.getWorld().rayTraceBlocks(eye,direction,16,FluidCollisionMode.NEVER,true);
        var destination=hit!=null?hit.getHitPosition().toLocation(eye.getWorld()):eye.clone().add(direction.clone().multiply(16));
        if(hit==null) {
            var ground=destination.clone();ground.setY(player.getY()+2);
            var floor=ground.getWorld().rayTraceBlocks(ground,new Vector(0,-1,0),32,FluidCollisionMode.NEVER,true);
            destination=floor!=null?floor.getHitPosition().toLocation(eye.getWorld()).add(0,0.1,0):ground.add(0,-16,0);
        }
        if(!startFlight(player,eye.clone().add(direction.multiply(0.45)),destination))return false;
        playerCooldowns.put(player.getUniqueId(),now+20);return true;
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void use(org.bukkit.event.player.PlayerInteractEvent e) {
        if(e.getHand()!=org.bukkit.inventory.EquipmentSlot.HAND||!"dioxis_grenade".equals(items.identify(e.getItem())))return;
        if(!e.getAction().isRightClick())return;
        if(e.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_AIR&&e.useItemInHand()==Event.Result.DENY)return;
        e.setCancelled(true);
        if(launchPlayer(e.getPlayer())&&e.getPlayer().getGameMode()!=GameMode.CREATIVE) {
            var stack=e.getPlayer().getInventory().getItemInMainHand();stack.subtract(1);
            e.getPlayer().getInventory().setItemInMainHand(stack);
        }
    }
    private boolean ownerAlive(UUID id){var e=Bukkit.getEntity(id);return e instanceof LivingEntity l&&l.isValid()&&!l.isDead()&&(e instanceof Player p?p.isOnline():"Bossk".equals(mobType.apply(e)));}
    private void tick() {
        int now=Bukkit.getCurrentTick();
        for(var it=flights.iterator();it.hasNext();) {
            var f=it.next();if(!ownerAlive(f.owner)||!f.display.isValid()){f.display.remove();it.remove();continue;}
            double t=Math.min(1,(now-f.start)/(double)settings.flightTicks());
            var next=arc(f.origin,f.destination,t,settings.arcHeight());var last=f.display.getLocation();var delta=next.toVector().subtract(last.toVector());
            var hit=delta.lengthSquared()>0.0001?last.getWorld().rayTraceBlocks(last,delta.clone().normalize(),delta.length(),FluidCollisionMode.NEVER,true):null;
            if(hit!=null)next=hit.getHitPosition().toLocation(last.getWorld());
            if(!allowed.test(next)){f.display.remove();it.remove();continue;}
            next.setYaw((now-f.start)*24);f.display.teleport(next);
            if(t>=1||hit!=null) {
                f.display.remove();it.remove();clouds.add(new Cloud(f.owner,next,now+settings.cloudTicks()));
                next.getWorld().playSound(next,Sound.BLOCK_FIRE_EXTINGUISH,1,0.6f);
            }
        }
        clouds.removeIf(c->now>=c.end||!ownerAlive(c.owner));
        if(now%5==0)for(var c:clouds){
            c.center.getWorld().spawnParticle(Particle.DUST,c.center.clone().add(0,0.8,0),30,settings.radius()*0.65,0.6,settings.radius()*0.65,0,
                    new Particle.DustOptions(Color.fromRGB(80,170,35),1.5f));
            c.center.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,c.center.clone().add(0,0.6,0),6,settings.radius()*0.55,0.25,settings.radius()*0.55,0.005);
            for(Player player:c.center.getWorld().getPlayers())if(!player.getUniqueId().equals(c.owner)&&eligible(player,c.center))expose(c,player,now);
        }
        for(var it=holds.entrySet().iterator();it.hasNext();) {
            var entry=it.next();var p=Bukkit.getPlayer(entry.getKey());var h=entry.getValue();
            if(p==null||!p.isOnline()||p.isDead()||now>=h.end||!ownerAlive(h.source)||!allowed.test(p.getLocation())
                    ||p.getWorld()!=h.anchor.getWorld()||p.getGameMode()!=GameMode.SURVIVAL&&p.getGameMode()!=GameMode.ADVENTURE){it.remove();continue;}
            if(now%settings.fakeHitInterval()==0){p.playHurtAnimation(0);p.getWorld().playSound(p.getLocation(),Sound.ENTITY_PLAYER_HURT,0.3f,1.2f);}
        }
        exposures.entrySet().removeIf(e->now>=Math.max(e.getValue().poisonEnd,e.getValue().immuneUntil));
    }
    public static Location arc(Location origin,Location destination,double t,double height) {
        return origin.clone().add(destination.toVector().subtract(origin.toVector()).multiply(t)).add(0,4*height*t*(1-t),0);
    }
    private boolean eligible(Player p,Location center){
        return !p.isDead()&&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE)
                &&p.getLocation().distanceSquared(center)<=settings.radius()*settings.radius()
                &&Math.abs(p.getY()-center.getY())<=2.5&&allowed.test(p.getLocation())&&allowed.test(center)
                &&p.getWorld().rayTraceBlocks(center.clone().add(0,0.4,0),p.getEyeLocation().toVector().subtract(center.clone().add(0,0.4,0).toVector()).normalize(),
                        Math.max(0.01,p.getEyeLocation().distance(center.clone().add(0,0.4,0))),FluidCollisionMode.NEVER,true)==null;
    }
    private void expose(Cloud c,Player p,int now){
        var before=exposures.get(p.getUniqueId());int immunity=before==null?0:before.immuneUntil;
        if(now>=immunity){holds.put(p.getUniqueId(),new Hold(c.owner,p.getLocation(),now+settings.stunTicks()));immunity=now+settings.immunityTicks();}
        if(now%20==0||before==null){
            if(p.addPotionEffect(new PotionEffect(PotionEffectType.POISON,settings.poisonTicks(),settings.poisonAmplifier(),false,true,true))){
                combatTag.accept(p);var owner=Bukkit.getEntity(c.owner);if(owner instanceof Player attacker)combatTag.accept(attacker);
            }
        }
        exposures.put(p.getUniqueId(),new Exposure(now+settings.poisonTicks(),immunity));
    }
    public boolean stunned(Player p){return holds.containsKey(p.getUniqueId());}
    public int activeFlights(){return flights.size();}
    public int activeClouds(){return clouds.size();}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void move(PlayerMoveEvent e){
        if(e instanceof PlayerTeleportEvent)return;var h=holds.get(e.getPlayer().getUniqueId());
        if(h==null||e.getTo()==null)return;
        if(!e.hasChangedPosition())return;
        var to=e.getTo();var anchor=h.anchor.clone();anchor.setYaw(to.getYaw());anchor.setPitch(to.getPitch());
        // Cancelling a move rewinds without creating a teleport event; setTo would invoke combat travel checks.
        e.setFrom(anchor);e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void poisonDamage(EntityDamageEvent e){
        if(e.getCause()!=EntityDamageEvent.DamageCause.POISON||!(e.getEntity() instanceof Player p)||!exposures.containsKey(p.getUniqueId()))return;
        if(!allowed.test(p.getLocation()))e.setCancelled(true);else combatTag.accept(p);
    }
    @EventHandler public void teleport(PlayerTeleportEvent e){if(!e.isCancelled())holds.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void quit(PlayerQuitEvent e){holds.remove(e.getPlayer().getUniqueId());exposures.remove(e.getPlayer().getUniqueId());playerCooldowns.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void died(org.bukkit.event.entity.PlayerDeathEvent e){holds.remove(e.getEntity().getUniqueId());exposures.remove(e.getEntity().getUniqueId());}
    @Override public void close(){task.cancel();HandlerList.unregisterAll(this);flights.forEach(f->f.display.remove());flights.clear();clouds.clear();holds.clear();exposures.clear();playerCooldowns.clear();}
}
