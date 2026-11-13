package net.mandomc.gameplay.jawa;

import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** Refreshing on-foot restraint. Airborne motion is untouched; a grounded move never rewinds a teleport. */
public final class JawaIonization implements Listener,AutoCloseable {
    private record Hold(UUID world,long until) { }
    private final MandoPlugin plugin;
    private final Map<UUID,Hold> holds=new HashMap<>();
    private final Map<UUID,Long> epochs=new HashMap<>();
    private final int duration,count;
    private final Particle.DustOptions dust;
    private final BukkitTask task;
    public JawaIonization(MandoPlugin plugin,ConfigurationSection config) {
        this.plugin=plugin;duration=config.getInt("duration-ticks");count=config.getInt("particle-count");
        var rgb=config.getIntegerList("particle-rgb");double size=config.getDouble("particle-size");
        if(duration<1 || duration>200 || count<1 || count>128 || rgb.size()!=3 || rgb.stream().anyMatch(v->v<0||v>255) || !Double.isFinite(size) || size<=0 || size>4)
            throw new IllegalArgumentException("mobs/jawas.yml: invalid ionization settings");
        dust=new Particle.DustOptions(Color.fromRGB(rgb.get(0),rgb.get(1),rgb.get(2)),(float)size);
        Bukkit.getPluginManager().registerEvents(this,plugin);task=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);
    }
    public long epoch(Player player){return epochs.getOrDefault(player.getUniqueId(),0L);}
    private boolean eligible(Player player){return player.isOnline() && !player.isDead() && !player.isInvulnerable()
            && (player.getGameMode()==GameMode.SURVIVAL || player.getGameMode()==GameMode.ADVENTURE) && plugin.territories().combat(player.getLocation());}
    public void hit(Player player,long hitTick) {
        if(!eligible(player))return;
        player.getWorld().spawnParticle(Particle.DUST,player.getLocation().add(0,player.getHeight()*.55,0),count,.35,.55,.35,0,dust);
        if(!player.isInsideVehicle())holds.put(player.getUniqueId(),new Hold(player.getWorld().getUID(),hitTick+duration));
    }
    public boolean held(Player player) {
        var hold=holds.get(player.getUniqueId());return hold!=null && hold.world().equals(player.getWorld().getUID()) && Bukkit.getCurrentTick()<hold.until();
    }
    public long remaining(Player player){var hold=holds.get(player.getUniqueId());return hold==null?0:Math.max(0,hold.until()-Bukkit.getCurrentTick());}
    public Particle.DustOptions dust(){return dust;}
    private boolean grounded(Player player,Location from) {
        if(player.isOnGround())return true;
        var hit=from.getWorld().rayTraceBlocks(from.clone().add(0,.05,0),new Vector(0,-1,0),.12,FluidCollisionMode.NEVER,true);
        return hit!=null;
    }
    private void tick() {
        for(var id:List.copyOf(holds.keySet())) {
            var player=Bukkit.getPlayer(id);
            if(player==null || !eligible(player) || !held(player) || player.isInsideVehicle()){holds.remove(id);continue;}
            if(grounded(player,player.getLocation())) {
                var velocity=player.getVelocity();player.setVelocity(new Vector(0,Math.min(0,velocity.getY()),0));
            }
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void move(PlayerMoveEvent event) {
        if(event instanceof PlayerTeleportEvent || !event.hasChangedPosition() || !held(event.getPlayer()) || !grounded(event.getPlayer(),event.getFrom()))return;
        var from=event.getFrom().clone();from.setYaw(event.getTo().getYaw());from.setPitch(event.getTo().getPitch());event.setFrom(from);event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void velocity(PlayerVelocityEvent event) {
        if(!held(event.getPlayer()) || !grounded(event.getPlayer(),event.getPlayer().getLocation()))return;
        var v=event.getVelocity();event.setVelocity(new Vector(0,Math.min(0,v.getY()),0));
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void mount(EntityMountEvent event){if(event.getEntity() instanceof Player player && held(player))event.setCancelled(true);}
    private void clear(Player player){holds.remove(player.getUniqueId());epochs.merge(player.getUniqueId(),1L,Long::sum);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent event){clear(event.getPlayer());}
    @EventHandler public void changed(PlayerChangedWorldEvent event){clear(event.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent event){clear(event.getPlayer());}
    @EventHandler public void death(PlayerDeathEvent event){clear(event.getEntity());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void mode(PlayerGameModeChangeEvent event){clear(event.getPlayer());}
    @Override public void close(){task.cancel();HandlerList.unregisterAll(this);holds.clear();epochs.clear();}
}
