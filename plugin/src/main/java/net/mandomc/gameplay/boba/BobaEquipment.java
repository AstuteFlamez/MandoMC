package net.mandomc.gameplay.boba;

import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.gameplay.items.ItemCatalog;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Offhand equipment owns no extra item storage. Chest armor and native death/drop ownership are untouched. */
public final class BobaEquipment implements Listener, AutoCloseable {
    public static final String JETPACK="boba_jetpack", FLAMETHROWER="boba_flamethrower";
    private static final NamespacedKey CREDIT=new NamespacedKey("mandomc","jetpack_credit_nanos");
    private final JavaPlugin plugin;
    private final ItemCatalog items;
    private final BobaEquipmentSettings settings;
    private final ConfigurationSection language;
    private final LongSupplier clock;
    private final net.mandomc.integrations.models.PlayerBackAttachment backAttachment = new net.mandomc.integrations.models.PlayerBackAttachment();
    private final Map<UUID,Long> actionBarReservations=new HashMap<>();
    private final Map<UUID,Wearer> wearers=new HashMap<>();
    private final BukkitTask task;
    private final PlayerFlamethrower flames;
    private boolean closed;
    private static final class Wearer {
        final Player player; final ThrustFuel fuel; final ItemDisplay display;
        boolean powered; long last; int launchEnd, sneak=-100;
        Wearer(Player player,ThrustFuel fuel,ItemDisplay display) { this.player=player;this.fuel=fuel;this.display=display; }
    }
    public BobaEquipment(JavaPlugin plugin,ItemCatalog items,BobaEquipmentSettings settings,
            ConfigurationSection language,Predicate<Location> allowed) {
        this(plugin,items,settings,language,allowed,System::nanoTime);
    }
    public BobaEquipment(JavaPlugin plugin,ItemCatalog items,BobaEquipmentSettings settings,
            ConfigurationSection language,Predicate<Location> allowed,LongSupplier clock) {
        this.plugin=plugin;this.items=items;this.settings=settings;this.language=Objects.requireNonNull(language);this.clock=clock;
        for(String key:List.of("fuel","warning","depleted","no-fuel","started","stopped","equip","flight-denied",
                "no-ammo","cooldown","flame-denied"))
            if(!language.isString(key)) throw new IllegalArgumentException("messages.yml: missing boba-equipment."+key);
        flames=new PlayerFlamethrower(plugin,items,settings,allowed,this::message);
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        task=plugin.getServer().getScheduler().runTaskTimer(plugin,this::tick,1,1);
    }
    public PlayerFlamethrower flames() { return flames; }
    public boolean equipped(Player player) { return JETPACK.equals(items.identify(player.getInventory().getItemInOffHand())); }
    public boolean powered(Player player) { var w=wearers.get(player.getUniqueId());return w!=null&&w.powered; }
    /** Reload/UI integrations must yield while powered or while a critical fuel notice is reserved. */
    public boolean ownsActionBar(Player player) {
        return powered(player)||clock.getAsLong()<actionBarReservations.getOrDefault(player.getUniqueId(),0L);
    }
    public ItemDisplay attachment(Player player) { var w=wearers.get(player.getUniqueId());return w==null?null:w.display; }
    public double seconds(Player player) {
        var w=wearers.get(player.getUniqueId());
        return (w==null?new ThrustFuel(player.getPersistentDataContainer().getOrDefault(CREDIT,PersistentDataType.LONG,0L)):w.fuel)
                .seconds(BobaInventory.coal(player.getInventory()));
    }
    private void message(Player player,String key) {
        if(Set.of("warning","depleted","no-fuel").contains(key))
            actionBarReservations.put(player.getUniqueId(),clock.getAsLong()+3_000_000_000L);
        player.sendActionBar(MiniMessage.miniMessage().deserialize(language.getString(key),
                Placeholder.unparsed("seconds",String.format(Locale.ROOT,"%.1f",seconds(player)))));
    }
    private Wearer wear(Player player) {
        return wearers.computeIfAbsent(player.getUniqueId(),id->{
            var stack=player.getInventory().getItemInOffHand();
            // Paper 26.1.2 canGlideUsing checks GLIDER + matching EQUIPPABLE in every native equipment slot.
            var packMeta=stack.getItemMeta();packMeta.lore(items.create(JETPACK).getItemMeta().lore());stack.setItemMeta(packMeta);
            stack.setData(DataComponentTypes.GLIDER);
            stack.setData(DataComponentTypes.EQUIPPABLE,Equippable.equippable(EquipmentSlot.OFF_HAND)
                    .swappable(false).damageOnHurt(false));
            player.getInventory().setItemInOffHand(stack);
            var model=new org.bukkit.inventory.ItemStack(Material.ECHO_SHARD);
            var meta=model.getItemMeta();meta.setItemModel(new NamespacedKey("mandomc","equipment/boba_jetpack_attached"));model.setItemMeta(meta);
            var display=player.getWorld().spawn(player.getLocation(),ItemDisplay.class,e->{
                e.setItemStack(model);e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                e.setPersistent(false);e.setInvulnerable(true);e.setGravity(false);e.setTeleportDuration(0);
                e.setInterpolationDuration(1);e.setViewRange(1);e.setShadowRadius(0);
            });
            if(!player.addPassenger(display)) {
                display.remove();throw new IllegalStateException("Could not attach jetpack display to player "+player.getUniqueId());
            }
            return new Wearer(player,new ThrustFuel(player.getPersistentDataContainer().getOrDefault(CREDIT,PersistentDataType.LONG,0L)),display);
        });
    }
    public boolean toggle(Player player) {
        if(closed||!equipped(player)) { message(player,"equip");return false; }
        var w=wear(player);
        if(w.powered) { settle(w,clock.getAsLong());stop(w,"stopped");return true; }
        if(!eligible(player)||player.getGameMode()==GameMode.CREATIVE||player.isFlying()) {
            message(player,"flight-denied");return false;
        }
        if(!w.fuel.start(()->BobaInventory.coalUnit(player.getInventory()))) { message(player,"no-fuel");return false; }
        boolean takeoff=player.isOnGround();
        w.powered=true;w.last=clock.getAsLong();
        w.launchEnd=Bukkit.getCurrentTick()+(takeoff?settings.launchTicks():0);
        // Only ground takeoff buys a launch impulse. Midair toggles cannot repeatedly mint upward boosts.
        if(takeoff)player.setVelocity(player.getVelocity().setY(settings.launch()));
        else if(!player.isGliding())player.setGliding(true);
        player.getWorld().playSound(player.getLocation(),"mandomc:mobs.jetpack",1,.9f);
        message(player,"started");warn(w);persist(w);
        return true;
    }
    private boolean eligible(Player p) {
        return p.isOnline()&&!p.isDead()&&!p.isInsideVehicle()&&!p.isInWater()&&!p.isSwimming()
                &&!p.hasPotionEffect(org.bukkit.potion.PotionEffectType.LEVITATION)
                &&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE);
    }
    private boolean settle(Wearer w,long now) {
        if(!w.powered)return false;
        long elapsed=Math.max(0,now-w.last);w.last=now;
        if(!w.fuel.spend(elapsed,()->BobaInventory.coalUnit(w.player.getInventory()))) { stop(w,"depleted");return false; }
        return true;
    }
    private void persist(Wearer w) { w.player.getPersistentDataContainer().set(CREDIT,PersistentDataType.LONG,w.fuel.credit()); }
    private void warn(Wearer w) {
        if(w.fuel.warning(BobaInventory.coal(w.player.getInventory()))) {
            message(w.player,"warning");w.player.sendMessage(MiniMessage.miniMessage().deserialize(language.getString("warning")));
            w.player.playSound(w.player.getLocation(),Sound.BLOCK_NOTE_BLOCK_BELL,1,1.8f);
        }
    }
    private void stop(Wearer w,String reason) {
        if(!w.powered)return;
        w.powered=false;persist(w);message(w.player,reason);
        w.player.getWorld().playSound(w.player.getLocation(),Sound.BLOCK_FIRE_EXTINGUISH,.7f,.8f);
        // Leave native glide running on exhaustion/voluntary shutdown, while the item remains equipped.
    }
    private void tick() {
        if(closed)return;
        actionBarReservations.entrySet().removeIf(e->clock.getAsLong()>=e.getValue());
        for(var player:plugin.getServer().getOnlinePlayers())if(equipped(player)&&!player.isDead())wear(player);
        for(var it=wearers.values().iterator();it.hasNext();) {
            var w=it.next();var p=w.player;
            if(!equipped(p)||!p.isOnline()||p.isDead()||!w.display.isValid()||w.display.getWorld()!=p.getWorld()||w.display.getVehicle()!=p) {
                remove(w);it.remove();continue;
            }
            if(w.powered) {
                if(!eligible(p)) { settle(w,clock.getAsLong());stop(w,"stopped"); }
                else if(settle(w,clock.getAsLong())) {
                    if(w.fuel.credit()==0&&!w.fuel.start(()->BobaInventory.coalUnit(p.getInventory()))) stop(w,"depleted");
                    if(w.powered) {
                        if(Bukkit.getCurrentTick()>=w.launchEnd&&p.isOnGround())stop(w,"stopped");
                        if(w.powered&&Bukkit.getCurrentTick()>=w.launchEnd) {
                            if(!p.isGliding()&&!p.isOnGround())p.setGliding(true);
                            var desired=p.getEyeLocation().getDirection().multiply(settings.speed());
                            var velocity=p.getVelocity().multiply(1-settings.steering()).add(desired.multiply(settings.steering()));
                            if(velocity.lengthSquared()>4)velocity.normalize().multiply(2);
                            p.setVelocity(velocity);
                        }
                        if(Bukkit.getCurrentTick()%4==0) {
                            var exhaustPose=backAttachment.pose(p);
                            var exhaust=new Vector3f(0,-1,0).rotate(exhaustPose.modelRotation());
                            for(float x:new float[]{-.375f,.375f}) {
                                var nozzle=new Vector3f(x,-.25f,.375f)
                                        .mul((float)p.getAttribute(org.bukkit.attribute.Attribute.SCALE).getValue())
                                        .rotate(exhaustPose.modelRotation());
                                var at=exhaustPose.center().clone().add(nozzle.x,nozzle.y,nozzle.z);
                                p.getWorld().spawnParticle(Particle.FLAME,at,0,exhaust.x,exhaust.y,exhaust.z,.12);
                                p.getWorld().spawnParticle(Particle.LARGE_SMOKE,at,1,.04,.04,.04,0);
                            }
                        }
                        if(Bukkit.getCurrentTick()%20==0)p.getWorld().playSound(p.getLocation(),Sound.BLOCK_FIRE_AMBIENT,.4f,1.6f);
                        if(Bukkit.getCurrentTick()%4==0)message(p,"fuel");
                        warn(w);persist(w);
                    }
                }
            }
            var pose=backAttachment.pose(p);
            // Native passenger movement follows the same interpolated player position on every client frame.
            // Only the torso-local pose changes; no per-tick teleports or delayed absolute movement packets.
            w.display.setRotation(0,0);
            var transform=new Transformation(backAttachment.translation(p,w.display,pose.center()),
                    pose.rotation(),new Vector3f((float)p.getAttribute(org.bukkit.attribute.Attribute.SCALE).getValue()),new Quaternionf());
            if(!transform.equals(w.display.getTransformation())) {
                w.display.setTransformation(transform);w.display.setInterpolationDelay(0);
            }
        }
    }
    private void remove(Wearer w) {
        if(w.powered) { settle(w,clock.getAsLong());stop(w,"stopped"); }
        persist(w);w.display.remove();
        if(w.player.isGliding()&&!equipped(w.player))w.player.setGliding(false);
    }
    public void detach(Player player) {
        var w=wearers.remove(player.getUniqueId());if(w!=null)remove(w);
        flames.clear(player);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void sneak(PlayerToggleSneakEvent event) {
        if(!event.isSneaking()||!equipped(event.getPlayer()))return;
        var w=wear(event.getPlayer());int now=Bukkit.getCurrentTick();
        if(now-w.sneak<=settings.doubleSneakTicks()) { w.sneak=-100;toggle(event.getPlayer()); }
        else w.sneak=now;
    }
    @EventHandler public void quit(PlayerQuitEvent event) { detach(event.getPlayer()); }
    @EventHandler public void death(PlayerDeathEvent event) { detach(event.getEntity()); }
    @EventHandler public void world(PlayerChangedWorldEvent event) { detach(event.getPlayer()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void teleport(PlayerTeleportEvent event) { detach(event.getPlayer()); }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void mode(PlayerGameModeChangeEvent event) { detach(event.getPlayer()); }
    @Override public void close() {
        if(closed)return;closed=true;task.cancel();
        wearers.values().forEach(w->{remove(w);if(w.player.isGliding())w.player.setGliding(false);});wearers.clear();
        flames.close();actionBarReservations.clear();HandlerList.unregisterAll(this);
    }
}
