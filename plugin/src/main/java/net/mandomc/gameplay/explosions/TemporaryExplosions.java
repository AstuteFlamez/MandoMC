package net.mandomc.gameplay.explosions;

import net.mandomc.config.AtomicData;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Bed;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

/** One world-wide owner of temporary blast holes, including vendor explosions. */
public final class TemporaryExplosions implements Listener, AutoCloseable {
    public record Position(UUID world, int x, int y, int z) {
        public static Position of(Block b) {
            return new Position(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ());
        }
    }

    private record Saved(Position position, String data, long due) {}

    private final JavaPlugin plugin;
    private final BlastSettings settings;
    private final Predicate<Location> allowed;
    private final Path journal;
    private final Map<Position, Saved> pending = new LinkedHashMap<>();
    private final BukkitTask task;

    public TemporaryExplosions(
            JavaPlugin plugin, BlastSettings settings, Predicate<Location> allowed)
            throws Exception {
        this.plugin = plugin;
        this.settings = settings;
        this.allowed = allowed;
        journal = plugin.getDataFolder().toPath().resolve("data/explosion-restoration.yml");
        if (java.nio.file.Files.exists(journal)) {
            var data = new YamlConfiguration();
            data.load(journal.toFile());
            if (data.getInt("schema-version") != 1)
                throw new IllegalArgumentException("Invalid explosion journal schema");
            for (var entry : data.getMapList("blocks")) {
                var pos =
                        new Position(
                                UUID.fromString(entry.get("world").toString()),
                                ((Number) entry.get("x")).intValue(),
                                ((Number) entry.get("y")).intValue(),
                                ((Number) entry.get("z")).intValue());
                String blockData = entry.get("data").toString();
                Bukkit.createBlockData(blockData);
                pending.put(
                        pos, new Saved(pos, blockData, ((Number) entry.get("due")).longValue()));
            }
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> restore(false), settings.restoreIntervalTicks(), settings.restoreIntervalTicks());
    }

    public int pendingCount() {
        return pending.size();
    }

    public BlastSettings settings() {
        return settings;
    }

    public boolean reserved(Block block) {
        return pending.containsKey(Position.of(block));
    }

