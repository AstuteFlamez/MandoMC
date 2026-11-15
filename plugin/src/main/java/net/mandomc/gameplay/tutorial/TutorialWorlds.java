package net.mandomc.gameplay.tutorial;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

/** Small generated training scenes, one world per player; bounded warm pool and reusable overflow slots. */
public final class TutorialWorlds implements AutoCloseable {
    private final JavaPlugin plugin;
    private final int warm, maximum;
    private final Map<Integer, CompletableFuture<World>> worlds = new LinkedHashMap<>();
    private final Map<Integer, Lease> leased = new HashMap<>();
    private boolean closed;
    public TutorialWorlds(JavaPlugin plugin, int warm, int maximum) {
        if (warm < 1 || warm > maximum || maximum > 32) throw new IllegalArgumentException("Invalid tutorial pool limits");
        this.plugin = plugin; this.warm = warm; this.maximum = maximum;
        // Spread world initialization across ticks; chunk preparation uses Paper's async chunk API.
        for (int i = 0; i < warm; i++) {
            int slot = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (!closed) prepare(slot); }, 40L + i * 40L);
        }
    }
    public record Lease(int slot, UUID token, CompletableFuture<World> ready) { }
    public Lease acquire() {
        if (closed) return null;
        for (int slot = 0; slot < maximum; slot++) if (!leased.containsKey(slot)) {
            var lease = new Lease(slot, UUID.randomUUID(), prepare(slot)); leased.put(slot, lease); return lease;
        }
        return null;
    }
    private CompletableFuture<World> prepare(int slot) {
        var existing = worlds.get(slot);
        if (existing != null) return existing;
        var future = new CompletableFuture<World>(); worlds.put(slot, future);
        try {
            var key = new NamespacedKey("mandomc", "tutorial_" + slot);
            World world = Bukkit.getWorld(key);
            if (world == null) world = WorldCreator.ofKey(key).generator(new Scene()).generateStructures(false).createWorld();
            if (world == null) throw new IllegalStateException("Could not create " + key);
            world.setPVP(false); world.setDifficulty(Difficulty.NORMAL); world.setAutoSave(false);
            world.setTime(6000); world.setStorm(false); world.setThundering(false);
            world.setSpawnLocation(new Location(world, 8.5, 65, .5, -90, 0));
            world.getWorldBorder().setCenter(48, 0); world.getWorldBorder().setSize(192);
            for (var rule : List.of(GameRules.SPAWN_MOBS, GameRules.ADVANCE_TIME, GameRules.ADVANCE_WEATHER,
                    GameRules.MOB_GRIEFING, GameRules.TNT_EXPLODES, GameRules.COMMAND_BLOCKS_WORK)) world.setGameRule(rule, false);
            world.setGameRule(GameRules.RANDOM_TICK_SPEED, 0);
            world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
            world.setGameRule(GameRules.KEEP_INVENTORY, true);
            var readyWorld = world;
            var chunks = new ArrayList<CompletableFuture<?>>();
            for (int x = -3; x <= 8; x++) for (int z = -6; z <= 5; z++) chunks.add(world.getChunkAtAsync(x, z, true));
            CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) -> {
                if (!plugin.isEnabled() || closed) { future.completeExceptionally(new IllegalStateException("Tutorial pool closed")); return; }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (failure != null) { future.completeExceptionally(failure); return; }
                    for (int x = -3; x <= 8; x++) for (int z = -6; z <= 5; z++) readyWorld.addPluginChunkTicket(x, z, plugin);
                    // Prior tutorial entities are disposable; player state is recovered separately from player.dat.
                    for (var entity : readyWorld.getEntities()) if (!(entity instanceof Player)) entity.remove();
                    future.complete(readyWorld);
                });
            });
        } catch (RuntimeException error) { future.completeExceptionally(error); }
        return future;
    }
    public void release(Lease lease) {
        int slot = lease.slot();
        if (!Objects.equals(leased.get(slot), lease)) return;
        var future = worlds.get(slot);
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) { leased.remove(slot, lease); return; }
        var world = future.join();
        // A failed exit must never make an occupied instance available to another player.
        if (!world.getPlayers().isEmpty()) {
            if (!closed && plugin.isEnabled()) Bukkit.getScheduler().runTaskLater(plugin, () -> release(lease), 20);
            return;
        }
        leased.remove(slot, lease);
        for (var entity : world.getEntities()) entity.remove();
        if (slot >= warm) {
            world.removePluginChunkTickets(plugin);
            if (Bukkit.unloadWorld(world, false)) worlds.remove(slot);
        }
    }
    @Override public void close() {
        closed = true;
        for (var future : worlds.values()) if (future.isDone() && !future.isCompletedExceptionally()) {
            var world = future.join(); world.removePluginChunkTickets(plugin);
            if (world.getPlayers().isEmpty()) Bukkit.unloadWorld(world, false);
        }
        worlds.clear(); leased.clear();
    }
    /** Pure chunk generation: no world access, file copies, or large synchronous block-edit loops. */
    public static final class Scene extends ChunkGenerator {
        @Override public void generateNoise(org.bukkit.generator.WorldInfo info, Random random, int chunkX, int chunkZ, ChunkData data) {
            for (int lx = 0; lx < 16; lx++) for (int lz = 0; lz < 16; lz++) {
                int x = chunkX * 16 + lx, z = chunkZ * 16 + lz;
                if (x < -48 || x >= 144 || z < -96 || z >= 96) continue;
                boolean market = x >= 96 && z < 16;
                Material floor = market ? Material.SANDSTONE : z >= 32 ? Material.SMOOTH_STONE
                        : x < 32 ? Material.POLISHED_DEEPSLATE : Material.RED_SANDSTONE;
                data.setRegion(lx, 60, lz, lx + 1, 64, lz + 1, Material.STONE);
                data.setBlock(lx, 64, lz, floor);
                if (x == 31 && Math.abs(z) < 20) data.setBlock(lx, 64, lz, Material.YELLOW_CONCRETE);
                if (Math.abs(z) <= 2 && x >= 0 && x <= 83) data.setBlock(lx, 64, lz, Material.SMOOTH_STONE);
                if (z >= 32 && (x % 16 == 0 || z % 16 == 0)) data.setBlock(lx, 64, lz, Material.BLACK_CONCRETE);
                if (x == -48 || x == 143 || z == -96 || z == 95) data.setRegion(lx, 65, lz, lx + 1, 68, lz + 1, Material.SMOOTH_SANDSTONE);
                // Tatooine market: two recessed stalls, shaded roofs, lit floor and cargo stacks.
                for (int center : new int[] {-32, -8}) {
                    if (x >= 116 && x <= 130 && z >= center - 6 && z <= center + 6) {
                        boolean wall = x == 130 || z == center - 6 || z == center + 6;
                        if (wall) data.setRegion(lx, 65, lz, lx + 1, 70, lz + 1, Material.SMOOTH_SANDSTONE);
                        data.setBlock(lx, 70, lz, center == -32 ? Material.BROWN_TERRACOTTA : Material.GRAY_TERRACOTTA);
                    }
                }
                if ((x == 3 || x == 28 || x == 50 || x == 80) && (z == -8 || z == 8)) {
                    data.setRegion(lx, 65, lz, lx + 1, 69, lz + 1, Material.POLISHED_BASALT);
                    data.setBlock(lx, 69, lz, Material.SEA_LANTERN);
                }
                if (market && x % 16 == 0 && z % 16 == 0) data.setBlock(lx, 64, lz, Material.SEA_LANTERN);
            }
        }
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
        @Override public Location getFixedSpawnLocation(World world, Random random) { return new Location(world, 8.5, 65, .5); }
    }
}
