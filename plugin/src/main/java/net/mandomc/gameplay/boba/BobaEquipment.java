package net.mandomc.gameplay.boba;

import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.gameplay.items.ItemCatalog;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Native Jetpack enchantment on the worn chestplate enables flight; no display entities or item storage. */
public final class BobaEquipment implements Listener, AutoCloseable {
    public static final String JETPACK="boba_jetpack", FLAMETHROWER="boba_flamethrower";
    private static final NamespacedKey CREDIT=new NamespacedKey("mandomc","jetpack_credit_nanos");
    private final JavaPlugin plugin;
    private final ItemCatalog items;
    private final BobaEquipmentSettings settings;
    private final ConfigurationSection language;
    private final LongSupplier clock;
    private final JetpackChestplate chestplates;
    private final Map<UUID,Long> actionBarReservations=new HashMap<>();
    private final Map<UUID,Wearer> wearers=new HashMap<>();
    private final BukkitTask task;
    private final PlayerFlamethrower flames;

    private boolean closed;
    private static final class Wearer {
        final Player player; final ThrustFuel fuel;
        boolean powered; long last; int launchEnd, sneak=-100;
        Wearer(Player player,ThrustFuel fuel) { this.player=player;this.fuel=fuel; }
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
        chestplates=new JetpackChestplate(plugin,items);
        flames=new PlayerFlamethrower(plugin,items,settings,allowed,this::message);
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        task=plugin.getServer().getScheduler().runTaskTimer(plugin,this::tick,1,1);
    }
    /** Obsolete visual-test compatibility entry; no visual controller is instantiated. */
    @Deprecated public JetpackVisualTrials visualTrials() { return null; }
    public JetpackChestplate chestplates() { return chestplates; }
    public PlayerFlamethrower flames() { return flames; }
    public boolean equipped(Player player) { return chestplates.upgraded(player.getInventory().getChestplate()); }
    public boolean powered(Player player) { var w=wearers.get(player.getUniqueId());return w!=null&&w.powered; }
    /** Reload/UI integrations must yield while powered or while a critical fuel notice is reserved. */
    public boolean ownsActionBar(Player player) {
        return powered(player)||clock.getAsLong()<actionBarReservations.getOrDefault(player.getUniqueId(),0L);
    }
    /** Enchanted chestplates have no visual attachment. */
    public ItemDisplay attachment(Player player) { return null; }
    public double seconds(Player player) {
        var w=wearers.get(player.getUniqueId());
        var fuel=(w==null?new ThrustFuel(player.getPersistentDataContainer().getOrDefault(CREDIT,PersistentDataType.LONG,0L),chestplates.level(player.getInventory().getChestplate())):w.fuel);
        fuel.setLevel(chestplates.level(player.getInventory().getChestplate()));
        return fuel.seconds(BobaInventory.coal(player.getInventory()));
    }
    private void message(Player player,String key) {
        if(Set.of("warning","depleted","no-fuel").contains(key))
            actionBarReservations.put(player.getUniqueId(),clock.getAsLong()+3_000_000_000L);
        player.sendActionBar(MiniMessage.miniMessage().deserialize(language.getString(key),
                Placeholder.unparsed("seconds",String.format(Locale.ROOT,"%.1f",seconds(player)))));
    }
    private Wearer wear(Player player) {
        return wearers.computeIfAbsent(player.getUniqueId(),id->{
            return new Wearer(player,new ThrustFuel(player.getPersistentDataContainer().getOrDefault(CREDIT,PersistentDataType.LONG,0L),chestplates.level(player.getInventory().getChestplate())));
        });
    }
    public boolean toggle(Player player) {
        if(closed||!equipped(player)) { message(player,"equip");return false; }
        var w=wear(player);w.fuel.setLevel(chestplates.level(player.getInventory().getChestplate()));
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
        for(var player:plugin.getServer().getOnlinePlayers()) {
            var inventory=player.getInventory();
            for(int slot=0;slot<inventory.getSize();slot++) {
                var item=inventory.getItem(slot);
                if(item!=null&&chestplates.maintain(item))inventory.setItem(slot,item);
            }
            if(equipped(player)&&!player.isDead())wear(player);
        }
        for(var it=wearers.values().iterator();it.hasNext();) {
            var w=it.next();var p=w.player;
            if(!equipped(p)||!p.isOnline()||p.isDead()) {
                remove(w);it.remove();continue;
            }
            w.fuel.setLevel(chestplates.level(p.getInventory().getChestplate()));
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
                        if(Bukkit.getCurrentTick()%20==0)p.getWorld().playSound(p.getLocation(),Sound.BLOCK_FIRE_AMBIENT,.4f,1.6f);
                        if(Bukkit.getCurrentTick()%4==0)message(p,"fuel");
                        warn(w);persist(w);
                    }
                }
            }

        }
    }
    private void remove(Wearer w) {
        if(w.powered) { settle(w,clock.getAsLong());stop(w,"stopped"); }
        persist(w);
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
        flames.close();chestplates.close();actionBarReservations.clear();HandlerList.unregisterAll(this);
    }
}
