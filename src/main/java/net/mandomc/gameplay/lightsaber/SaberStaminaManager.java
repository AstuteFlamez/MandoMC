package net.mandomc.gameplay.lightsaber;

import net.mandomc.core.LangManager;
import net.mandomc.gameplay.lightsaber.config.LightsaberConfig;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Applies lightsaber stamina consumption using shield durability.
 */
public final class SaberStaminaManager {

    private final LightsaberConfig config;

    public SaberStaminaManager(LightsaberConfig config) {
        this.config = config;
    }

    public void consumeOnDeflect(Player player, ItemStack saberItem) {
        if (!config.isStaminaEnabled() || !SaberItemUtil.isSaberShield(saberItem)) return;
        applyDamage(player, saberItem, config.getDurabilityPerDeflect(), true);
    }

    public boolean shouldBlockExternalDurability() {
        return config.isStaminaEnabled();
    }

    private void applyDamage(Player player, ItemStack saberItem, int damageDelta, boolean allowOverheat) {
        ItemMeta rawMeta = saberItem.getItemMeta();
        if (!(rawMeta instanceof Damageable damageable)) return;

        int maxDurability = saberItem.getType().getMaxDurability();
        if (maxDurability <= 1) return;

        int currentDamage = Math.max(0, damageable.getDamage());
        int nextDamage = Math.min(maxDurability - 1, currentDamage + Math.max(0, damageDelta));
        int threshold = Math.min(maxDurability - 1, config.getLowDurabilityThreshold());
        int overheatBoundary = Math.max(0, maxDurability - threshold);

        if (allowOverheat && nextDamage >= overheatBoundary) {
            triggerOverheat(player, saberItem, damageable);
            return;
        }

        damageable.setDamage(nextDamage);
        saberItem.setItemMeta((ItemMeta) damageable);
    }

    private void triggerOverheat(Player player, ItemStack saberItem, Damageable damageable) {
        damageable.setDamage(0);
        saberItem.setItemMeta((ItemMeta) damageable);
        player.setCooldown(Material.SHIELD, config.getOverheatDisableTicks());
        player.clearActiveItem();

        if (config.isOverheatDebuffEnabled()) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SLOWNESS,
                    config.getOverheatSlownessTicks(),
                    config.getOverheatSlownessAmplifier(),
                    true,
                    false,
                    true
            ));
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.WEAKNESS,
                    config.getOverheatWeaknessTicks(),
                    config.getOverheatWeaknessAmplifier(),
                    true,
                    false,
                    true
            ));
        }
        player.sendMessage(LangManager.get("lightsabers.stamina-overheat"));
    }
}
