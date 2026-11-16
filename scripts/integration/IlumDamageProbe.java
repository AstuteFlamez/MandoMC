package net.mandomc.verification;

import java.nio.file.Files;
import net.mandomc.gameplay.ilum.ParkourService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Only install in a disposable full-stack runtime on port 25591. */
public final class IlumDamageProbe extends JavaPlugin implements org.bukkit.event.Listener {
    private org.bukkit.event.entity.PlayerDeathEvent death;

    @org.bukkit.event.EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void death(org.bukkit.event.entity.PlayerDeathEvent event) {
        if (event.getEntity().equals(player)) death = event;
    }
    private NativeProbePlayers actors;
    private ParkourService parkour;
    private Player player;
    private int checks;

    private void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }

    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                check(getServer().getPort() == 25591, "Disposable port required");
                parkour = Bukkit.getServicesManager().load(ParkourService.class);
                check(parkour != null, "Ilum service ready");
                Bukkit.getPluginManager().registerEvents(this, this);
                actors = new NativeProbePlayers("IlumDamage");
                player = actors.create(parkour.start());
                parkour.begin(player);
                check(parkour.active(player), "Run active");
                verifyCheckpoint(0);
            } catch (Throwable error) { finish(error); }
        }, 160);
    }

    private void verifyCheckpoint(int checkpoint) {
        try {
            player.getPersistentDataContainer().set(new NamespacedKey("mandomc", "ilum_checkpoint"),
                    PersistentDataType.INTEGER, checkpoint);
            parkour.retry(player);
            Location saved = player.getLocation();
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            handle.getClass().getMethod("setPos", double.class, double.class, double.class)
                    .invoke(handle, saved.getX() + 2, saved.getY() + 2, saved.getZ());
            Location before = player.getLocation();
            boolean dropper = checkpoint == 3;
            check(parkour.damageTrial(player) != dropper, "Damage rule at " + checkpoint);
            var damage = new EntityDamageEvent(player, EntityDamageEvent.DamageCause.FALL,
                    DamageSource.builder(DamageType.FALL).build(), 2);
            Bukkit.getPluginManager().callEvent(damage);
            check(damage.isCancelled() == dropper, "Fall event cancellation at " + checkpoint);
            player.setHealth(20);
            player.setNoDamageTicks(0);
            player.damage(2, DamageSource.builder(DamageType.FALL).build());
            check(player.getHealth() == (dropper ? 20 : 18), "Actual health after damage at " + checkpoint);

            Location next = before.clone().add(.1, 0, 0);
            player.setFireTicks(40);
            var fireMove = new PlayerMoveEvent(player, before, next);
            Bukkit.getPluginManager().callEvent(fireMove);
            check(fireMove.getTo().equals(next), "Fire movement at " + checkpoint);
            player.setFireTicks(0);
            Location below = before.clone(); below.setY(-100);
            var fallMove = new PlayerMoveEvent(player, before, below);
            Bukkit.getPluginManager().callEvent(fallMove);
            check(fallMove.getTo().equals(dropper ? saved : below), "Below-course movement at " + checkpoint);

            var respawn = new PlayerRespawnEvent(player, parkour.start(), false);
            Bukkit.getPluginManager().callEvent(respawn);
            check(respawn.getRespawnLocation().equals(saved), "Death respawn checkpoint at " + checkpoint);
            var hit = new org.bukkit.event.entity.EntityDamageByEntityEvent(player, player,
                    EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                    DamageSource.builder(DamageType.PLAYER_ATTACK).withDirectEntity(player).withCausingEntity(player).build(), 2);
            Bukkit.getPluginManager().callEvent(hit);
            check(hit.isCancelled(), "PvP remains blocked at " + checkpoint);

            Bukkit.getScheduler().runTaskLater(this, () -> {
                try {
                    check(player.getLocation().distanceSquared(dropper ? saved : before) < .01,
                            "Damage preserves checkpoint position at " + checkpoint);
                    check(parkour.run(player).checkpoint() == checkpoint, "Progress preserved at " + checkpoint);
                    if (checkpoint < 4) verifyCheckpoint(checkpoint + 1);
                    else {
                        player.setNoDamageTicks(0);
                        player.damage(1000, DamageSource.builder(DamageType.FALL).build());
                        check(player.isDead() && death != null, "Lethal environmental damage reaches death");
                        check(death.getKeepInventory() && death.getDrops().isEmpty() && death.getKeepLevel(),
                                "Death preserves course inventory and level");
                        check(parkour.active(player) && parkour.run(player).checkpoint() == 4,
                                "Death preserves saved run");
                        finish(null);
                    }
                } catch (Throwable error) { finish(error); }
            }, 2);
        } catch (Throwable error) { finish(error); }
    }

    private void finish(Throwable error) {
        try {
            if (actors != null) actors.close();
            Files.createDirectories(getDataFolder().toPath());
            Files.writeString(getDataFolder().toPath().resolve(error == null ? "PASSED.txt" : "FAILED.txt"),
                    error == null ? checks + " native Ilum damage assertions passed\n" : error.toString());
            if (error == null) getLogger().info("ILUM DAMAGE PASSED: " + checks);
            else getLogger().log(java.util.logging.Level.SEVERE, "ILUM DAMAGE FAILED", error);
        } catch (Exception failure) { getLogger().log(java.util.logging.Level.SEVERE, "Probe cleanup failed", failure); }
        Bukkit.shutdown();
    }
}
