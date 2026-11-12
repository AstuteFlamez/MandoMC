package net.mandomc.integrations.mobs;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.ticxo.modelengine.api.events.AddModelEvent;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.mandomc.gameplay.mobs.HerdAge;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** A native goal/pathfinder, so food attraction yields to panic and resumes ordinary wandering. */
public final class HerdTempt implements Listener {
    private final JavaPlugin plugin;
    private final MythicMobAccess mobs;
    private final Set<UUID> attached = new HashSet<>();
    private final Material banthaFood;
    private final Material dewbackFood;

    public HerdTempt(JavaPlugin plugin, MythicMobAccess mobs, Material banthaFood, Material dewbackFood) {
        this.plugin = plugin;
        this.mobs = mobs;
        this.banthaFood = banthaFood;
        this.dewbackFood = dewbackFood;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (var world : Bukkit.getWorlds()) for (var entity : world.getLivingEntities()) {
            if (entity instanceof Mob mob) attach(mob);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void model(AddModelEvent event) {
        String id = event.getModel().getBlueprint().getName();
        if (!"bantha".equals(id) && !"dewback".equals(id)) return;
        UUID uuid = event.getTarget().getBase().getUUID();
        // Mythic installs its configured goals after attaching the model during spawn/load.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (Bukkit.getEntity(uuid) instanceof Mob mob) attach(mob);
        });
    }

    private void attach(Mob mob) {
        String type = mobs.type(mob);
        if (!HerdAge.herd(type)) return;
        Material food = "Bantha".equals(type) ? banthaFood : dewbackFood;
        var goals = Bukkit.getMobGoals();
        goals.removeGoal(mob, FoodGoal.KEY);
        goals.addGoal(mob, 3, new FoodGoal(mob, food));
        attached.add(mob.getUniqueId());
    }

    @EventHandler public void unloaded(EntitiesUnloadEvent event) {
        for (var entity : event.getEntities()) attached.remove(entity.getUniqueId());
    }

    /** Paper removes native goals with the entity; explicitly remove ours on plugin disable too. */
    public void close() {
        for (UUID uuid : attached) if (Bukkit.getEntity(uuid) instanceof Mob mob) {
            Bukkit.getMobGoals().removeGoal(mob, FoodGoal.KEY);
        }
        attached.clear();
    }

    public static final class FoodGoal implements Goal<Mob> {
        public static final GoalKey<Mob> KEY = GoalKey.of(Mob.class, new NamespacedKey("mandomc", "herd_food"));
        private final Mob mob;
        private final Material food;
        private Player target;
        private int ticks;

        public FoodGoal(Mob mob, Material food) {
            this.mob = mob;
            this.food = food;
        }

        public boolean accepts(Player player) {
            return player.isValid() && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR
                    && player.getWorld().equals(mob.getWorld()) && mob.getPassengers().isEmpty()
                    && mob.getLocation().distanceSquared(player.getLocation()) <= 100
                    && (player.getInventory().getItemInMainHand().getType() == food
                        || player.getInventory().getItemInOffHand().getType() == food)
                    && mob.hasLineOfSight(player);
        }

        @Override public boolean shouldActivate() {
            target = mob.getWorld().getNearbyPlayers(mob.getLocation(), 10).stream().filter(this::accepts)
                    .min(Comparator.comparingDouble(player -> player.getLocation().distanceSquared(mob.getLocation())))
                    .orElse(null);
            return target != null;
        }

        @Override public boolean shouldStayActive() { return target != null && accepts(target); }
        @Override public void start() { ticks = 0; }
        @Override public void stop() { target = null; mob.getPathfinder().stopPathfinding(); }
        @Override public void tick() {
            if (!shouldStayActive()) { stop(); return; }
            mob.lookAt(target, 30, 30);
            if (mob.getLocation().distanceSquared(target.getLocation()) < 6.25) {
                mob.getPathfinder().stopPathfinding();
            } else if (ticks++ % 10 == 0) {
                mob.getPathfinder().moveTo(target, 1.1);
            }
        }
        @Override public GoalKey<Mob> getKey() { return KEY; }
        @Override public EnumSet<GoalType> getTypes() { return EnumSet.of(GoalType.MOVE, GoalType.LOOK); }
    }
}
