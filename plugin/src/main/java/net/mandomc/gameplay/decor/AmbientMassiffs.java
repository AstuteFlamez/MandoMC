package net.mandomc.gameplay.decor;

import java.util.*;
import net.mandomc.bootstrap.MandoPlugin;
import net.mandomc.gameplay.territory.TerritoryService;
import net.mandomc.gameplay.territory.TerritoryPolicy;
import net.mandomc.integrations.models.AmbientMassiffModel;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/** Three peaceful, bounded ground navigators. Static guide freezing never runs on these bodies. */
public final class AmbientMassiffs implements Listener, AutoCloseable {
    // Original rig reaches 20 model pixels from its center: reserve two 1.3-block
    // envelopes plus a small margin, rather than using the much smaller wolf body.
    private static final double CLEARANCE = 2.75;
    public static final NamespacedKey MARKER = new NamespacedKey("mandomc", "ambient_massiff_id");
    private static final class Live {
        final String id; final Wolf body; AmbientMassiffModel model;
        Location previous; Location animationPrevious; int lastAnimatedMotion = -100; int nextWalk; int lastProgress; boolean navigating;
        Live(String id, Wolf body, AmbientMassiffModel model) { this.id = id; this.body = body; this.model = model; previous = body.getLocation(); animationPrevious = previous.clone(); lastProgress = Bukkit.getCurrentTick(); }
    }
    private final MandoPlugin plugin;
    private final TerritoryService territories;
    private final World world;
    private final int minX, maxX, minZ, maxZ, y;
    private final List<Location> starts = new ArrayList<>();
    private final List<Location> destinations = new ArrayList<>();
    private final Map<String, Live> live = new LinkedHashMap<>();
    private final Set<Chunk> chunks = new HashSet<>();
    private final BukkitTask task;
    private final BukkitTask animationTask;
    public AmbientMassiffs(MandoPlugin plugin, ConfigurationSection config) {
        this.plugin = plugin;
        territories = Objects.requireNonNull(Bukkit.getServicesManager().load(TerritoryService.class));
        world = Objects.requireNonNull(Bukkit.getWorld(Objects.requireNonNull(NamespacedKey.fromString(config.getString("world", "")))));
        minX = config.getInt("min-x"); maxX = config.getInt("max-x"); minZ = config.getInt("min-z"); maxZ = config.getInt("max-z"); y = config.getInt("y");
        if (minX > maxX || minZ > maxZ || maxX - minX > 24 || maxZ - minZ > 24) throw new IllegalArgumentException("Ambient Massiffs require a bounded spawn footprint");
        for (var row : config.getMapList("starts")) starts.add(new Location(world, ((Number)row.get("x")).doubleValue(), y, ((Number)row.get("z")).doubleValue()));
        if (starts.size() != 3 || starts.stream().anyMatch(p -> !safe(p))) throw new IllegalArgumentException("Exactly three safe Massiff starts required");
        try {
            for (int cx = Math.floorDiv(minX, 16); cx <= Math.floorDiv(maxX, 16); cx++) for (int cz = Math.floorDiv(minZ, 16); cz <= Math.floorDiv(maxZ, 16); cz++) {
                Chunk c = world.getChunkAt(cx, cz); if (c.addPluginChunkTicket(plugin)) chunks.add(c);
            }
            for (World w : Bukkit.getWorlds()) for (Entity e : List.copyOf(w.getEntities())) removeStale(e);
            for (int x = minX + 1; x < maxX; x++) for (int z = minZ + 1; z < maxZ; z++) {
                Location at = new Location(world, x + .5, y, z + .5); if (safe(at)) destinations.add(at);
            }
            if (destinations.size() < 12) throw new IllegalArgumentException("Not enough safe ambient walk destinations");
            for (int i = 0; i < 3; i++) spawn(i);
        } catch (RuntimeException error) { removeAll(); throw error; }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 5);
        animationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::animate, 1, 1);
        plugin.getLogger().info("Ambient Massiffs ready: exactly 3 peaceful ground navigators in saved spawn footprint.");
    }
    private void spawn(int index) {
        String id = "spawn_massiff_" + (index + 1); Location start = starts.get(index);
        if (live.values().stream().anyMatch(v -> v.body.isValid() && v.body.getLocation().distanceSquared(starts.get(index)) < CLEARANCE * CLEARANCE))
            start = destinations.stream().filter(this::safe).filter(at -> live.values().stream().noneMatch(v -> v.body.isValid() && v.body.getLocation().distanceSquared(at) < CLEARANCE * CLEARANCE)).findFirst().orElse(null);
        if (start == null) return;
        final Location spawnAt = start;
        Wolf body = territories.spawnNpc(EntityType.WOLF, spawnAt, () -> world.spawn(spawnAt, Wolf.class, e -> {
            e.getPersistentDataContainer().set(MARKER, PersistentDataType.STRING, id);
            e.setPersistent(false); e.setRemoveWhenFarAway(false); e.setInvulnerable(true); e.setSilent(true);
            e.setCollidable(false); e.setCanPickupItems(false); e.setBreed(false); e.setTamed(false); e.setAngry(false);
            e.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE).setBaseValue(0);
            e.getAttribute(org.bukkit.attribute.Attribute.MOVEMENT_SPEED).setBaseValue(.18);
        }));
        try {
            Bukkit.getMobGoals().removeAllGoals(body); body.setAware(true); body.setTarget(null);
            body.getPathfinder().setCanOpenDoors(false); body.getPathfinder().setCanPassDoors(false); body.getPathfinder().setCanFloat(false);
            live.put(id, new Live(id, body, new AmbientMassiffModel(body)));
            // Noncolliding with players; reciprocal exemptions enable native dog-only collision.
            for (Live other : live.values()) if (other.body != body) {
                body.getCollidableExemptions().add(other.body.getUniqueId());
                other.body.getCollidableExemptions().add(body.getUniqueId());
            }
        } catch (RuntimeException error) { body.remove(); throw error; }
    }
    public Map<String, Wolf> bodies() { Map<String, Wolf> result = new LinkedHashMap<>(); live.forEach((id,v) -> result.put(id,v.body)); return Map.copyOf(result); }
    public AmbientMassiffModel model(String id) { return live.get(id).model; }
    public boolean inside(Location at) { return at.getWorld().equals(world) && at.getX() >= minX + .5 && at.getX() <= maxX + .5 && at.getZ() >= minZ + .5 && at.getZ() <= maxZ + .5 && Math.abs(at.getY() - y) < .2; }
    public boolean safe(Location at) {
        if (!inside(at) || territories.zone(at) != TerritoryPolicy.Zone.SAFEZONE) return false;
        // Full one-block clearance around each footfall excludes edges, water, portals and doorways.
        int x = at.getBlockX(), z = at.getBlockZ();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            var floor = world.getBlockAt(x + dx, y - 1, z + dz);
            if (!floor.getType().isSolid() || !floor.getBoundingBox().contains(x + dx + .5, y - .01, z + dz + .5)) return false;
            if (Set.of(Material.MAGMA_BLOCK, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.CACTUS, Material.POWDER_SNOW).contains(floor.getType())) return false;
            for (int dy = 0; dy < 2; dy++) { var b = world.getBlockAt(x + dx, y + dy, z + dz); if (!b.isEmpty()) return false; }
        }
        return true;
    }
    private void yieldIfClosing(Live value, int now) {
        if (!value.body.getPathfinder().hasPath()) return;
        var at = value.body.getLocation(); var velocity = value.body.getVelocity();
        for (Live other : live.values()) {
            if (other == value || !other.body.isValid()) continue;
            var there = other.body.getLocation(); var otherVelocity = other.body.getVelocity();
            double dx = at.getX() - there.getX(), dz = at.getZ() - there.getZ();
            double vx = velocity.getX() - otherVelocity.getX(), vz = velocity.getZ() - otherVelocity.getZ();
            double speed = vx * vx + vz * vz, closing = dx * vx + dz * vz;
            if (speed < 1e-8 || closing >= 0) continue;
            double t = Math.min(10, -closing / speed);
            double px = dx + vx * t, pz = dz + vz * t;
            if (px * px + pz * pz >= (CLEARANCE + .35) * (CLEARANCE + .35)) continue;
            boolean otherMoving = otherVelocity.getX() * otherVelocity.getX() + otherVelocity.getZ() * otherVelocity.getZ() > 1e-6;
            if (otherMoving && value.id.compareTo(other.id) < 0) continue;
            value.body.getPathfinder().stopPathfinding(); value.navigating = false;
            value.nextWalk = now + 10; return;
        }
    }
    private boolean routeClear(Live value, List<Location> route) {
        List<Location> mine = new ArrayList<>(); mine.add(value.body.getLocation()); mine.addAll(route);
        for (Live other : live.values()) {
            if (other == value || !other.body.isValid()) continue;
            List<Location> theirs = new ArrayList<>(); theirs.add(other.body.getLocation());
            var path = other.body.getPathfinder().getCurrentPath();
            if (path != null && other.body.getPathfinder().hasPath()) {
                var points = path.getPoints(); theirs.addAll(points.subList(Math.min(path.getNextPointIndex(), points.size()), points.size()));
            }
            if (theirs.size() == 1) theirs.add(theirs.getFirst());
            for (int i = 1; i < mine.size(); i++) for (int j = 1; j < theirs.size(); j++)
                if (segmentDistanceSquared(mine.get(i - 1), mine.get(i), theirs.get(j - 1), theirs.get(j)) < CLEARANCE * CLEARANCE) return false;
        }
        return true;
    }
    private static double pointSegment(double x, double z, Location a, Location b) {
        double dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ();
        double length = dx * dx + dz * dz;
        double t = length == 0 ? 0 : Math.max(0, Math.min(1, ((x - a.getX()) * dx + (z - a.getZ()) * dz) / length));
        double ex = x - a.getX() - t * dx, ez = z - a.getZ() - t * dz; return ex * ex + ez * ez;
    }
    private static double segmentDistanceSquared(Location a, Location b, Location c, Location d) {
        double ax = b.getX() - a.getX(), az = b.getZ() - a.getZ(), bx = d.getX() - c.getX(), bz = d.getZ() - c.getZ();
        double denominator = ax * bz - az * bx;
        if (Math.abs(denominator) > 1e-9) {
            double dx = c.getX() - a.getX(), dz = c.getZ() - a.getZ();
            double t = (dx * bz - dz * bx) / denominator, u = (dx * az - dz * ax) / denominator;
            if (t >= 0 && t <= 1 && u >= 0 && u <= 1) return 0;
        }
        return Math.min(Math.min(pointSegment(a.getX(), a.getZ(), c, d), pointSegment(b.getX(), b.getZ(), c, d)),
                Math.min(pointSegment(c.getX(), c.getZ(), a, b), pointSegment(d.getX(), d.getZ(), a, b)));
    }
    private void animate() {
        int now = Bukkit.getCurrentTick();
        for (Live value : live.values()) {
            if (!value.body.isValid() || !value.model.valid()) continue;
            Location at = value.body.getLocation();
            yieldIfClosing(value, now);
            double dx = at.getX() - value.animationPrevious.getX(), dz = at.getZ() - value.animationPrevious.getZ();
            if (dx * dx + dz * dz > 1.0e-7) value.lastAnimatedMotion = now;
            value.animationPrevious = at;
            // Brief stationary gaps at turns should not reset an advancing gait loop.
            // Navigation intent alone never counts as moving feet (e.g. a blocked path).
            value.model.walk(now - value.lastAnimatedMotion <= 3);
        }
    }
    private void tick() {
        int now = Bukkit.getCurrentTick();
        for (int i = 0; i < 3; i++) {
            String id = "spawn_massiff_" + (i + 1); Live value = live.get(id);
            if (value == null || !value.body.isValid()) { if (value != null) { value.model.close(); for (Live other : live.values()) other.body.getCollidableExemptions().remove(value.body.getUniqueId()); } spawn(i); continue; }
            Wolf body = value.body; Location at = body.getLocation();
            if (!value.model.valid()) value.model = new AmbientMassiffModel(body);
            body.setTarget(null); body.setAngry(false); body.setFireTicks(0);
            boolean moving = at.distanceSquared(value.previous) > .0001; value.previous = at;
            if (moving) value.lastProgress = now;
            if (body.getPathfinder().hasPath() && now - value.lastProgress > 80) body.getPathfinder().stopPathfinding();
            if (value.navigating && !body.getPathfinder().hasPath()) {
                value.navigating = false; value.nextWalk = now + java.util.concurrent.ThreadLocalRandom.current().nextInt(10, 31);
            }
            if (now < value.nextWalk || body.getPathfinder().hasPath()) continue;
            var random = java.util.concurrent.ThreadLocalRandom.current();
            for (int attempt = 0; attempt < 12; attempt++) {
                Location to = destinations.get(random.nextInt(destinations.size()));
                if (to.distanceSquared(at) < 9 || !safe(to)) continue;
                var path = body.getPathfinder().findPath(to);
                if (path == null || !path.canReachFinalPoint() || path.getPoints().stream().anyMatch(p -> !safe(p)) || !routeClear(value, path.getPoints())) continue;
                if (body.getPathfinder().moveTo(path, .8)) { value.navigating = true; value.lastProgress = now; break; }
            }
            if (!body.getPathfinder().hasPath()) value.nextWalk = now + 20;
        }
    }
    private boolean owned(Entity e) { return e.getPersistentDataContainer().has(MARKER, PersistentDataType.STRING); }
    private void removeStale(Entity e) {
        if (owned(e) && live.values().stream().noneMatch(v -> v.body.getUniqueId().equals(e.getUniqueId()))) {
            AmbientMassiffModel.removeStale(e);
        }
    }
    @EventHandler public void chunk(ChunkLoadEvent e) { for (Entity entity : e.getChunk().getEntities()) removeStale(entity); }
    @EventHandler(priority = EventPriority.HIGHEST) public void click(PlayerInteractEntityEvent e) { if (owned(e.getRightClicked())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void damage(EntityDamageEvent e) { if (owned(e.getEntity())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.LOWEST) public void attack(EntityDamageByEntityEvent e) { if (owned(e.getDamager())) e.setCancelled(true); }
    @EventHandler public void death(EntityDeathEvent e) { if (owned(e.getEntity())) { e.getDrops().clear(); e.setDroppedExp(0); } }
    @EventHandler(priority = EventPriority.HIGHEST) public void target(EntityTargetEvent e) { if (owned(e.getEntity()) || e.getTarget() != null && owned(e.getTarget())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void tame(EntityTameEvent e) { if (owned(e.getEntity())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void leash(PlayerLeashEntityEvent e) { if (owned(e.getEntity())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void mount(EntityMountEvent e) { if (owned(e.getMount()) || owned(e.getEntity())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void teleport(EntityTeleportEvent e) { if (owned(e.getEntity())) e.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST) public void combust(EntityCombustEvent e) { if (owned(e.getEntity())) e.setCancelled(true); }
    private void removeAll() { live.values().forEach(v -> { v.model.close(); v.body.remove(); }); live.clear(); chunks.forEach(c -> c.removePluginChunkTicket(plugin)); chunks.clear(); }
    @Override public void close() { animationTask.cancel(); task.cancel(); HandlerList.unregisterAll(this); removeAll(); }
}
