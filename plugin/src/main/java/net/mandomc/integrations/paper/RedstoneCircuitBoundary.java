package net.mandomc.integrations.paper;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.function.Predicate;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.data.AnaloguePowerable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.Powerable;
import org.bukkit.block.data.type.Hopper;
import org.bukkit.block.data.type.SculkSensor;
import org.bukkit.block.data.type.Dispenser;
import org.bukkit.block.data.type.Crafter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** One server-wide boundary: no world, region, operator or event exemptions. */
public final class RedstoneCircuitBoundary implements Listener {
    private static final Set<String> NATIVE_CLASSES = Set.of(
            "net.minecraft.world.level.SignalGetter",
            "net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase",
            "net.minecraft.world.level.block.DispenserBlock",
            "net.minecraft.world.level.block.CrafterBlock",
            "net.minecraft.world.level.block.CommandBlock",
            "net.minecraft.world.level.block.DaylightDetectorBlock",
            "net.minecraft.world.level.block.piston.PistonBaseBlock");
    private final Method handle;
    private final Method sections;
    private final Method maybeHas;
    private final Method state;
    private final Method material;

    public RedstoneCircuitBoundary(JavaPlugin plugin) {
        try {
            if (!"26.1.2-v1".equals(System.getProperty("mandomc.redstone.agent"))) {
                throw new IllegalStateException("Start Paper with the MandoMC redstone Java agent.");
            }
            for (String type : NATIVE_CLASSES) {
                Class.forName(type);
                if (!"disabled".equals(System.getProperty("mandomc.redstone." + type.replace('.', '/')))) {
                    throw new IllegalStateException("Native redstone cutoff missing: " + type);
                }
            }
            handle = Class.forName("org.bukkit.craftbukkit.CraftChunk").getMethod("getHandle",
                    Class.forName("net.minecraft.world.level.chunk.status.ChunkStatus"));
            sections = Class.forName("net.minecraft.world.level.chunk.ChunkAccess").getMethod("getSections");
            Class<?> section = Class.forName("net.minecraft.world.level.chunk.LevelChunkSection");
            maybeHas = section.getMethod("maybeHas", Predicate.class);
            state = section.getMethod("getBlockState", int.class, int.class, int.class);
            material = Class.forName("net.minecraft.world.level.block.state.BlockState").getMethod("getBukkitMaterial");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Redstone boundary requires pinned Paper 26.1.2", failure);
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        long start = System.nanoTime();
        int chunks = 0;
        for (var world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                normalize(chunk);
                chunks++;
            }
        }
        plugin.getLogger().info("Redstone circuits disabled in all worlds; normalized " + chunks
                + " loaded chunks in " + (System.nanoTime() - start) / 1_000_000 + "ms.");
    }

    private boolean relevant(Material type) {
        // Never touches ores, inventories, recipes, items or terrain identity.
        String name = type.name();
        return name.endsWith("_BUTTON") || name.endsWith("_PRESSURE_PLATE") || name.endsWith("_DOOR")
                || name.endsWith("_TRAPDOOR") || name.endsWith("COPPER_BULB") || name.endsWith("FENCE_GATE")
                || switch (type) {
                    case REDSTONE_WIRE, REPEATER, COMPARATOR, REDSTONE_TORCH, REDSTONE_WALL_TORCH,
                            REDSTONE_LAMP, LEVER, OBSERVER, DAYLIGHT_DETECTOR, SCULK_SENSOR,
                            CALIBRATED_SCULK_SENSOR, POWERED_RAIL, ACTIVATOR_RAIL, DETECTOR_RAIL,
                            HOPPER, DISPENSER, DROPPER, CRAFTER, TRIPWIRE, TRIPWIRE_HOOK -> true;
                    default -> false;
                };
    }

    private void normalize(Chunk chunk) {
        try {
            // null requests the already-loaded chunk; no ticket, getChunkAt or world-wide force loading.
            Object[] nativeSections = (Object[]) sections.invoke(handle.invoke(chunk, new Object[]{null}));
            Predicate<Object> relevantState = value -> {
                try { return relevant((Material) material.invoke(value)); }
                catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            };
            for (int section = 0; section < nativeSections.length; section++) {
                Object nativeSection = nativeSections[section];
                if (nativeSection == null || !(boolean) maybeHas.invoke(nativeSection, relevantState)) continue;
                for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                    Object nativeState = state.invoke(nativeSection, x, y, z);
                    if (!relevantState.test(nativeState)) continue;
                    var block = chunk.getBlock(x, chunk.getWorld().getMinHeight() + section * 16 + y, z);
                    BlockData data = block.getBlockData();
                    String before = data.getAsString();
                    boolean powered = data instanceof Powerable power && power.isPowered();
                    if (data instanceof Powerable power) power.setPowered(false);
                    if (data instanceof AnaloguePowerable power) power.setPower(0);
                    if (powered && data instanceof Openable open) open.setOpen(false);
                    if (data instanceof Lightable light) light.setLit(false);
                    if (data instanceof Hopper hopper) hopper.setEnabled(true);
                    if (data instanceof SculkSensor sensor) sensor.setPhase(SculkSensor.Phase.INACTIVE);
                    if (data instanceof Dispenser dispenser) dispenser.setTriggered(false);
                    if (data instanceof Crafter crafter) { crafter.setTriggered(false); crafter.setCrafting(false); }
                    if (!before.equals(data.getAsString())) block.setBlockData(data, false);
                }
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot normalize loaded redstone circuit state", failure);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void loaded(ChunkLoadEvent event) { normalize(event.getChunk()); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void power(BlockRedstoneEvent event) {
        // Native reads are already zero. This prevents sensor/observer and local state activation;
        // allowing zero transitions avoids trapping observers or inverted torches in retry loops.
        event.setNewCurrent(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void extend(BlockPistonExtendEvent event) { event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void retract(BlockPistonRetractEvent event) { event.setCancelled(true); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void tnt(TNTPrimeEvent event) {
        if (event.getCause() == TNTPrimeEvent.PrimeCause.REDSTONE) event.setCancelled(true);
    }
}
