package net.mandomc.gameplay.lightsaber.listener;

import me.deecaad.weaponmechanics.weapon.projectile.weaponprojectile.WeaponProjectile;
import me.deecaad.weaponmechanics.weapon.weaponevents.ProjectileHitEntityEvent;
import net.mandomc.gameplay.lightsaber.SaberItemUtil;
import net.mandomc.gameplay.lightsaber.SaberStaminaManager;

import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * Handles projectile deflection when a player is blocking with a lightsaber.
 *
 * Supports both WeaponMechanics projectiles and vanilla Bukkit projectiles.
 * Incoming projectiles are redirected in the direction the player is facing,
 * and visual and sound effects are applied.
 */
public class SaberDeflectListener implements Listener {

    private static final double WM_REFLECT_SPEED = 3.0;
    private static final double VANILLA_REFLECT_SPEED = 1.6;
    private final SaberStaminaManager staminaManager;

    public SaberDeflectListener(SaberStaminaManager staminaManager) {
        this.staminaManager = staminaManager;
    }

    /**
     * Handles WeaponMechanics projectile hits.
     *
     * Cancels the hit and reflects the projectile.
     *
     * @param event the WeaponMechanics projectile hit event
     */
    @EventHandler
    public void onWeaponProjectileHit(ProjectileHitEntityEvent event) {

        if (!(event.getEntity() instanceof Player player)) return;

        if (!isValidSaberBlock(player)) return;

        event.setCancelled(true);

        WeaponProjectile projectile = event.getProjectile();
        projectile.setMotion(getReflectVector(player, WM_REFLECT_SPEED));

        staminaManager.consumeOnDeflect(player, player.getInventory().getItemInMainHand());
        playEffects(player);
    }

    /**
     * Handles vanilla Bukkit projectile hits.
     *
     * Redirects the projectile and assigns the player as the new shooter.
     *
     * @param event the projectile hit event
     */
    @EventHandler
    public void onVanillaProjectileHit(ProjectileHitEvent event) {

        if (!(event.getHitEntity() instanceof Player player)) return;

        if (!isValidSaberBlock(player)) return;

        Projectile projectile = event.getEntity();

        projectile.setVelocity(getReflectVector(player, VANILLA_REFLECT_SPEED));
        projectile.setShooter(player);

        staminaManager.consumeOnDeflect(player, player.getInventory().getItemInMainHand());
        playEffects(player);
    }

    /**
     * Validates whether a player is holding a lightsaber and actively blocking.
     *
     * @param player the player to check
     * @return true if the player can deflect
     */
    private boolean isValidSaberBlock(Player player) {

        ItemStack item = player.getInventory().getItemInMainHand();

        return SaberItemUtil.isSaberShield(item) && player.isBlocking();
    }

    /**
     * Creates a reflection vector based on player direction.
     *
     * @param player the player
     * @param speed the reflection speed multiplier
     * @return the normalized reflection vector
     */
    private Vector getReflectVector(Player player, double speed) {
        return player.getLocation()
                .getDirection()
                .normalize()
                .multiply(speed);
    }

    /**
     * Plays deflection sound and particle effects.
     *
     * @param player the player performing the deflect
     */
    private void playEffects(Player player) {

        player.getWorld().playSound(
                player.getLocation(),
                "melee.lightsaber.hit",
                SoundCategory.PLAYERS,
                1f,
                1.2f
        );

        player.getWorld().spawnParticle(
                Particle.SWEEP_ATTACK,
                player.getLocation().add(0, 1, 0),
                10
        );

        player.getWorld().spawnParticle(
                Particle.CRIT,
                player.getLocation().add(0, 1, 0),
                18,
                0.2, 0.2, 0.2
        );
    }
}