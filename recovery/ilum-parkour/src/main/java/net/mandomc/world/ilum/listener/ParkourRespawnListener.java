package net.mandomc.world.ilum.listener;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import net.mandomc.world.ilum.model.ParkourSession;
import net.mandomc.world.ilum.manager.ParkourManager;

import org.bukkit.entity.Player;

public class ParkourRespawnListener implements Listener {

    private final ParkourManager parkourManager;

    public ParkourRespawnListener(ParkourManager parkourManager) {
        this.parkourManager = parkourManager;
    }

    @EventHandler
    public void onVoidFall(PlayerMoveEvent event) {

        Player player = event.getPlayer();

        if (!parkourManager.hasSession(player)) return;

        ParkourSession session = parkourManager.getSession(player);

        // If player falls into void
        if (player.getLocation().getY() < -100) {
            teleportToCheckpoint(player, session);
            return;
        }

        // If player is on fire
        if (player.getFireTicks() > 0) {
            player.setFireTicks(0);
            teleportToCheckpoint(player, session);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {

        if (!(event.getEntity() instanceof Player player)) return;

        if (!parkourManager.hasSession(player)) return;
        event.setCancelled(true);
        player.setFireTicks(0);
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20);
        teleportToCheckpoint(player, parkourManager.getSession(player));
    }

    private void teleportToCheckpoint(Player player, ParkourSession session) {

        Location checkpoint = session.getCheckpoint();

        if (checkpoint == null) {
            checkpoint = session.getStartLocation();
        }
        if (checkpoint == null) {
            parkourManager.exitParkour(player);
            return;
        }

        player.teleport(checkpoint);
        player.setFallDistance(0);

        player.playSound(
            player.getLocation(),
            Sound.ENTITY_ENDERMAN_TELEPORT,
            1f,
            1f
        );
    }
}