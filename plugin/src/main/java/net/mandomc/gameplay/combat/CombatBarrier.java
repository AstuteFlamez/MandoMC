package net.mandomc.gameplay.combat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import io.papermc.paper.math.BlockPosition;
import io.papermc.paper.math.Position;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** Per-player packet blocks, backed by authoritative safe-zone entry checks. Never edits the world. */
public final class CombatBarrier implements Listener, AutoCloseable {
    private static final class View {
        private final Player player;
        private final World world;
        private Set<CombatBoundary.Cell> cells = Set.of();
        private CombatBoundary.Cell anchor;
        private long rendered = -100;
        private long warned = -100;
        private View(Player player) { this.player = player; world = player.getWorld(); }
    }
    private final JavaPlugin plugin;
    private final CombatService combat;
    private final Predicate<Location> safe;
    private final Map<UUID, View> views = new HashMap<>();
    private final int radius;
    private final int below;
    private final int above;
    private final BlockData glass = Material.RED_STAINED_GLASS.createBlockData();
    private final Component denied;
    private final BukkitTask ticker;
    private long tick;
    private boolean movingVehicle;

    public CombatBarrier(JavaPlugin plugin, CombatService combat, Predicate<Location> safe,
                         ConfigurationSection config, String message) {
        this.plugin = plugin;
        this.combat = combat;
        this.safe = safe;
        radius = config == null ? 6 : config.getInt("radius-blocks", 6);
        below = config == null ? 1 : config.getInt("blocks-below", 1);
        above = config == null ? 4 : config.getInt("blocks-above", 4);
        if (radius < 2 || radius > 8 || below < 0 || below > 3 || above < 2 || above > 6)
            throw new IllegalArgumentException("combat/combat.yml: barrier radius 2..8, below 0..3, above 2..6 required");
        if (message == null || message.isBlank()) throw new IllegalArgumentException("language/messages.yml: missing combat.barrier-denied");
        denied = MiniMessage.miniMessage().deserialize(message);
        ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1, 4);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private static CombatBoundary.Cell cell(Location location) {
        return new CombatBoundary.Cell(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private Predicate<CombatBoundary.Cell> query(World world) {
        Map<CombatBoundary.Cell, Boolean> cache = new HashMap<>();
        return point -> cache.computeIfAbsent(point, p -> safe.test(new Location(world, p.x(), p.y(), p.z())));
    }

    public boolean blocks(Player player, Location from, Location to) {
        if (!combat.tagged(player) || from.getWorld() == null || !from.getWorld().equals(to.getWorld())) return false;
        return CombatBoundary.enters(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ(),
                query(from.getWorld()));
    }

    private void tick() {
        tick += 4;
        for (View view : List.copyOf(views.values())) {
            if (!view.player.isOnline() || view.player.isDead() || !combat.tagged(view.player)
                    || !view.world.equals(view.player.getWorld())) clear(view.player);
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (combat.tagged(player)) refresh(player);
        }
    }

    public void refresh(Player player) { refreshAt(player, player.getLocation()); }

    private void refreshAt(Player player, Location location) {
        if (!combat.tagged(player) || player.isDead() || safe.test(location)) {
            clear(player);
            return;
        }
        View view = views.get(player.getUniqueId());
        if (view != null && !view.world.equals(player.getWorld())) { clear(player); view = null; }
        if (view == null) {
            view = new View(player);
            views.put(player.getUniqueId(), view);
        }
        var anchor = cell(location);
        if (anchor.equals(view.anchor) && tick - view.rendered < 20) return;
        var candidates = CombatBoundary.wall(anchor, radius, below, above, query(view.world));
        Set<CombatBoundary.Cell> next = new HashSet<>();
        Map<BlockPosition, BlockData> updates = new HashMap<>();
        for (var point : candidates) {
            if (!visible(player, view.world, point)) continue;
            var block = view.world.getBlockAt(point.x(), point.y(), point.z());
            // Solid terrain already blocks movement; never fake a tile entity or load distant chunks.
            if (!block.isPassable() || block.getState() instanceof TileState) continue;
            next.add(point);
            updates.put(position(point), glass);
        }
        for (var old : view.cells) {
            if (!next.contains(old) && visible(player, view.world, old))
                updates.put(position(old), view.world.getBlockAt(old.x(), old.y(), old.z()).getBlockData());
        }
        if (!updates.isEmpty()) player.sendMultiBlockChange(updates);
        view.cells = next;
        view.anchor = anchor;
        view.rendered = tick;
    }

    private static BlockPosition position(CombatBoundary.Cell point) {
        return Position.block(point.x(), point.y(), point.z());
    }

    private static boolean visible(Player player, World world, CombatBoundary.Cell point) {
        if (point.y() < world.getMinHeight() || point.y() >= world.getMaxHeight()
                || !world.isChunkLoaded(point.x() >> 4, point.z() >> 4)) return false;
        long key = ((long) (point.z() >> 4) << 32) | ((point.x() >> 4) & 0xffffffffL);
        return player.isChunkSent(key);
    }

    public void clear(Player player) {
        View view = views.remove(player.getUniqueId());
        if (view == null || !player.isOnline() || !view.world.equals(player.getWorld())) return;
        Map<BlockPosition, BlockData> updates = new HashMap<>();
        for (var point : view.cells) {
            if (visible(player, view.world, point)) updates.put(position(point),
                    view.world.getBlockAt(point.x(), point.y(), point.z()).getBlockData());
        }
        if (!updates.isEmpty()) player.sendMultiBlockChange(updates);
    }

    private void warn(Player player) {
        var view = views.get(player.getUniqueId());
        if (view != null && tick - view.warned < 20) return;
        player.sendActionBar(denied);
        if (view != null) view.warned = tick;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || event.getTo() == null || !event.hasChangedPosition()) return;
        if (blocks(event.getPlayer(), event.getFrom(), event.getTo())) {
            event.setCancelled(true);
            event.getPlayer().setVelocity(new Vector());
            // Paper can expose the attempted position during the callback; render at the retained position.
            refreshAt(event.getPlayer(), event.getFrom());
            warn(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void mount(io.papermc.paper.event.entity.EntityMoveEvent event) {
        if (!event.hasChangedPosition() || event.getEntity().getPassengers().isEmpty()) return;
        var riders = riders(event.getEntity());
        if (riders.stream().noneMatch(player -> blocks(player, event.getFrom(), event.getTo()))) return;
        event.setCancelled(true);
        event.getEntity().setVelocity(new Vector());
        for (Player player : riders) {
            if (combat.tagged(player)) { refreshAt(player, event.getFrom()); warn(player); }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void vehicle(VehicleMoveEvent event) {
        if (movingVehicle) return;
        List<Player> riders = riders(event.getVehicle());
        if (riders.stream().noneMatch(player -> blocks(player, event.getFrom(), event.getTo()))) return;
        movingVehicle = true;
        try {
            event.getVehicle().setVelocity(new Vector());
            if (!event.getVehicle().teleport(event.getFrom())) {
                // If a vehicle plugin refuses the correction, put its tagged riders back outside.
                for (Player player : riders) {
                    if (!combat.tagged(player)) continue;
                    player.leaveVehicle();
                    combat.correctMovement(player, event.getFrom());
                }
            }
            for (Player player : riders) if (combat.tagged(player)) { refresh(player); warn(player); }
        } finally { movingVehicle = false; }
    }

    private static List<Player> riders(Entity entity) {
        var players = new java.util.ArrayList<Player>();
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof Player player) players.add(player);
            players.addAll(riders(passenger));
        }
        return players;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        var view = views.get(event.getPlayer().getUniqueId());
        var point = cell(event.getClickedBlock().getLocation());
        if (view != null && view.cells.contains(point)) {
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            event.getPlayer().sendBlockChange(event.getClickedBlock().getLocation(), glass);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void breakGlass(BlockBreakEvent event) {
        var view = views.get(event.getPlayer().getUniqueId());
        if (view != null && view.cells.contains(cell(event.getBlock().getLocation()))) {
            event.setCancelled(true);
            event.getPlayer().sendBlockChange(event.getBlock().getLocation(), glass);
        }
    }

    @EventHandler public void death(PlayerDeathEvent event) { clear(event.getEntity()); }
    @EventHandler public void quit(PlayerQuitEvent event) { clear(event.getPlayer()); }
    @EventHandler public void worldChanged(PlayerChangedWorldEvent event) { clear(event.getPlayer()); }
    @Override public void close() {
        ticker.cancel();
        for (View view : List.copyOf(views.values())) clear(view.player);
    }
}
