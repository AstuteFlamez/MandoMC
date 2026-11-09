package net.mandomc.gameplay.mobs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import org.bukkit.configuration.ConfigurationSection;

/** Immutable legacy loot rules; one weapon roll and independent single-cell rolls per actual death. */
public record JabbaDropsSettings(Map<String, Drop> mobs) {
    private static final java.util.Set<String> GUARDS = java.util.Set.of(
            "GamorreanGuard", "QuarrenGuard", "TwilekGuard");

    public record Drop(String weapon, double weaponChance, String ammo, double ammoChance, int ammoRolls) {
        public List<String> roll(RandomGenerator random) {
            var result = new ArrayList<String>();
            if (random.nextDouble() < weaponChance) result.add(weapon);
            for (int i = 0; i < ammoRolls; i++) {
                if (random.nextDouble() < ammoChance) result.add(ammo);
            }
            return List.copyOf(result);
        }
    }

    public JabbaDropsSettings {
        mobs = Map.copyOf(mobs);
    }

    public static JabbaDropsSettings read(ConfigurationSection config) {
        var section = config.getConfigurationSection("drops");
        if (config.getInt("schema-version") != 1 || section == null || (!section.getKeys(false).containsAll(GUARDS) || !java.util.Set.of("GamorreanGuard", "QuarrenGuard", "TwilekGuard", "BobaFett").containsAll(section.getKeys(false)))) {
            throw new IllegalArgumentException("mobs/drops/jabba.yml: expected schema 1 and all three generic guard drops and optional BobaFett");
        }
        var result = new LinkedHashMap<String, Drop>();
        for (String mob : section.getKeys(false)) {
            var rule = section.getConfigurationSection(mob);
            if (rule == null) throw new IllegalArgumentException("mobs/drops/jabba.yml: missing drop section " + mob);
            String weapon = rule.getString("weapon", "");
            String ammo = rule.getString("ammo", "");
            double weaponChance = rule.getDouble("weapon-chance", Double.NaN);
            double ammoChance = rule.getDouble("ammo-chance", 0);
            int rolls = rule.getInt("ammo-rolls", -1);
            if (!weapon.matches("[a-z0-9_]+") || !chance(weaponChance) || !chance(ammoChance)
                    || !rule.isInt("ammo-rolls") || rolls < 0 || rolls > 64
                    || (rolls > 0 && !ammo.matches("[a-z0-9_]+"))) {
                throw new IllegalArgumentException("mobs/drops/jabba.yml: invalid items, chances (0–1) or ammo-rolls (0–64) for " + mob);
            }
            result.put(mob, new Drop(weapon, weaponChance, ammo, ammoChance, rolls));
        }
        return new JabbaDropsSettings(result);
    }

    private static boolean chance(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }
}
