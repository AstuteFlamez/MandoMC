package net.mandomc.gameplay.saber;

import java.util.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.mandomc.gameplay.items.ItemCatalog;
import net.mandomc.integrations.regions.SaberProtection;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.bukkit.util.BoundingBox;
import java.util.concurrent.ThreadLocalRandom;
import io.papermc.paper.event.entity.EntityKnockbackEvent;
import io.papermc.paper.event.entity.EntityPushedByEntityAttackEvent;

/** One scheduler owns throws and regenerating charge, displayed as native saber durability. */
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
    private final NamespacedKey storedItem;
    private final NamespacedKey storedSlot;
    private HitImpact impact;
    private static final class HitImpact {
        final UUID owner;
        final UUID victim;
        final Vector direction;
        boolean accepted;
        HitImpact(Player owner, LivingEntity victim, Vector direction) {
            this.owner = owner.getUniqueId();
            this.victim = victim.getUniqueId();
            this.direction = direction.clone().setY(0);
        }
    }
    private int tick;
    private static final class Flight {
        final Player owner;
        final int slot;
        final ItemStack item;
        final ItemDisplay display;
        final SaberArc arc;
        final SaberContacts contacts = new SaberContacts();
        final Location origin;
        final Vector targetAnchor;
        final Vector targetOffset = new Vector();
        final SaberArc.Style style;
        final SaberMotion motion;
        SaberBlade.Pose blade;
        Mob target;
        Location location;
        int age;
        boolean recalling;

        Flight(Player player, ItemDisplay display, Mob target, SaberSettings settings) {
            this.owner = player;
            this.slot = player.getInventory().getHeldItemSlot();
            this.item = player.getInventory().getItem(slot).clone();
            this.display = display;
            this.target = target;
            this.origin = display.getLocation();
            this.location = origin.clone();
            this.targetAnchor = target == null ? origin.toVector() : body(target).toVector();
            Vector direction = target == null ? player.getEyeLocation().getDirection()
                    : targetAnchor.clone().subtract(origin.toVector()).normalize();
            Vector right = new Vector(-direction.getZ(), 0, direction.getX());
            if (right.lengthSquared() < 1e-6) {
                double yaw = Math.toRadians(player.getLocation().getYaw());
                right = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
            }
            double distance = target == null ? 0 : targetAnchor.distance(origin.toVector());
            double reach = target == null ? settings.range() : Math.min(settings.range(), distance + 1.5);
            this.style = SaberArc.Style.sample(settings, ThreadLocalRandom.current());
            this.arc = new SaberArc(origin.toVector(), direction, right, reach, distance, style);
            this.motion = new SaberMotion(arc, style, settings, origin.toVector(), player.getEyeLocation().getYaw());
            this.blade = motion.initialPose(origin.toVector());
        }
    }
    public SaberService(JavaPlugin plugin,ItemCatalog catalog,SaberSettings settings,ConfigurationSection messages) {
        this.plugin=plugin;this.catalog=catalog;this.settings=settings;this.messages=messages;
        if(!isSaber(catalog.create(settings.itemId()))) throw new IllegalArgumentException("Configured saber must be a registered SHIELD");
        for (String id : settings.additionalItemIds()) {
            if (!isSaber(catalog.create(id))) throw new IllegalArgumentException("Additional saber must be a registered SHIELD: " + id);
        }
        storedItem = new NamespacedKey(plugin, "saber_flight_item");
        storedSlot = new NamespacedKey(plugin, "saber_flight_slot");
        plugin.getServer().getPluginManager().registerEvents(this,plugin);
        task=plugin.getServer().getScheduler().runTaskTimer(plugin,this::tick,1,1);
    }
    public SaberSettings settings() { return settings; }
    public boolean isSaber(ItemStack item) {
        if (item == null || item.getType() != Material.SHIELD) return false;
        String id = catalog.identify(item);
        return id != null && (settings.itemId().equals(id) || settings.additionalItemIds().contains(id));
    }
    public boolean allowed(Location location) { return protection.allowed(location); }
    public boolean busy(Player p) { return flights.containsKey(p.getUniqueId()); }
    private GuardState guard(Player p) { return guards.computeIfAbsent(p.getUniqueId(),id->new GuardState()); }
    public boolean canReflect(Player p,Vector incoming) {
        if(!isSaber(p.getInventory().getItemInMainHand()) || !p.isBlocking() || p.getActiveItemHand()!=EquipmentSlot.HAND || !isSaber(p.getActiveItem()) || busy(p) || guard(p).disabled(tick)
                || !allowed(p.getLocation()) || incoming.lengthSquared()<1e-8) return false;
        return GuardState.facing(p.getEyeLocation().getDirection().dot(incoming.clone().normalize().multiply(-1)),settings.halfAngle());
    }
    public void reflected(Player p, double damage) { consumeGuard(p, damage); sound(p.getLocation(),"hit"); }
    private void consumeGuard(Player p, double damage) {
        if(guard(p).hit(tick,settings,damage)) stun(p);
        syncDurability(p);
    }
    private void stun(Player player) {
        player.clearActiveItem();
        player.setCooldown(Material.SHIELD, guard(player).stunTicks(tick));
        finish(player.getUniqueId());
        message(player, "tired-use");
        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.7f, 1.4f);
    }
    private void updateGuard(UUID owner, GuardState state) {
        if (!state.cool(tick, settings)) return;
        Flight flight = flights.get(owner);
        Player player = flight == null ? Bukkit.getPlayer(owner) : flight.owner;
        if (player != null) stun(player);
    }
    private void syncDurability(Player player) {
        GuardState state = guards.get(player.getUniqueId());
        double used = state == null ? 0 : state.heat() / settings.capacity();
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (durability(item, used)) inventory.setItem(slot, item);
        }
        Flight flight = flights.get(player.getUniqueId());
        if (flight != null && durability(flight.item, used)) {
            flight.display.setItemStack(flight.item);
            player.getPersistentDataContainer().set(storedItem, PersistentDataType.BYTE_ARRAY,
                    flight.item.serializeAsBytes());
        }
    }
    private boolean durability(ItemStack item, double used) {
        if (!isSaber(item) || !(item.getItemMeta() instanceof Damageable meta)) return false;
        int maximum = meta.hasMaxDamage() ? meta.getMaxDamage() : item.getType().getMaxDurability();
        // Keep the actual item intact even when its displayed charge reaches empty.
        int damage = Math.clamp((int)Math.round(used * maximum), 0, maximum - 1);
        if (meta.getDamage() == damage) return false;
        meta.setDamage(damage);
        item.setItemMeta(meta);
        return true;
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void wear(PlayerItemDamageEvent event) {
        if (isSaber(event.getItem())) event.setCancelled(true);
    }
    private void message(Player p,String key) {
        p.sendActionBar(MiniMessage.miniMessage().deserialize(Objects.requireNonNull(messages.getString(key))));
    }
    private void sound(Location l,String name) { l.getWorld().playSound(l,"mandomc:saber."+name,SoundCategory.PLAYERS,0.7f,1f); }
    private void restoreModel(ItemStack item) {
        var template = catalog.create(catalog.identify(item));
        if (template != null) model(item, template.getItemMeta().getItemModel().toString());
    }
    private void model(ItemStack i,String key) { if(i==null)return;var m=i.getItemMeta();m.setItemModel(NamespacedKey.fromString(key));i.setItemMeta(m); }

    @EventHandler public void swing(PlayerAnimationEvent e) {
        Player p = e.getPlayer();
        if (busy(p)) {e.setCancelled(true);return;}
        if (!isSaber(p.getInventory().getItemInMainHand())) return;
        if (guard(p).disabled(tick)) {e.setCancelled(true);message(p,"tired-attack");return;}
        sound(p.getLocation(),"swing-1");
    }
    @EventHandler public void interact(PlayerInteractEvent e) {
        if(!isSaber(e.getItem())) return;
        if(e.getHand()!=EquipmentSlot.HAND) {e.setUseItemInHand(Event.Result.DENY);return;}
        var p=e.getPlayer();
        if (guard(p).disabled(tick)) {
            e.setCancelled(true);p.clearActiveItem();
            boolean attack = e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK;
            message(p, attack ? (p.isSneaking() ? "tired-throw" : "tired-attack") : "tired-block");
            return;
        }
        if(e.getAction()==Action.LEFT_CLICK_AIR || e.getAction()==Action.LEFT_CLICK_BLOCK) {
            if(p.isSneaking()) { e.setCancelled(true);launch(p); }
        } else if(busy(p)) {
            e.setUseItemInHand(Event.Result.DENY);p.clearActiveItem();message(p,"in-flight");
        }
    }
    @EventHandler(ignoreCancelled=true) public void mine(BlockDamageEvent e) {
        if (isSaber(e.getItemInHand()) && guard(e.getPlayer()).disabled(tick)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled=true) public void breakBlock(BlockBreakEvent e) {
        if (isSaber(e.getPlayer().getInventory().getItemInMainHand()) && guard(e.getPlayer()).disabled(tick)) e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void attack(EntityDamageByEntityEvent e) {
        if(!(e.getDamager() instanceof Player p) || throwDamage.contains(p.getUniqueId())) return;
        if(busy(p)) {e.setCancelled(true);message(p,"in-flight");return;}
        if(!isSaber(p.getInventory().getItemInMainHand())) return;
        if(p.isSneaking()) {e.setCancelled(true);launch(p);return;}
        if(busy(p)||guard(p).disabled(tick)||!allowed(p.getLocation())||!allowed(e.getEntity().getLocation())) {
            e.setCancelled(true);message(p,busy(p)?"in-flight":guard(p).disabled(tick)?"tired-attack":"safe-zone");
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void damaged(EntityDamageByEntityEvent e) {
        if (e.getFinalDamage() > 0) {
            if (e.getDamager() instanceof Player p) activity(p);
            else if (e.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player p)
                activity(p);
        }
        if (e.getDamager() instanceof Player p && e.getFinalDamage() > 0) {
            if (impact != null && impact.owner.equals(p.getUniqueId()) && impact.victim.equals(e.getEntity().getUniqueId())) {
                impact.accepted = true;
            } else if (isSaber(p.getInventory().getItemInMainHand())) sound(e.getEntity().getLocation(), "hit");
        }
        if(e.getEntity() instanceof Player p && isSaber(p.getInventory().getItemInMainHand())
                && e.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING) && e.getDamage(EntityDamageEvent.DamageModifier.BLOCKING)<0)
            consumeGuard(p, -e.getDamage(EntityDamageEvent.DamageModifier.BLOCKING));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void hurt(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getFinalDamage() > 0) activity(player);
    }
    private void activity(Player player) {
        GuardState state = guards.get(player.getUniqueId());
        if (state != null) state.activity(tick);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void knockback(EntityKnockbackEvent event) {
        if (impact == null || !impact.accepted || !impact.victim.equals(event.getEntity().getUniqueId())
                || impact.direction.lengthSquared() < 1e-6) return;
        if (event instanceof EntityPushedByEntityAttackEvent byEntity
                && !impact.owner.equals(byEntity.getPushedBy().getUniqueId())) return;
        Vector nativePush = event.getKnockback();
        double force = Math.hypot(nativePush.getX(), nativePush.getZ());
        // Preserve native resistance/vertical response and the cancellable knockback event.
        Vector push = impact.direction.clone().normalize().multiply(force).setY(nativePush.getY());
        event.setKnockback(push);
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void projectile(EntityDamageByEntityEvent e) {
        // Wait for native speed/critical damage calculation rather than estimating at collision time.
        if (!(e.getEntity() instanceof Player p) || !(e.getDamager() instanceof AbstractArrow projectile)
                || !canReflect(p, projectile.getVelocity())) return;
        if(projectile.getShooter() instanceof Entity shooter && !allowed(shooter.getLocation())) {e.setCancelled(true);projectile.remove();return;}
        int count=projectile.getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin,"reflections"),org.bukkit.persistence.PersistentDataType.INTEGER,0);
        e.setCancelled(true);reflected(p, e.getDamage());
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
        if(!guard(p).canThrow(tick, settings)) {message(p,"tired-throw");return false;}
        if(cooldowns.getOrDefault(p.getUniqueId(),0)>tick) {message(p,"tired-throw");return false;}
        ItemStack real=p.getInventory().getItemInMainHand();
        var display=p.getWorld().spawn(p.getEyeLocation(),ItemDisplay.class,d->{
            d.setPersistent(false);d.setInvulnerable(true);d.setGravity(false);
            d.setItemStack(real.clone());d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTeleportDuration(1);d.setViewRange(0.5f);
            d.setInterpolationDuration(1);d.setInterpolationDelay(0);d.setRotation(0,0);
        });
        var flight=new Flight(p,display,findTarget(p),settings);flights.put(p.getUniqueId(),flight);
        display.setTransformation(flight.blade.transformation());
        reserve(flight);p.clearActiveItem();
        guard(p).thrown(settings);
        cooldowns.put(p.getUniqueId(),tick+settings.cooldown());sound(p.getLocation(),"throw");return true;
    }
    private void tick() {
        tick++;
        guards.forEach(this::updateGuard);
        guards.entrySet().removeIf(e->!e.getValue().recovering()&&!e.getValue().disabled(tick));
        cooldowns.entrySet().removeIf(e->e.getValue()<=tick);
        for(Player p:Bukkit.getOnlinePlayers()) {
            GuardState state = guards.get(p.getUniqueId());
            if (state != null && state.disabled(tick)) {
                if (isSaber(p.getActiveItem())) p.clearActiveItem();
                if (p.getCooldown(Material.SHIELD) < state.stunTicks(tick))
                    p.setCooldown(Material.SHIELD, state.stunTicks(tick));
            }
            boolean held=isSaber(p.getInventory().getItemInMainHand()) || busy(p);
            if(held && selected.add(p.getUniqueId())) sound(p.getLocation(),"on");
            else if(!held && selected.remove(p.getUniqueId())) sound(p.getLocation(),"off");
            syncDurability(p);
        }
        for(var f:new ArrayList<>(flights.values())) {
            syncDurability(f.owner);
            try {step(f);} catch(RuntimeException error) {plugin.getLogger().warning("Saber throw stopped: "+error.getMessage());finish(f.owner.getUniqueId());}
        }
    }
    private static Location body(LivingEntity entity) {
        return entity.getBoundingBox().getCenter().toLocation(entity.getWorld());
    }

    private Mob findTarget(Player player) {
        Location eye = player.getEyeLocation();
        Vector aim = eye.getDirection();
        double bestAngle = Math.cos(Math.toRadians(settings.lockAngle()));
        double bestDistance = Double.MAX_VALUE;
        Mob best = null;
        for (Entity entity : eye.getWorld().getNearbyEntities(eye, settings.range(), settings.range(), settings.range())) {
            if (!(entity instanceof Mob mob) || !targetable(mob, player)) continue;
            Location center = body(mob);
            Vector offset = center.toVector().subtract(eye.toVector());
            double distance = offset.length();
            if (distance < 1 || distance > settings.range() - 0.75) continue;
            double angle = aim.dot(offset.multiply(1 / distance));
            if (angle < bestAngle || (Math.abs(angle - bestAngle) < 1e-6 && distance >= bestDistance)) continue;
            if (!clearSight(eye, center)) continue;
            best = mob;
            bestAngle = angle;
            bestDistance = distance;
        }
        return best;
    }

    private boolean targetable(Mob mob, Player owner) {
        return mob.isValid() && !mob.isDead() && !mob.isInvulnerable() && allowed(mob.getLocation())
                && (!(mob instanceof Tameable pet) || !owner.getUniqueId().equals(pet.getOwnerUniqueId()));
    }

    private record PathBlock(int x, int y, int z) {}

    private boolean clearSight(Location start, Location end) {
        return clearSight(start, end, new HashMap<>());
    }

    private boolean clearSight(Location start, Location end, Map<PathBlock, Boolean> permissions) {
        Vector delta = end.toVector().subtract(start.toVector());
        double length = delta.length();
        if (length < 1e-6) return true;
        int samples = Math.max(1, (int) Math.ceil(length / 0.25));
        for (int i = 0; i <= samples; i++) {
            Location point = start.clone().add(delta.clone().multiply((double) i / samples));
            var block = new PathBlock(point.getBlockX(), point.getBlockY(), point.getBlockZ());
            if (!permissions.computeIfAbsent(block, ignored -> loaded(point) && allowed(point))) return false;
        }
        return start.getWorld().rayTraceBlocks(start, delta, length, FluidCollisionMode.NEVER, true) == null;
    }

    private static boolean loaded(Location point) {
        return point.getY() >= point.getWorld().getMinHeight() && point.getY() < point.getWorld().getMaxHeight()
                && point.getWorld().isChunkLoaded(point.getBlockX() >> 4, point.getBlockZ() >> 4);
    }

    private void trackTarget(Flight flight) {
        Mob target = flight.target;
        if (target == null) return;
        Location center = body(target);
        if (!target.getWorld().equals(flight.location.getWorld()) || !targetable(target, flight.owner)
                || center.toVector().distance(flight.targetAnchor) > settings.lockCorrection()
                || center.distance(flight.origin) > settings.range() || !clearSight(flight.location, center)) {
            flight.target = null;
            return;
        }
        Vector correction = center.toVector().subtract(flight.targetAnchor).subtract(flight.targetOffset);
        double length = correction.length();
        if (length > 0.15) correction.multiply(0.15 / length);
        flight.targetOffset.add(correction);
    }

    private void step(Flight flight) {
        Player player = flight.owner;
        if (guard(player).disabled(tick)) {finish(player.getUniqueId());return;}
        if (!player.isOnline() || player.isDead() || !player.getWorld().equals(flight.location.getWorld())
                || !flight.display.isValid() || player.getInventory().getHeldItemSlot() != flight.slot
                || !empty(player.getInventory().getItem(flight.slot)) || ++flight.age > 100
                || !allowed(player.getLocation()) || !allowed(flight.location)) {
            finish(player.getUniqueId());
            return;
        }
        Location hand = player.getEyeLocation();
        if (flight.motion.progress() <= flight.arc.attackTime()) trackTarget(flight);
        else flight.target = null;
        SaberBlade.Pose pose = flight.motion.next(flight.location.toVector(), hand.toVector(),
                flight.targetOffset, flight.recalling);
        Vector motion = pose.center().clone().subtract(flight.location.toVector());
        if (!move(flight, motion, pose)) {
            if (flight.display.isValid()) render(flight);
            return;
        }
        if ((flight.recalling || flight.motion.progress() >= 1) && flight.location.distanceSquared(hand) < 0.36) {
            finish(player.getUniqueId());
            return;
        }
        render(flight);
    }

    private void render(Flight flight) {
        flight.location.setYaw(0);
        flight.location.setPitch(0);
        flight.display.setInterpolationDelay(0);
        flight.display.setTransformation(flight.blade.transformation());
        flight.display.teleport(flight.location);
    }

    private boolean move(Flight flight, Vector motion) {
        return move(flight, motion, new SaberBlade.Pose(flight.location.toVector().add(motion),
                flight.blade.rotation(), flight.blade.halfLength()));
    }

    private boolean move(Flight flight, Vector motion, SaberBlade.Pose destination) {
        SaberBlade.Pose start = flight.blade;
        int steps = SaberBlade.steps(start, destination);
        // Region boundaries use block coordinates; share checks across this one densely sampled move.
        Map<PathBlock, Boolean> permissions = new HashMap<>();
        for (int i = 1; i <= steps; i++) {
            SaberBlade.Pose next = start.interpolate(destination, (double) i / steps);
            Location end = next.center().toLocation(flight.location.getWorld());
            // Check the pivot's travel and the complete visible blade before any contact damage.
            if (!clearSight(flight.location, end, permissions)
                    || !clearSight(next.base().toLocation(end.getWorld()), next.tip().toLocation(end.getWorld()), permissions)
                    || !clearSight(flight.blade.base().toLocation(end.getWorld()), next.base().toLocation(end.getWorld()), permissions)
                    || !clearSight(flight.blade.tip().toLocation(end.getWorld()), next.tip().toLocation(end.getWorld()), permissions)) {
                if (flight.recalling) finish(flight.owner.getUniqueId());
                else flight.recalling = true;
                return false;
            }
            damageContacts(flight, next);
            if (!flights.containsKey(flight.owner.getUniqueId())) return false;
            flight.location = end;
            flight.blade = next;
        }
        return true;
    }

    private void damageContacts(Flight flight, SaberBlade.Pose next) {
        SaberBlade.Pose previous = flight.blade;
        double reach = next.halfLength() + settings.radius();
        BoundingBox sweep = BoundingBox.of(previous.center(), next.center()).expand(reach);
        Set<UUID> atStart = new HashSet<>();
        Set<UUID> atEnd = new HashSet<>();
        List<LivingEntity> hits = new ArrayList<>();
        for (Entity entity : flight.location.getWorld().getNearbyEntities(sweep)) {
            if (!(entity instanceof LivingEntity victim) || victim.getUniqueId().equals(flight.owner.getUniqueId())
                    || victim.isDead() || victim.isInvulnerable() || !allowed(victim.getLocation())) continue;
            BoundingBox bounds = victim.getBoundingBox();
            BoundingBox envelope = bounds.clone().expand(reach);
            // A spinning blade crossing the same body again within one pass is still one contact.
            if (envelope.contains(previous.center())) atStart.add(victim.getUniqueId());
            if (envelope.contains(next.center())) atEnd.add(victim.getUniqueId());
            if (SaberBlade.touches(bounds, previous, settings.radius())
                    || SaberBlade.touches(bounds, next, settings.radius())) hits.add(victim);
        }
        flight.contacts.retain(atStart);
        for (LivingEntity victim : hits) {
            Vector center = body(victim).toVector();
            Vector point = next.closest(center);
            Location contact = point.toLocation(flight.location.getWorld());
            if (!clearSight(contact, body(victim)) || !flight.contacts.enter(victim.getUniqueId())) continue;
            Vector direction = point.clone().subtract(previous.closest(center));
            if (direction.lengthSquared() < 1e-6) direction = next.center().clone().subtract(previous.center());
            HitImpact hit = new HitImpact(flight.owner, victim, direction);
            HitImpact prior = impact;
            int immunity = victim.getNoDamageTicks();
            double lastDamage = victim.getLastDamage();
            impact = hit;
            throwDamage.add(flight.owner.getUniqueId());
            try {
                victim.setNoDamageTicks(0);
                victim.damage(settings.throwDamage(), flight.owner);
            } finally {
                victim.setNoDamageTicks(immunity);
                victim.setLastDamage(lastDamage);
                throwDamage.remove(flight.owner.getUniqueId());
                impact = prior;
            }
            if (hit.accepted) {
                if (flight.target != null && flight.target.getUniqueId().equals(victim.getUniqueId())) flight.target = null;
                sound(contact, "hit");
                contact.getWorld().spawnParticle(Particle.CRIT, contact, 8, .16, .16, .16, .12);
                contact.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, contact, 5, .12, .12, .12, .03);
            }
            if (!flights.containsKey(flight.owner.getUniqueId())) break;
        }
        flight.contacts.retain(atEnd);
    }
    private static boolean empty(ItemStack item) { return item == null || item.getType().isAir(); }

    private void reserve(Flight flight) {
        // Player snapshots save inventory and PDC together: a saved in-flight item can recover after a crash.
        byte[] bytes = flight.item.serializeAsBytes();
        var data = flight.owner.getPersistentDataContainer();
        data.set(storedItem, PersistentDataType.BYTE_ARRAY, bytes);
        data.set(storedSlot, PersistentDataType.INTEGER, flight.slot);
        flight.owner.getInventory().setItem(flight.slot, null);
    }

    private void clearStored(Player player) {
        player.getPersistentDataContainer().remove(storedItem);
        player.getPersistentDataContainer().remove(storedSlot);
    }

    private void returnItem(Player player, ItemStack item, int slot) {
        GuardState state = guards.get(player.getUniqueId());
        durability(item, state == null ? 0 : state.heat() / settings.capacity());
        if (empty(player.getInventory().getItem(slot))) player.getInventory().setItem(slot, item);
        else {
            // A command or another plugin may fill the reserved slot. Never overwrite that item.
            for (ItemStack overflow : player.getInventory().addItem(item).values()) {
                var drop = player.getWorld().dropItem(player.getLocation(), overflow);
                drop.setOwner(player.getUniqueId());
            }
        }
        clearStored(player);
    }

    public void finish(UUID owner) {
        Flight flight = flights.remove(owner);
        if (flight == null) return;
        flight.display.remove();
        returnItem(flight.owner, flight.item, flight.slot);
    }

    private void recoverStored(Player player) {
        if (busy(player)) return;
        var data = player.getPersistentDataContainer();
        byte[] bytes = data.get(storedItem, PersistentDataType.BYTE_ARRAY);
        if (bytes == null) return;
        ItemStack item;
        try { item = ItemStack.deserializeBytes(bytes); }
        catch (RuntimeException error) {
            plugin.getLogger().warning("Cannot recover airborne saber for " + player.getUniqueId() + ": " + error.getMessage());
            return;
        }
        int slot = data.getOrDefault(storedSlot, PersistentDataType.INTEGER, player.getInventory().getHeldItemSlot());
        returnItem(player, item, Math.clamp(slot, 0, 8));
    }

    @EventHandler(ignoreCancelled=true) public void pickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && busy(player)) event.setCancelled(true);
    }
    @EventHandler public void inventory(InventoryClickEvent e) {if(flights.containsKey(e.getWhoClicked().getUniqueId()))e.setCancelled(true);}
    @EventHandler public void drag(InventoryDragEvent e) {if(flights.containsKey(e.getWhoClicked().getUniqueId()))e.setCancelled(true);}
    @EventHandler public void drop(PlayerDropItemEvent e) {if(busy(e.getPlayer()))e.setCancelled(true);}
    @EventHandler public void swap(PlayerSwapHandItemsEvent e) {if(busy(e.getPlayer()))e.setCancelled(true);}
    @EventHandler public void held(PlayerItemHeldEvent e) {finish(e.getPlayer().getUniqueId());}
    @EventHandler public void teleport(PlayerTeleportEvent e) {finish(e.getPlayer().getUniqueId());}
    @EventHandler public void quit(PlayerQuitEvent e) {finish(e.getPlayer().getUniqueId());selected.remove(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.HIGHEST) public void death(PlayerDeathEvent event) {
        Flight flight = flights.remove(event.getEntity().getUniqueId());
        if (flight != null) {
            GuardState state = guards.get(flight.owner.getUniqueId());
            durability(flight.item, state == null ? 0 : state.heat() / settings.capacity());
            flight.display.remove();
            if (event.getKeepInventory()) returnItem(flight.owner, flight.item, flight.slot);
            else {
                event.getDrops().add(flight.item);
                clearStored(flight.owner);
            }
        }
        event.getDrops().stream().filter(this::isSaber).forEach(item -> restoreModel(item));
    }
    @EventHandler public void join(PlayerJoinEvent e) {
        recoverStored(e.getPlayer());
        // Normalize legacy hilt presentation from builds that kept the saber in its slot.
        var contents=e.getPlayer().getInventory().getContents();
        for(ItemStack item:contents) if(isSaber(item))restoreModel(item);
        e.getPlayer().getInventory().setContents(contents);
    }
    public void close() {task.cancel();new ArrayList<>(flights.keySet()).forEach(this::finish);}
}
