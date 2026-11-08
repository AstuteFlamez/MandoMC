package net.mandomc.gameplay.mobs;

import java.util.*;
import net.mandomc.integrations.mobs.MythicMobAccess;
import net.mandomc.integrations.regions.MobProtection;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** One bounded population pass shared by all nearby players; never loads chunks to spawn mobs. */
public final class SandySpawns implements Listener, AutoCloseable {
    private static final Set<CreatureSpawnEvent.SpawnReason> NATURAL = EnumSet.of(
            CreatureSpawnEvent.SpawnReason.NATURAL, CreatureSpawnEvent.SpawnReason.CHUNK_GEN,
            CreatureSpawnEvent.SpawnReason.PATROL, CreatureSpawnEvent.SpawnReason.REINFORCEMENTS);
    private final JavaPlugin plugin;
    private final SandySettings settings;
    private final MythicMobAccess mobs;
    private final MobProtection protection = new MobProtection();
    private final NamespacedKey encounter;
    private final Random random = new Random();
    private final BukkitTask task;
    private boolean healthy = true;

    public SandySpawns(JavaPlugin plugin, SandySettings settings, MythicMobAccess mobs) {
        this.plugin = plugin;
        this.settings = settings;
        this.mobs = mobs;
        encounter = new NamespacedKey(plugin, "sandy_encounter_v1");
        if (Bukkit.getWorld(NamespacedKey.fromString(settings.world())) == null) {
            throw new IllegalArgumentException("mobs.yml: configured survival world is not loaded: " + settings.world());
        }
        for (String biome : settings.biomes()) {
            if (Registry.BIOME.get(NamespacedKey.fromString(biome)) == null)
                throw new IllegalArgumentException("mobs.yml: unknown biome " + biome);
        }
        for (String ground : settings.ground()) {
            if (Material.matchMaterial(ground) == null) throw new IllegalArgumentException("mobs.yml: unknown ground " + ground);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 200, settings.interval() * 20L);
    }

    public SandySettings settings() { return settings; }

    public boolean biome(Location location) {
        return location.getWorld() != null && location.getWorld().getKey().toString().equals(settings.world())
                && settings.biomes().contains(location.getBlock().getBiome().getKey().toString());
    }

