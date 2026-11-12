package net.mandomc.integrations.mobs;

import com.destroystokyo.paper.entity.ai.*;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Mob;

/** Native navigator courtship and parent following, with one server-thread birth decision. */
public final class HerdCourtship implements Goal<Mob> {
    public static final GoalKey<Mob> PAIR = GoalKey.of(Mob.class, new NamespacedKey("mandomc", "herd_pair"));
    public static final GoalKey<Mob> PARENT = GoalKey.of(Mob.class, new NamespacedKey("mandomc", "herd_parent"));
    private final Horse horse;
    private final MythicMobAccess mobs;
    private final HerdAnimals animals;
    private Horse partner;
    private int started;
    private int repath;
    public HerdCourtship(Horse horse, MythicMobAccess mobs, HerdAnimals animals) {
        this.horse = horse; this.mobs = mobs; this.animals = animals;
    }
    @Override public boolean shouldActivate() {
        if (horse.getAge() != 0 || !horse.isLoveMode() || !horse.isTamed()) return false;
        partner = horse.getNearbyEntities(8, 4, 8).stream().filter(Horse.class::isInstance).map(Horse.class::cast)
                .filter(other -> animals.pair(horse, other))
                .min(Comparator.comparingDouble(other -> horse.getLocation().distanceSquared(other.getLocation())))
                .orElse(null);
        return partner != null;
    }
    @Override public boolean shouldStayActive() {
        return partner != null && animals.pair(horse, partner)
                && horse.getLocation().distanceSquared(partner.getLocation()) <= 256;
    }
    @Override public void start() { started = Bukkit.getCurrentTick(); repath = 0; }
    @Override public void stop() { partner = null; horse.getPathfinder().stopPathfinding(); }
    @Override public void tick() {
        if (!shouldStayActive()) return;
        horse.lookAt(partner, 30, 30);
        if (repath++ % 10 == 0) horse.getPathfinder().moveTo(partner, 1);
        if (Bukkit.getCurrentTick() - started >= 60 && horse.getUniqueId().compareTo(partner.getUniqueId()) < 0
                && horse.getLocation().distanceSquared(partner.getLocation()) <= 9) animals.breed(horse, partner);
    }
    @Override public GoalKey<Mob> getKey() { return PAIR; }
    @Override public EnumSet<GoalType> getTypes() { return EnumSet.of(GoalType.MOVE, GoalType.LOOK); }

    public static final class Parent implements Goal<Mob> {
        private final Horse baby;
        private final MythicMobAccess mobs;
        private Horse parent;
        private int repath;
        public Parent(Horse baby, MythicMobAccess mobs) { this.baby = baby; this.mobs = mobs; }
        private boolean valid(Horse adult) {
            return adult.isValid() && !adult.isDead() && adult.isAdult() && mobs.type(baby).equals(mobs.type(adult))
                    && Objects.equals(baby.getOwnerUniqueId(), adult.getOwnerUniqueId());
        }
        @Override public boolean shouldActivate() {
            if (baby.isAdult()) return false;
            parent = baby.getNearbyEntities(16, 8, 16).stream().filter(Horse.class::isInstance).map(Horse.class::cast)
                    .filter(this::valid).min(Comparator.comparingDouble(adult -> baby.getLocation().distanceSquared(adult.getLocation())))
                    .orElse(null);
            return parent != null && baby.getLocation().distanceSquared(parent.getLocation()) >= 9;
        }
        @Override public boolean shouldStayActive() {
            return !baby.isAdult() && parent != null && valid(parent)
                    && baby.getLocation().distanceSquared(parent.getLocation()) >= 4
                    && baby.getLocation().distanceSquared(parent.getLocation()) <= 256;
        }
        @Override public void start() { repath = 0; }
        @Override public void stop() { parent = null; baby.getPathfinder().stopPathfinding(); }
        @Override public void tick() {
            if (shouldStayActive() && repath++ % 10 == 0) baby.getPathfinder().moveTo(parent, 1.1);
        }
        @Override public GoalKey<Mob> getKey() { return PARENT; }
        @Override public EnumSet<GoalType> getTypes() { return EnumSet.of(GoalType.MOVE); }
    }
}
