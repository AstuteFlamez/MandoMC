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
    public void onDeathPrevent(EntityDamageEvent event) {

        if (!(event.getEntity() instanceof Player player)) return;

        if (!parkourManager.hasSession(player)) return;

        ParkourSession session = parkourManager.getSession(player);

        Location checkpoint = session.getCheckpoint();
        if (checkpoint == null) {
            checkpoint = session.getStartLocation();
        }

        // Handle fire/lava damage instantly
        switch (event.getCause()) {
            case FIRE:
            case FIRE_TICK:
            case LAVA:
            case HOT_FLOOR:

                event.setCancelled(true);

                player.setFireTicks(0);

                player.teleport(checkpoint);

                player.playSound(
                    player.getLocation(),
                    Sound.ENTITY_ENDERMAN_TELEPORT,
                    1f,
                    1f
                );

                return;
            default:
                break;
        }

        double finalHealth = player.getHealth() - event.getFinalDamage();

        if (finalHealth > 0) return;

        // Prevent death
        event.setCancelled(true);

        // Restore player stats
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setSaturation(20);

        // Teleport to checkpoint
        player.teleport(checkpoint);

        player.playSound(
            player.getLocation(),
            Sound.ENTITY_ENDERMAN_TELEPORT,
            1f,
            1f
        );
    }

    private void teleportToCheckpoint(Player player, ParkourSession session) {

        Location checkpoint = session.getCheckpoint();

        if (checkpoint == null) {
            checkpoint = session.getStartLocation();
        }

        player.teleport(checkpoint);

        player.playSound(
            player.getLocation(),
            Sound.ENTITY_ENDERMAN_TELEPORT,
            1f,
            1f
        );
    }
}