package net.mandomc.gameplay.mobs;

import java.util.*;
import net.mandomc.integrations.mobs.MythicMobAccess;
import net.mandomc.integrations.regions.MobProtection;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
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
    private final NamespacedKey patrol;
    private final PatrolState patrols = new PatrolState();
    private final Random random = new Random();
    private final BukkitTask task;
    private boolean healthy = true;

    public SandySpawns(JavaPlugin plugin, SandySettings settings, MythicMobAccess mobs) {
        this.plugin = plugin;
        this.settings = settings;
        this.mobs = mobs;
        encounter = new NamespacedKey(plugin, "sandy_encounter_v1");
        patrol = new NamespacedKey(plugin, "sandy_patrol_v1");
        if (Bukkit.getWorld(NamespacedKey.fromString(settings.world())) == null) {
            throw new IllegalArgumentException("mobs/spawning.yml: configured survival world is not loaded: " + settings.world());
        }
        for (String biome : settings.biomes()) {
            if (Registry.BIOME.get(NamespacedKey.fromString(biome)) == null)
                throw new IllegalArgumentException("mobs/spawning.yml: unknown biome " + biome);
        }
        for (String ground : settings.ground()) {
            if (Material.matchMaterial(ground) == null) throw new IllegalArgumentException("mobs/spawning.yml: unknown ground " + ground);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 200, settings.interval() * 20L);
    }

    public SandySettings settings() { return settings; }

    public static boolean eligible(GameMode mode) {
        return mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE || mode == GameMode.CREATIVE;
    }

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
    public void interact(PlayerInteractEntityEvent event) {
        if (mobs.managed(event.getRightClicked())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void breed(EntityBreedEvent event) {
        if (mobs.managed(event.getMother()) || mobs.managed(event.getFather())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void mount(EntityMountEvent event) {
        if (mobs.managed(event.getMount())) event.setCancelled(true);
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
            var population = new ArrayList<>(world.getLivingEntities().stream().filter(entity -> mobs.managed(entity)
                    || entity.getPersistentDataContainer().has(encounter, PersistentDataType.STRING)).toList());
            var players = new ArrayList<>(world.getPlayers());
            // Remove only our natural encounters after all players leave their vicinity; no death/drop pipeline.
            for (var entity : List.copyOf(population)) {
                if (entity.getPersistentDataContainer().has(encounter)
                        && players.stream().noneMatch(p -> horizontal(p.getLocation(), entity.getLocation()) <= 128 * 128)) {
                    mobs.remove(entity);
                    population.remove(entity);
                }
            }
            refreshPatrols(population, players);
            Collections.shuffle(players, random);
            for (Player player : players) {
                if (player.isDead() || !eligible(player.getGameMode())
                        || !biome(player.getLocation())) continue;
                claimNearby(player, population.stream().collect(java.util.stream.Collectors.toMap(Entity::getUniqueId, e -> e)));
                for (var kind : EncounterPlan.opportunities(random, settings, patrols.playerGroup(player.getUniqueId()) == null)) {
                    tryEncounter(player, kind, population, players);
                }
            }
        } catch (RuntimeException error) {
            healthy = false;
            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                    "Sandy spawning stopped; natural suppression disabled until repaired and restarted", error);
        }
    }

    private void tryEncounter(Player player, EncounterPlan.Kind kind, List<LivingEntity> population, List<Player> players) {
        var plan = EncounterPlan.roll(kind, random, settings);
        if (population.size() + plan.members().size() > settings.worldCap()) return;
        if (!plan.fits((int) population.stream().filter(entity -> horizontal(entity.getLocation(), player.getLocation())
                <= settings.radius() * settings.radius()).count(), population.size(), settings)) return;
        World world = player.getWorld();
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
            population.addAll(spawn(plan, sites, kind == EncounterPlan.Kind.PATROL ? player : null));
            break;
        }
    }

    private boolean hostile(LivingEntity entity) {
        String type = mobs.type(entity);
        return type.startsWith("TuskenRaider") || type.equals("Massiff")
                || type.isEmpty() && entity.getPersistentDataContainer().has(patrol) && entity.getType() == EntityType.ZOMBIE;
    }

    private void refreshPatrols(List<LivingEntity> population, List<Player> players) {
        var byGroup = new HashMap<String, List<LivingEntity>>();
        var byId = new HashMap<UUID, LivingEntity>();
        for (var entity : population) {
            byId.put(entity.getUniqueId(), entity);
            String id = entity.getPersistentDataContainer().get(encounter, PersistentDataType.STRING);
            if (id != null) byGroup.computeIfAbsent(id, ignored -> new ArrayList<>()).add(entity);
        }
        var active = new HashSet<String>();
        for (var entry : byGroup.entrySet()) {
            boolean isPatrol = patrols.groups().contains(entry.getKey()) || entry.getValue().stream().anyMatch(entity ->
                    entity.getPersistentDataContainer().has(patrol) || mobs.type(entity).startsWith("TuskenRaider"));
            if (!isPatrol) continue;
            var members = new HashSet<UUID>();
            var hostiles = new HashSet<UUID>();
            for (var entity : entry.getValue()) {
                members.add(entity.getUniqueId());
                if (hostile(entity)) hostiles.add(entity.getUniqueId());
                entity.getPersistentDataContainer().set(patrol, PersistentDataType.BYTE, (byte) 1);
            }
            patrols.update(entry.getKey(), members, hostiles);
            if (!hostiles.isEmpty()) active.add(entry.getKey());
        }
        patrols.retainGroups(active);
        var eligiblePlayers = players.stream().filter(player -> !player.isDead() && eligible(player.getGameMode())).toList();
        patrols.retainPlayers(eligiblePlayers.stream().map(Player::getUniqueId).collect(java.util.stream.Collectors.toSet()));
        long now = System.nanoTime() / 1_000_000;
        for (Player player : eligiblePlayers) {
            String id = patrols.playerGroup(player.getUniqueId());
            if (id == null) continue;
            if (distance(id, player, byId) <= settings.radius() * settings.radius()
                    || patrols.hostiles(id).stream().map(byId::get).filter(Objects::nonNull)
                    .anyMatch(entity -> entity instanceof Mob mob && player.equals(mob.getTarget()))) {
                patrols.touch(player.getUniqueId(), now);
            }
        }
        patrols.expire(now, settings.patrolDisengageSeconds() * 1000L);
        // Nearby unassigned players share an existing patrol instead of receiving overlapping fresh groups.
        for (Player player : eligiblePlayers) claimNearby(player, byId);
        enforceTargets(population);
    }

    private void claimNearby(Player player, Map<UUID, LivingEntity> entities) {
        if (patrols.playerGroup(player.getUniqueId()) != null) return;
        String nearest = null;
        double best = settings.radius() * settings.radius();
        boolean engaged = false;
        for (String id : patrols.groups()) {
            double distance = distance(id, player, entities);
            boolean targetsPlayer = patrols.hostiles(id).stream().map(entities::get).filter(Objects::nonNull)
                    .anyMatch(entity -> entity instanceof Mob mob && player.equals(mob.getTarget()));
            if (targetsPlayer && (!engaged || distance <= best) || !engaged && distance <= best) {
                best = distance;
                nearest = id;
                engaged = targetsPlayer;
            }
        }
        if (nearest != null) patrols.claim(player.getUniqueId(), nearest, System.nanoTime() / 1_000_000);
    }

    private double distance(String id, Player player, Map<UUID, LivingEntity> entities) {
        return patrols.hostiles(id).stream().map(entities::get).filter(Objects::nonNull)
                .mapToDouble(entity -> horizontal(entity.getLocation(), player.getLocation())).min().orElse(Double.MAX_VALUE);
    }

    private void enforceTargets(Collection<LivingEntity> population) {
        for (var entity : population) {
            if (!(entity instanceof Mob mob) || !(mob.getTarget() instanceof Player player)) continue;
            String group = patrols.memberGroup(entity.getUniqueId());
            String assigned = patrols.playerGroup(player.getUniqueId());
            if (group != null && assigned != null && !group.equals(assigned)) mob.setTarget(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void patrolTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || !(event.getTarget() instanceof Player player)) return;
        if (!entity.getWorld().getKey().toString().equals(settings.world())) return;
        if (player.isDead() || !eligible(player.getGameMode())) return;
        String id = entity.getPersistentDataContainer().get(encounter, PersistentDataType.STRING);
        if (id == null || !mobs.managed(entity)) return;
        if (!entity.getPersistentDataContainer().has(patrol) && !mobs.type(entity).startsWith("TuskenRaider")) return;
        patrols.track(id, entity.getUniqueId(), hostile(entity));
        // Mythic may cancel the native request while selecting its own target. Inspect that side
        // effect even for an already-cancelled event, without claiming its rejected requested target.
        if (event.isCancelled()) { enforceTargets(List.of(entity)); return; }
        if (!player.getWorld().equals(entity.getWorld())
                || !patrols.claimMember(player.getUniqueId(), entity.getUniqueId(), System.nanoTime() / 1_000_000)) {
            event.setCancelled(true);
            event.setTarget(null);
            // Mythic's earlier target listener can already have changed the host's native target.
            // Cancelling the outer request alone does not roll that side effect back.
            if (entity instanceof Mob mob && player.equals(mob.getTarget())) mob.setTarget(null);
        }
        enforceTargets(List.of(entity));
    }

    @EventHandler public void patrolDeath(EntityDeathEvent event) { patrols.memberGone(event.getEntity().getUniqueId()); }
    @EventHandler public void patrolRemoved(org.bukkit.event.entity.EntityRemoveEvent event) {
        patrols.memberGone(event.getEntity().getUniqueId());
    }
    @EventHandler public void patrolQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        patrols.release(event.getPlayer().getUniqueId());
    }
    @EventHandler public void patrolPlayerDeath(PlayerDeathEvent event) {
        patrols.release(event.getEntity().getUniqueId());
    }
    @EventHandler public void patrolWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        patrols.release(event.getPlayer().getUniqueId());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void patrolMode(org.bukkit.event.player.PlayerGameModeChangeEvent event) {
        if (!eligible(event.getNewGameMode())) patrols.release(event.getPlayer().getUniqueId());
    }

    private List<Location> sites(Location center, int count, List<Player> players) {
        var result = new ArrayList<Location>();
        for (int attempt = 0; attempt < count * 12 && result.size() < count; attempt++) {
            Location site = surface(center.getWorld(), center.getBlockX() + random.nextInt(-7, 8),
                    center.getBlockZ() + random.nextInt(-7, 8));
            if (site == null || Math.abs(site.getY() - center.getY()) > 2 || !protection.encounter(site)
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
                if (!protection.encounter(new Location(world, x + dx + 0.5, y + dy, z + dz + 0.5))) return null;
            }
        }
        return site;
    }

    /** All-or-nothing creation: a cancelled member rolls back the group without loot. */
    public List<LivingEntity> spawn(EncounterPlan plan, List<Location> sites) {
        return spawn(plan, sites, null);
    }

    private List<LivingEntity> spawn(EncounterPlan plan, List<Location> sites, Player focus) {
        if (sites.size() != plan.members().size()) throw new IllegalArgumentException("One site is required per mob");
        var spawned = new ArrayList<LivingEntity>();
        String group = UUID.randomUUID().toString();
        boolean isPatrol = plan.members().stream().anyMatch(member -> member.type().startsWith("TuskenRaider"));
        try {
            for (int i = 0; i < sites.size(); i++) {
                if (!biome(sites.get(i)) || !protection.encounter(sites.get(i))) throw new IllegalStateException("Encounter site denied");
                var member = plan.members().get(i);
                LivingEntity entity = mobs.spawn(member.type(), sites.get(i), member.level());
                if (entity == null || !entity.isValid()) throw new IllegalStateException("Encounter member spawn cancelled");
                spawned.add(entity);
                entity.getPersistentDataContainer().set(encounter, PersistentDataType.STRING, group);
                if (isPatrol) entity.getPersistentDataContainer().set(patrol, PersistentDataType.BYTE, (byte) 1);
            }
            if (isPatrol) {
                var members = spawned.stream().map(Entity::getUniqueId).collect(java.util.stream.Collectors.toSet());
                var hostiles = spawned.stream().filter(this::hostile).map(Entity::getUniqueId)
                        .collect(java.util.stream.Collectors.toSet());
                patrols.update(group, members, hostiles);
                if (focus != null && !patrols.claim(focus.getUniqueId(), group, System.nanoTime() / 1_000_000)) {
                    spawned.forEach(mobs::remove);
                    patrols.remove(group);
                    return List.of();
                }
                enforceTargets(spawned);
            }
            return spawned;
        } catch (RuntimeException error) {
            patrols.remove(group);
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
