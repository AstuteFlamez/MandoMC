package net.mandomc.gameplay.mobs;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

/** The tunable animal rules; native entity age, love ticks, owner and equipment own saved state. */
public record HerdSettings(Material banthaFood, Material dewbackFood, double tameChance, double heal,
        double babyGrowth, int loveTicks, int breedCooldown) {
    public static HerdSettings read(ConfigurationSection config) {
        for (String key : new String[] {"tame-chance", "healing", "baby-growth-fraction"}) {
            if (config.contains(key) && !(config.get(key) instanceof Number)) {
                throw new IllegalArgumentException("mobs/herd.yml: " + key + " must be numeric");
            }
        }
        for (String key : new String[] {"love-ticks", "breed-cooldown-ticks"}) {
            if (config.contains(key) && !config.isInt(key)) {
                throw new IllegalArgumentException("mobs/herd.yml: " + key + " must be an integer");
            }
        }
        Material bantha = Material.matchMaterial(config.getString("bantha-food", ""));
        Material dewback = Material.matchMaterial(config.getString("dewback-food", ""));
        double chance = config.getDouble("tame-chance", 1.0 / 3);
        double heal = config.getDouble("healing", 4);
        double growth = config.getDouble("baby-growth-fraction", .1);
        int love = config.getInt("love-ticks", 600);
        int cooldown = config.getInt("breed-cooldown-ticks", 6000);
        if (config.getInt("schema-version") != 1 || bantha == null || dewback == null
                || !bantha.isItem() || !dewback.isItem() || !Double.isFinite(chance) || chance <= 0 || chance > 1
                || !Double.isFinite(heal) || heal <= 0 || heal > 100 || !Double.isFinite(growth) || growth <= 0 || growth > 1
                || love < 60 || love > 24000 || cooldown < 1 || cooldown > 24000) {
            throw new IllegalArgumentException("mobs/herd.yml: invalid food, taming, feeding or breeding settings");
        }
        return new HerdSettings(bantha, dewback, chance, heal, growth, love, cooldown);
    }

    public Material food(String type) { return "Bantha".equals(type) ? banthaFood : dewbackFood; }
}
