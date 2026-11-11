package net.mandomc.gameplay.crates;

import java.util.ArrayList;
import org.bukkit.configuration.ConfigurationSection;

public record CrateDefinition(String id, String name, String keyItem, boolean virtual, WeightedPool<Reward> rewards) {
    public record Reward(String item, int amount) { }

    public static CrateDefinition read(String id, ConfigurationSection section) {
        if (section == null || !id.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid crate " + id);
        String key = section.getString("key-item", "");
        String name = section.getString("name", "");
        String mode = section.getString("key-mode", "physical");
        if (!key.matches("[a-z0-9_]+") || name.isBlank() || !java.util.Set.of("physical", "virtual").contains(mode)) {
            throw new IllegalArgumentException("crates.yml: invalid key/name for " + id);
        }
        var rewards = new ArrayList<WeightedPool.Entry<Reward>>();
        for (var raw : section.getMapList("rewards")) {
            String item = String.valueOf(raw.get("item"));
            if (!item.matches("[a-z0-9_]+") || !(raw.get("amount") instanceof Number amount)
                    || !(raw.get("weight") instanceof Number weight) || amount.doubleValue()!=amount.intValue()
                    || weight.doubleValue()!=weight.intValue() || amount.intValue() < 1 || amount.intValue() > 64) {
                throw new IllegalArgumentException("crates.yml: invalid reward for " + id);
            }
            rewards.add(new WeightedPool.Entry<>(new Reward(item, amount.intValue()), weight.intValue()));
        }
        if (rewards.size() > 28) throw new IllegalArgumentException("Crate preview supports up to 28 rewards");
        return new CrateDefinition(id, name, key, mode.equals("virtual"), new WeightedPool<>(rewards));
    }
}
