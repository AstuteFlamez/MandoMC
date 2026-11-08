package net.mandomc.gameplay.saber;

import java.util.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.gameplay.items.ItemCatalog;
import net.mandomc.integrations.regions.SaberProtection;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** One main-thread scheduler owns bounded throws and guard heat. The real item never leaves its owner. */
public final class SaberService implements Listener {
    private final JavaPlugin plugin;
    private final ItemCatalog catalog;
    private final SaberSettings settings;
    private final ConfigurationSection messages;
    private final SaberProtection protection=new SaberProtection();
    private final Map<UUID,GuardState> guards=new HashMap<>();
    private final Map<UUID,Integer> cooldowns=new HashMap<>();
    private final Map<UUID,Flight> flights=new HashMap<>();
    private final Set<UUID> selected=new HashSet<>();
    private final Set<UUID> throwDamage=new HashSet<>();
    private final BukkitTask task;
    private int tick;
    private static final class Flight {
        final Player owner; final int slot; final ItemStack reserved; final ItemDisplay display;
        final Vector direction; final Set<UUID> hit=new HashSet<>();
        Location location; int age; double distance; boolean returning;
        Flight(Player p,ItemStack i,ItemDisplay d) {
            owner=p;slot=p.getInventory().getHeldItemSlot();reserved=i;display=d;
            location=d.getLocation(); direction=p.getEyeLocation().getDirection().normalize();
        }
    }
    public SaberService(JavaPlugin plugin,ItemCatalog catalog,SaberSettings settings,ConfigurationSection messages) {
        this.plugin=plugin;this.catalog=catalog;this.settings=settings;this.messages=messages;
        if(!isSaber(catalog.create(settings.itemId()))) throw new IllegalArgumentException("Configured saber must be a registered SHIELD");
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        task=plugin.getServer().getScheduler().runTaskTimer(plugin,this::tick,1,1);
    }
    public SaberSettings settings() { return settings; }
    public boolean isSaber(ItemStack item) { return item!=null && item.getType()==Material.SHIELD && settings.itemId().equals(catalog.identify(item)); }
    public boolean allowed(Location location) { return protection.allowed(location); }
    public boolean busy(Player p) { return flights.containsKey(p.getUniqueId()); }
    private GuardState guard(Player p) { return guards.computeIfAbsent(p.getUniqueId(),id->new GuardState()); }
    public boolean canReflect(Player p,Vector incoming) {
        if(!isSaber(p.getInventory().getItemInMainHand()) || !p.isBlocking() || p.getActiveItemHand()!=EquipmentSlot.HAND || !isSaber(p.getActiveItem()) || busy(p) || guard(p).disabled(tick)
                || !allowed(p.getLocation()) || incoming.lengthSquared()<1e-8) return false;
        return GuardState.facing(p.getEyeLocation().getDirection().dot(incoming.clone().normalize().multiply(-1)),settings.halfAngle());
    }
    public void reflected(Player p) { consumeGuard(p); sound(p.getLocation(),"hit"); }
    private void consumeGuard(Player p) {
        if(guard(p).hit(tick,settings)) {
            p.clearActiveItem();p.setCooldown(Material.SHIELD,settings.overheat());message(p,"overheated");
            p.playSound(p.getLocation(),Sound.BLOCK_FIRE_EXTINGUISH,0.7f,1.4f);
        }
    }
    private void message(Player p,String key) {
        p.sendActionBar(MiniMessage.miniMessage().deserialize(Objects.requireNonNull(messages.getString(key))));
    }
    private void sound(Location l,String name) { l.getWorld().playSound(l,"mandomc:saber."+name,SoundCategory.PLAYERS,0.7f,1f); }
    private void model(ItemStack i,String key) { if(i==null)return;var m=i.getItemMeta();m.setItemModel(NamespacedKey.fromString(key));i.setItemMeta(m); }

