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
    public static final NamespacedKey MARKER = new NamespacedKey("mandomc", "ambient_massiff_id");
    private static final class Live {
        final String id; final Wolf body; AmbientMassiffModel model;
        Location previous; int nextWalk; int lastProgress; boolean navigating;
        Live(String id, Wolf body, AmbientMassiffModel model) { this.id = id; this.body = body; this.model = model; previous = body.getLocation(); lastProgress = Bukkit.getCurrentTick(); }
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
        plugin.getLogger().info("Ambient Massiffs ready: exactly 3 peaceful ground navigators in saved spawn footprint.");
    }
    private void spawn(int index) {
        String id = "spawn_massiff_" + (index + 1); Location start = starts.get(index);
        Wolf body = territories.spawnNpc(EntityType.WOLF, start, () -> world.spawn(start, Wolf.class, e -> {
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
    private void tick() {
        int now = Bukkit.getCurrentTick();
        for (int i = 0; i < 3; i++) {
            String id = "spawn_massiff_" + (i + 1); Live value = live.get(id);
            if (value == null || !value.body.isValid()) { if (value != null) value.model.close(); spawn(i); continue; }
            Wolf body = value.body; Location at = body.getLocation();
            if (!value.model.valid()) value.model = new AmbientMassiffModel(body);
            body.setTarget(null); body.setAngry(false); body.setFireTicks(0);
            boolean moving = at.distanceSquared(value.previous) > .0001; value.model.walk(moving); value.previous = at;
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
                if (path == null || !path.canReachFinalPoint() || path.getPoints().stream().anyMatch(p -> !safe(p))) continue;
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
    @Override public void close() { task.cancel(); HandlerList.unregisterAll(this); removeAll(); }
}
