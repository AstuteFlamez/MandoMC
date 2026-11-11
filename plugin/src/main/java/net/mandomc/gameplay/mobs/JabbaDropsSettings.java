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

    public record Salvage(List<String> pool, int minimum, int maximum) {
        public Salvage { pool=List.copyOf(pool); }
    }
    public record Drop(String weapon, double weaponChance, String ammo, double ammoChance, int ammoRolls, Salvage salvage) {
        public Drop(String weapon, double weaponChance, String ammo, double ammoChance, int ammoRolls) {
            this(weapon,weaponChance,ammo,ammoChance,ammoRolls,new Salvage(List.of(),0,0));
        }
        public List<String> roll(RandomGenerator random) {
            var result = new ArrayList<String>();
            if (random.nextDouble() < weaponChance) result.add(weapon);
            for (int i = 0; i < ammoRolls; i++) {
                if (random.nextDouble() < ammoChance) result.add(ammo);
            }
            if(!salvage.pool().isEmpty()) {
                int count=random.nextInt(salvage.minimum(),salvage.maximum()+1);
                for(int i=0;i<count;i++)result.add(salvage.pool().get(random.nextInt(salvage.pool().size())));
            }
            return List.copyOf(result);
        }
    }

    public JabbaDropsSettings {
        mobs = Map.copyOf(mobs);
    }

    public static JabbaDropsSettings read(ConfigurationSection config) {
        var section = config.getConfigurationSection("drops");
        if (config.getInt("schema-version") != 1 || section == null || (!section.getKeys(false).containsAll(GUARDS) || !java.util.Set.of("GamorreanGuard", "QuarrenGuard", "TwilekGuard", "BobaFett", "JawaBrown").containsAll(section.getKeys(false)))) {
            throw new IllegalArgumentException("mobs/drops/jabba.yml: expected schema 1 and all three generic guard drops and optional BobaFett/JawaBrown");
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
            var pool=rule.getStringList("salvage.pool");
            int minimum=rule.getInt("salvage.minimum",0),maximum=rule.getInt("salvage.maximum",0);
            if(rule.contains("salvage") && (!rule.isInt("salvage.minimum") || !rule.isInt("salvage.maximum") || pool.isEmpty() || pool.size()>32 || pool.stream().anyMatch(id->!id.matches("[a-z0-9_]+"))
                    || minimum<1 || maximum<minimum || maximum>8))
                throw new IllegalArgumentException("mobs/drops/jabba.yml: invalid salvage pool/count for "+mob);
            result.put(mob, new Drop(weapon, weaponChance, ammo, ammoChance, rolls,new Salvage(pool,minimum,maximum)));
        }
        return new JabbaDropsSettings(result);
    }

    private static boolean chance(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }
}