    @EventHandler public void swing(PlayerAnimationEvent e) {
        if(isSaber(e.getPlayer().getInventory().getItemInMainHand()) && !busy(e.getPlayer()) && !guard(e.getPlayer()).disabled(tick)) sound(e.getPlayer().getLocation(),"swing-1");
    }
    @EventHandler public void interact(PlayerInteractEvent e) {
        if(!isSaber(e.getItem())) return;
        if(e.getHand()!=EquipmentSlot.HAND) {e.setUseItemInHand(Event.Result.DENY);return;}
        var p=e.getPlayer();
        if(e.getAction()==Action.LEFT_CLICK_AIR || e.getAction()==Action.LEFT_CLICK_BLOCK) {
            if(p.isSneaking()) { e.setCancelled(true);launch(p); }
        } else if(busy(p)||guard(p).disabled(tick)) {
            e.setUseItemInHand(Event.Result.DENY);p.clearActiveItem();message(p,busy(p)?"in-flight":"overheated");
        }
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void attack(EntityDamageByEntityEvent e) {
        if(!(e.getDamager() instanceof Player p) || throwDamage.contains(p.getUniqueId()) || !isSaber(p.getInventory().getItemInMainHand())) return;
        if(p.isSneaking()) {e.setCancelled(true);launch(p);return;}
        if(busy(p)||guard(p).disabled(tick)||!allowed(p.getLocation())||!allowed(e.getEntity().getLocation())) {
            e.setCancelled(true);message(p,busy(p)?"in-flight":guard(p).disabled(tick)?"overheated":"safe-zone");
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void damaged(EntityDamageByEntityEvent e) {
        if(e.getDamager() instanceof Player p && isSaber(p.getInventory().getItemInMainHand()) && e.getFinalDamage()>0) sound(e.getEntity().getLocation(),"hit");
        if(e.getEntity() instanceof Player p && isSaber(p.getInventory().getItemInMainHand())
                && e.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING) && e.getDamage(EntityDamageEvent.DamageModifier.BLOCKING)<0) consumeGuard(p);
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void projectile(ProjectileHitEvent e) {
        if(!(e.getHitEntity() instanceof Player p)||!canReflect(p,e.getEntity().getVelocity())) return;
        var projectile=e.getEntity();
        if(!(projectile instanceof AbstractArrow)) return; // Other projectile effects need their own compatibility checks.
        if(projectile.getShooter() instanceof Entity shooter && !allowed(shooter.getLocation())) {e.setCancelled(true);projectile.remove();return;}
        int count=projectile.getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin,"reflections"),org.bukkit.persistence.PersistentDataType.INTEGER,0);
        e.setCancelled(true);reflected(p);
        if(count>=settings.reflectionLimit()) {projectile.remove();return;}
        var motion=p.getEyeLocation().getDirection().multiply(Math.max(0.5,projectile.getVelocity().length()));
        projectile.setShooter(p);
        projectile.getPersistentDataContainer().set(new NamespacedKey(plugin,"reflections"),org.bukkit.persistence.PersistentDataType.INTEGER,count+1);
        plugin.getServer().getScheduler().runTask(plugin,()-> {
            if(projectile.isValid() && p.isOnline()) {
                projectile.teleport(p.getEyeLocation().add(motion.clone().normalize().multiply(0.8)));projectile.setVelocity(motion);
            }
        });
    }
    public boolean launch(Player p) {
        if(!isSaber(p.getInventory().getItemInMainHand())||busy(p)) return false;
        if(!allowed(p.getLocation())) {message(p,"safe-zone");return false;}
        if(guard(p).disabled(tick)) {message(p,"overheated");return false;}
        if(cooldowns.getOrDefault(p.getUniqueId(),0)>tick) {message(p,"throw-cooldown");return false;}
        ItemStack real=p.getInventory().getItemInMainHand();
        var display=p.getWorld().spawn(p.getEyeLocation(),ItemDisplay.class,d->{
            d.setPersistent(false);d.setInvulnerable(true);d.setGravity(false);
            d.setItemStack(real.clone());d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTeleportDuration(1);d.setViewRange(0.5f);
        });
        var flight=new Flight(p,real,display);flights.put(p.getUniqueId(),flight);
        model(real,settings.hilt());p.getInventory().setItem(flight.slot,real);p.clearActiveItem();
        cooldowns.put(p.getUniqueId(),tick+settings.cooldown());sound(p.getLocation(),"throw");return true;
    }
    private void tick() {
        tick++;
        guards.values().forEach(g->g.cool(tick,settings));
        guards.entrySet().removeIf(e->e.getValue().heat()==0&&!e.getValue().disabled(tick));
        cooldowns.entrySet().removeIf(e->e.getValue()<=tick);
        for(Player p:Bukkit.getOnlinePlayers()) {
            boolean held=isSaber(p.getInventory().getItemInMainHand());
            if(held && selected.add(p.getUniqueId())) sound(p.getLocation(),"on");
            else if(!held && selected.remove(p.getUniqueId())) sound(p.getLocation(),"off");
            if(held && tick%5==0 && guards.containsKey(p.getUniqueId())) p.sendActionBar(MiniMessage.miniMessage().deserialize(
                    Objects.requireNonNull(messages.getString("heat")),Placeholder.unparsed("heat",Integer.toString((int)Math.ceil(guard(p).heat()*100/settings.capacity())))));
        }
        for(var f:new ArrayList<>(flights.values())) {
            try {step(f);} catch(RuntimeException error) {plugin.getLogger().warning("Saber throw stopped: "+error.getMessage());finish(f.owner.getUniqueId());}
        }
    }
    private void step(Flight f) {
        Player p=f.owner;
        if(!p.isOnline()||p.isDead()||!p.getWorld().equals(f.location.getWorld())||!f.display.isValid()
                ||p.getInventory().getHeldItemSlot()!=f.slot||!isSaber(p.getInventory().getItem(f.slot))||++f.age>100) {finish(p.getUniqueId());return;}
        Location target=p.getEyeLocation();
        if(f.returning && f.location.distanceSquared(target)<2.25) {finish(p.getUniqueId());return;}
        Vector motion=f.returning?target.toVector().subtract(f.location.toVector()).normalize().multiply(settings.returnSpeed()):f.direction.clone().multiply(settings.speed());
        double length=motion.length();
        if(!f.returning) {
            var wall=f.location.getWorld().rayTraceBlocks(f.location,motion,length,FluidCollisionMode.NEVER,true);
            if(wall!=null) length=Math.min(length,wall.getHitPosition().distance(f.location.toVector()));
            Location end=f.location.clone().add(motion.clone().normalize().multiply(length));
            if(!allowed(p.getLocation()) || !allowed(end) || !allowed(f.location)) {f.returning=true;return;}
            var hit=f.location.getWorld().rayTraceEntities(f.location,motion,length,settings.radius(),
                    e->e instanceof LivingEntity && e!=p && !e.getUniqueId().equals(f.display.getUniqueId()) && !f.hit.contains(e.getUniqueId()));
            if(hit!=null && hit.getHitEntity() instanceof LivingEntity victim && allowed(victim.getLocation())) {
                f.hit.add(victim.getUniqueId());throwDamage.add(p.getUniqueId());
                try {victim.damage(settings.throwDamage(),p);} finally {throwDamage.remove(p.getUniqueId());}
                sound(victim.getLocation(),"hit");
            }
            f.distance+=length;if(wall!=null||f.distance>=settings.range()) f.returning=true;
        }
        f.location.add(motion.clone().normalize().multiply(length));
        f.location.setYaw((f.age*35)%360);f.location.setPitch(90);f.display.teleport(f.location);
    }
    public void finish(UUID owner) {
        Flight f=flights.remove(owner);if(f==null)return;
        f.display.remove();
        // Restore only presentation on the item already owned by the player; never create a replacement.
        ItemStack item=f.owner.getInventory().getItem(f.slot);
        if(isSaber(item)) {model(item,settings.model());f.owner.getInventory().setItem(f.slot,item);}
    }
    @EventHandler public void inventory(InventoryClickEvent e) {if(flights.containsKey(e.getWhoClicked().getUniqueId()))e.setCancelled(true);}
    @EventHandler public void drag(InventoryDragEvent e) {if(flights.containsKey(e.getWhoClicked().getUniqueId()))e.setCancelled(true);}
    @EventHandler public void drop(PlayerDropItemEvent e) {if(busy(e.getPlayer()))e.setCancelled(true);}
    @EventHandler public void swap(PlayerSwapHandItemsEvent e) {if(busy(e.getPlayer()))e.setCancelled(true);}
    @EventHandler public void held(PlayerItemHeldEvent e) {finish(e.getPlayer().getUniqueId());}
    @EventHandler public void teleport(PlayerTeleportEvent e) {finish(e.getPlayer().getUniqueId());}
    @EventHandler public void quit(PlayerQuitEvent e) {finish(e.getPlayer().getUniqueId());selected.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void death(PlayerDeathEvent e) {
        finish(e.getEntity().getUniqueId());e.getDrops().stream().filter(this::isSaber).forEach(i->model(i,settings.model()));
    }
    @EventHandler public void join(PlayerJoinEvent e) {
        // A crash may leave the temporary hilt presentation saved; normalize existing items, never grant another.
        var contents=e.getPlayer().getInventory().getContents();
        for(ItemStack item:contents) if(isSaber(item))model(item,settings.model());
        e.getPlayer().getInventory().setContents(contents);
    }
    public void close() {task.cancel();new ArrayList<>(flights.keySet()).forEach(this::finish);}
}
