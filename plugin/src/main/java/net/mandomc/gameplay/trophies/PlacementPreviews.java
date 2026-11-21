package net.mandomc.gameplay.trophies;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import net.mandomc.gameplay.trophies.TrophyJournal.Cell;
import java.util.UUID;
import java.util.function.Function;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** A private display has no hitbox, interaction entity, barrier, or journal entry. */
final class PlacementPreviews implements Listener, AutoCloseable {
    record Target(PlacementPlan plan, NamespacedKey model, boolean allowed) {}
    private record Shown(Target target, ItemDisplay display) {}
    private final JavaPlugin plugin;
    private final Function<Player, Target> resolve;
    private final Map<UUID, Shown> shown = new HashMap<>();
    private final Map<UUID, Set<Cell>> lastPlacement = new HashMap<>();
    private final BukkitTask task;

    void placed(Player player, Cell clicked, PlacementPlan plan) {
        clear(player);
        var cells = new HashSet<>(plan.cells());
        cells.add(clicked);
        lastPlacement.put(player.getUniqueId(), cells);
    }

    boolean dismissed(Player player, Cell clicked) {
        var cells = lastPlacement.get(player.getUniqueId());
        if (cells == null) return false;
        if (cells.contains(clicked)) return true;
        lastPlacement.remove(player.getUniqueId());
        return false;
    }

    PlacementPreviews(JavaPlugin plugin, Function<Player, Target> resolve) {
        this.plugin = plugin;
        this.resolve = resolve;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::refresh), 1, 2);
    }

    static ItemStack visual(NamespacedKey model) {
        var stack = ItemStack.of(Material.ECHO_SHARD);
        stack.editMeta(meta -> meta.setItemModel(model));
        return stack;
    }

    private void refresh(Player player) {
        Target next = resolve.apply(player);
        if (next == null) {
            clear(player);
            return;
        }
        var previous = shown.get(player.getUniqueId());
        if (previous != null && previous.display().isValid() && next.equals(previous.target())) return;
        var location = next.plan().center(player.getWorld());
        ItemDisplay display;
        if (previous == null || !previous.display().isValid() || previous.display().getWorld() != location.getWorld()) {
            clear(player);
            display = player.getWorld().spawn(location, ItemDisplay.class, entity -> {
                entity.setVisibleByDefault(false); // Set before the tracker sees the spawn.
                entity.setPersistent(false);
                entity.setGravity(false);
                entity.setInvulnerable(true);
                entity.setShadowRadius(0);
                entity.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            });
            player.showEntity(plugin, display);
        } else {
            display = previous.display();
            if (!previous.target().plan().equals(next.plan())) display.teleport(location);
        }
        if (previous == null || display != previous.display() || next.allowed() != previous.target().allowed()
                || !next.model().equals(previous.target().model())) {
            String prefix = next.allowed() ? "placement/blue/" : "placement/red/";
            display.setItemStack(visual(new NamespacedKey(next.model().getNamespace(), prefix + next.model().getKey())));
        }
        shown.put(player.getUniqueId(), new Shown(next, display));
    }

    void clear(Player player) {
        var previous = shown.remove(player.getUniqueId());
        if (previous != null) previous.display().remove();
    }

    @EventHandler public void quit(PlayerQuitEvent event) { reset(event.getPlayer()); }
    @EventHandler public void world(PlayerChangedWorldEvent event) { reset(event.getPlayer()); }
    @EventHandler public void slot(PlayerItemHeldEvent event) { reset(event.getPlayer()); }

    void reset(Player player) {
        clear(player);
        lastPlacement.remove(player.getUniqueId());
    }

    @Override public void close() {
        task.cancel();
        HandlerList.unregisterAll(this);
        shown.values().forEach(value -> value.display().remove());
        shown.clear();
    }
}
