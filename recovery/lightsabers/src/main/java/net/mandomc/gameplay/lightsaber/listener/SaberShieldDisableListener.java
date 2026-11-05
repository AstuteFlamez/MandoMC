package net.mandomc.gameplay.lightsaber.listener;

import net.mandomc.MandoMC;
import net.mandomc.gameplay.lightsaber.SaberItemUtil;
import net.mandomc.gameplay.lightsaber.config.LightsaberConfig;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;

/**
 * Prevents vanilla axe shield disable from affecting SABER-tagged shields.
 */
public final class SaberShieldDisableListener implements Listener {

    private final LightsaberConfig config;

    public SaberShieldDisableListener(LightsaberConfig config) {
        this.config = config;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAxeHit(EntityDamageByEntityEvent event) {
        if (!config.isAxeDisablePreventionEnabled()) return;
        if (!(event.getEntity() instanceof Player defender)) return;
        if (!(event.getDamager() instanceof LivingEntity attacker)) return;
        if (!defender.isBlocking()) return;

        ItemStack saber = defender.getInventory().getItemInMainHand();
        if (!SaberItemUtil.isSaberShield(saber)) return;

        EntityEquipment equipment = attacker.getEquipment();
        if (equipment == null) return;

        ItemStack weapon = equipment.getItemInMainHand();
        if (!isAxe(weapon)) return;

        Bukkit.getScheduler().runTask(MandoMC.getInstance(), () -> {
            if (!defender.isOnline()) return;
            if (!SaberItemUtil.isSaberShield(defender.getInventory().getItemInMainHand())) return;
            defender.setCooldown(Material.SHIELD, 0);
        });
    }

    private boolean isAxe(ItemStack item) {
        return item != null && item.getType().name().endsWith("_AXE");
    }
}