    public boolean suppress(CreatureSpawnEvent.SpawnReason reason, Location location) {
        return healthy && NATURAL.contains(reason) && biome(location);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void natural(CreatureSpawnEvent event) {
        if (suppress(event.getSpawnReason(), event.getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void targeting(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() != null && mobs.managed(event.getEntity())
                && (!protection.allowed(event.getEntity().getLocation()) || !protection.allowed(event.getTarget().getLocation()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent event) {
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) source = shooter;
        if ((mobs.managed(source) || mobs.managed(event.getEntity()))
                && (!protection.allowed(source.getLocation()) || !protection.allowed(event.getEntity().getLocation()))) {
            event.setCancelled(true);
        }
    }

    private void tick() {
        if (!healthy) return;
        try {
            World world = Bukkit.getWorld(NamespacedKey.fromString(settings.world()));
            if (world == null) return;
            var population = new ArrayList<>(world.getLivingEntities().stream().filter(mobs::managed).toList());
            var players = new ArrayList<>(world.getPlayers());
            // Remove only our natural encounters after all players leave their vicinity; no death/drop pipeline.
            for (var entity : List.copyOf(population)) {
                if (entity.getPersistentDataContainer().has(encounter)
                        && players.stream().noneMatch(p -> horizontal(p.getLocation(), entity.getLocation()) <= 128 * 128)) {
                    mobs.remove(entity);
                    population.remove(entity);
                }
            }
            Collections.shuffle(players, random);
            for (Player player : players) {
                if (player.isDead() || (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE)
                        || !biome(player.getLocation()) || random.nextDouble() >= settings.chance()) continue;
                var kind = EncounterPlan.Kind.values()[random.nextInt(EncounterPlan.Kind.values().length)];
                var plan = EncounterPlan.roll(kind, random, settings);
                if (population.size() + plan.members().size() > settings.worldCap()) break;
                for (int attempt = 0; attempt < settings.attempts(); attempt++) {
                    double angle = random.nextDouble() * Math.PI * 2;
                    double distance = random.nextDouble(settings.minDistance(), settings.maxDistance());
                    int x = (int) Math.floor(player.getX() + Math.cos(angle) * distance);
                    int z = (int) Math.floor(player.getZ() + Math.sin(angle) * distance);
                    Location center = surface(world, x, z);
                    if (center == null || population.stream().anyMatch(e -> horizontal(e.getLocation(), center)
                            < settings.separation() * settings.separation())) continue;
                    var sites = sites(center, plan.members().size(), players);
                    if (sites.size() != plan.members().size()) continue;
                    boolean fits = sites.stream().allMatch(site -> plan.fits((int) population.stream()
                            .filter(e -> horizontal(e.getLocation(), site) <= settings.radius() * settings.radius()).count(),
                            population.size(), settings));
                    if (!fits) continue;
                    population.addAll(spawn(plan, sites));
                    break;
                }
            }
        } catch (RuntimeException error) {
            healthy = false;
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Sandy spawning stopped; natural suppression disabled until repaired and restarted", error);
        }
    }

    private List<Location> sites(Location center, int count, List<Player> players) {
        var result = new ArrayList<Location>();
        for (int attempt = 0; attempt < count * 12 && result.size() < count; attempt++) {
            Location site = surface(center.getWorld(), center.getBlockX() + random.nextInt(-7, 8),
                    center.getBlockZ() + random.nextInt(-7, 8));
            if (site == null || Math.abs(site.getY() - center.getY()) > 2 || !protection.allowed(site)
                    || players.stream().anyMatch(p -> horizontal(p.getLocation(), site) < settings.minDistance() * settings.minDistance())
                    || result.stream().anyMatch(p -> horizontal(p, site) < 9)) continue;
            result.add(site);
        }
        return result;
    }

    private Location surface(World world, int x, int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        Location site = new Location(world, x + 0.5, y + 1, z + 0.5);
        if (!world.getWorldBorder().isInside(site) || !biome(site)
                || !settings.ground().contains(world.getBlockAt(x, y, z).getType().name())) return null;
        // A generous empty footprint accommodates the recovered bantha/dewback models too.
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (!world.isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) return null;
            if (!world.getBlockAt(x + dx, y, z + dz).getType().isSolid()) return null;
            for (int dy = 1; dy <= 3; dy++) {
                if (!world.getBlockAt(x + dx, y + dy, z + dz).getType().isAir()) return null;
                if (!protection.allowed(new Location(world, x + dx + 0.5, y + dy, z + dz + 0.5))) return null;
            }
        }
        return site;
    }

    /** All-or-nothing creation: a cancelled member rolls back the group without loot. */
    public List<LivingEntity> spawn(EncounterPlan plan, List<Location> sites) {
        if (sites.size() != plan.members().size()) throw new IllegalArgumentException("One site is required per mob");
        var spawned = new ArrayList<LivingEntity>();
        String group = UUID.randomUUID().toString();
        try {
            for (int i = 0; i < sites.size(); i++) {
                if (!biome(sites.get(i)) || !protection.allowed(sites.get(i))) throw new IllegalStateException("Encounter site denied");
                var member = plan.members().get(i);
                LivingEntity entity = mobs.spawn(member.type(), sites.get(i), member.level());
                if (entity == null || !entity.isValid()) throw new IllegalStateException("Encounter member spawn cancelled");
                spawned.add(entity);
                entity.getPersistentDataContainer().set(encounter, PersistentDataType.STRING, group);
            }
            return spawned;
        } catch (RuntimeException error) {
            for (var entity : spawned) mobs.remove(entity);
            throw error;
        }
    }

    private static double horizontal(Location a, Location b) {
        double x = a.getX() - b.getX(), z = a.getZ() - b.getZ();
        return x * x + z * z;
    }

    @Override public void close() {
        task.cancel();
        HandlerList.unregisterAll(this);
    }
}