    public List<Block> candidates(Location center) {
        var result = new ArrayList<Block>();
        World world = center.getWorld();
        int r = settings.radius();
        if (world == null || !allowed.test(center)) return result;
        for (int x = center.getBlockX() - r; x <= center.getBlockX() + r; x++) {
            for (int y = Math.max(world.getMinHeight(), center.getBlockY() - r);
                    y <= Math.min(world.getMaxHeight() - 1, center.getBlockY() + r);
                    y++) {
                for (int z = center.getBlockZ() - r; z <= center.getBlockZ() + r; z++) {
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getLocation().add(.5, .5, .5).distanceSquared(center) <= r * r
                            && eligible(block)
                            && allowed.test(block.getLocation())) result.add(block);
                }
            }
        }
        result.sort(
                Comparator.comparingDouble(
                                (Block b) ->
                                        b.getLocation().add(.5, .5, .5).distanceSquared(center))
                        .thenComparingInt(Block::getY)
                        .thenComparingInt(Block::getX)
                        .thenComparingInt(Block::getZ));
        return result;
    }

    private boolean eligible(Block b) {
        return !reserved(b)
                && !b.getType().isAir()
                && !b.isLiquid()
                && b.getType().getHardness() >= 0
                && !(b.getState() instanceof TileState)
                && !(b.getBlockData() instanceof Bisected)
                && !(b.getBlockData() instanceof Bed)
                && b.getType() != Material.NETHER_PORTAL
                && b.getType() != Material.PISTON_HEAD
                && b.getType() != Material.MOVING_PISTON
                && b.getType() != Material.TNT
                && b.getType() != Material.RESPAWN_ANCHOR
                && b.getType() != Material.END_PORTAL
                && b.getType() != Material.END_GATEWAY;
    }

    private void seed(Location center, List<Block> blocks) {
        blocks.clear();
        blocks.addAll(candidates(center));
    }

    private void limit(Location center, List<Block> blocks) {
        int capacity =
                Math.min(
                        settings.maxBlocks(),
                        Math.max(0, settings.pendingLimit() - pending.size()));
        var selected =
                blocks.stream()
                        .distinct()
                        .filter(b -> b.getWorld().equals(center.getWorld()))
                        .filter(this::eligible)
                        .filter(b -> allowed.test(b.getLocation()))
                        .filter(
                                b ->
                                        b.getLocation().add(.5, .5, .5).distanceSquared(center)
                                                <= settings.radius() * settings.radius())
                        .sorted(
                                Comparator.comparingDouble(
                                        b ->
                                                b.getLocation()
                                                        .add(.5, .5, .5)
                                                        .distanceSquared(center)))
                        .limit(capacity)
                        .toList();
        blocks.clear();
        blocks.addAll(selected);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void entityCandidates(EntityExplodeEvent e) {
        seed(e.getLocation(), e.blockList());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void blockCandidates(BlockExplodeEvent e) {
        seed(e.getBlock().getLocation().add(.5, .5, .5), e.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityLimit(EntityExplodeEvent e) {
        limit(e.getLocation(), e.blockList());
        e.setYield(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void blockLimit(BlockExplodeEvent e) {
        limit(e.getBlock().getLocation().add(.5, .5, .5), e.blockList());
        e.setYield(0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void entitySnapshot(EntityExplodeEvent e) {
        snapshot(e.blockList(), e);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void blockSnapshot(BlockExplodeEvent e) {
        snapshot(e.blockList(), e);
    }

    private void snapshot(List<Block> blocks, Cancellable event) {
        var added = new ArrayList<Position>();
        long due = System.currentTimeMillis() + settings.restoreSeconds() * 1000L;
        for (Block block : blocks) {
            Position pos = Position.of(block);
            if (pending.putIfAbsent(pos, new Saved(pos, block.getBlockData().getAsString(), due))
                    == null) added.add(pos);
        }
        try {
            if (!added.isEmpty()) save();
        } catch (Exception error) {
            added.forEach(pending::remove);
            event.setCancelled(true); // Fail closed if the write-ahead journal cannot be committed.
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Blast cancelled: cannot save restoration journal",
                            error);
        }
    }

    /**
     * Paper reports the source entity's position to EntityExplodeEvent, so use a local blast
     * carrier.
     */
    public void explode(Location center, Entity attacker) {
        var carrier =
                center.getWorld()
                        .spawn(
                                center,
                                org.bukkit.entity.TNTPrimed.class,
                                tnt -> {
                                    tnt.setFuseTicks(Integer.MAX_VALUE);
                                    tnt.setGravity(false);
                                    tnt.setPersistent(false);
                                    tnt.setSource(attacker);
                                });
        try {
            center.getWorld().createExplosion(center, settings.radius(), false, true, carrier);
        } finally {
            carrier.remove();
        }
    }

    /** Vendor block handling is disabled by the adapter; entity damage remains vendor-owned. */
    public void vendorBlast(Location center, Entity source) {
        var event =
                new EntityExplodeEvent(
                        source, center, new ArrayList<>(), 0, ExplosionResult.DESTROY);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;
        for (Block block : event.blockList()) block.setType(Material.AIR, false);
    }

    private void save() throws java.io.IOException {
        var data = new YamlConfiguration();
        data.set("schema-version", 1);
        data.set(
                "blocks",
                pending.values().stream()
                        .map(
                                s ->
                                        Map.of(
                                                "world",
                                                s.position.world.toString(),
                                                "x",
                                                s.position.x,
                                                "y",
                                                s.position.y,
                                                "z",
                                                s.position.z,
                                                "data",
                                                s.data,
                                                "due",
                                                s.due))
                        .toList());
        AtomicData.write(journal, data.saveToString());
    }

    public void restore(boolean all) {
        long now = System.currentTimeMillis();
        var restored = new ArrayList<Saved>();
        for (Saved saved :
                pending.values().stream()
                        .sorted(Comparator.comparingInt(s -> s.position.y))
                        .toList()) {
            if (!all && restored.size() >= settings.restoreBlocksPerStep()) break;
            if (!all && saved.due > now) continue;
            var p = saved.position;
            // Keep support below a waiting block, including when a player delays the lower cell.
            if (!all && pending.containsKey(new Position(p.world, p.x, p.y - 1, p.z))
                    && restored.stream().noneMatch(s -> s.position.equals(
                            new Position(p.world, p.x, p.y - 1, p.z)))) continue;
            World world = Bukkit.getWorld(p.world);
            if (world == null) continue;
            // A bounded journal can safely load an old blast chunk; unloaded worlds remain
            // journaled.
            Block block = world.getBlockAt(p.x, p.y, p.z);
            if (block.getType().isAir() || block.isLiquid()) {
                var box = new org.bukkit.util.BoundingBox(p.x, p.y, p.z, p.x + 1, p.y + 1, p.z + 1);
                if (!all
                        && world
                                .getNearbyEntities(
                                        box, e -> e instanceof org.bukkit.entity.LivingEntity)
                                .stream()
                                .anyMatch(e -> e.getBoundingBox().overlaps(box))) continue;
                block.setBlockData(Bukkit.createBlockData(saved.data), false);
            }
            // A plugin/admin replacement wins; never overwrite a new structure or recreate
            // container loot.
            restored.add(saved);
        }
        if (restored.isEmpty()) return;
        restored.forEach(s -> pending.remove(s.position));
        try {
            save();
        } catch (Exception error) {
            restored.forEach(s -> pending.put(s.position, s));
            plugin.getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Cannot checkpoint restored blocks; retrying",
                            error);
        }
    }

    private boolean nearHole(Block b) {
        if (reserved(b)) return true;
        for (var face :
                new org.bukkit.block.BlockFace[] {
                    org.bukkit.block.BlockFace.UP,
                    org.bukkit.block.BlockFace.DOWN,
                    org.bukkit.block.BlockFace.NORTH,
                    org.bukkit.block.BlockFace.SOUTH,
                    org.bukkit.block.BlockFace.EAST,
                    org.bukkit.block.BlockFace.WEST
                }) if (reserved(b.getRelative(face))) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void place(BlockPlaceEvent e) {
        if (reserved(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void physics(BlockPhysicsEvent e) {
        if (nearHole(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void flow(BlockFromToEvent e) {
        if (reserved(e.getToBlock()) || reserved(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void change(EntityChangeBlockEvent e) {
        if (nearHole(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void form(BlockFormEvent e) {
        if (reserved(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void spread(BlockSpreadEvent e) {
        if (reserved(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void grow(BlockGrowEvent e) {
        if (reserved(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void bucket(org.bukkit.event.player.PlayerBucketEmptyEvent e) {
        if (reserved(e.getBlock()) || reserved(e.getBlockClicked().getRelative(e.getBlockFace())))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void extend(BlockPistonExtendEvent e) {
        if (nearHole(e.getBlock().getRelative(e.getDirection()))
                || e.getBlocks().stream()
                        .anyMatch(b -> nearHole(b) || nearHole(b.getRelative(e.getDirection()))))
            e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void retract(BlockPistonRetractEvent e) {
        if (e.getBlocks().stream().anyMatch(this::nearHole)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void ignite(BlockIgniteEvent e) {
        if (e.getCause() == BlockIgniteEvent.IgniteCause.EXPLOSION) e.setCancelled(true);
    }

    @Override
    public void close() {
        task.cancel();
        restore(true);
        HandlerList.unregisterAll(this);
    }
}
