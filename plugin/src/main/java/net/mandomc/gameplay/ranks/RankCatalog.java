package net.mandomc.gameplay.ranks;

import java.util.*;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.configuration.ConfigurationSection;

/** Validated rank graph. Membership identity is independent of badge artwork and display names. */
public final class RankCatalog {
    public record Rank(String id, String name, int order, String badge, String icon, TextColor color,
                       List<String> parents, Map<String, Boolean> permissions) {
        public Component badge(boolean small) {
            return Component.text(small ? icon : badge, NamedTextColor.WHITE).font(Key.key("mandomc:ranks"));
        }
        public Component playerName(String player) {
            return badge(true).append(Component.text(" " + player, color).font(Key.key("minecraft:default")));
        }
    }
    private final Map<String, Rank> ranks;
    public RankCatalog(ConfigurationSection config) {
        if (config.getInt("schema-version", -1) != 1) throw new IllegalArgumentException("ranks.yml: expected schema-version 1");
        var section = config.getConfigurationSection("ranks");
        if (section == null) throw new IllegalArgumentException("ranks.yml: missing ranks");
        var result = new LinkedHashMap<String, Rank>();
        var orders = new HashSet<Integer>();
        for (String id : section.getKeys(false)) {
            var value = section.getConfigurationSection(id);
            if (!id.matches("[a-z][a-z0-9_]{0,31}") || value == null) throw new IllegalArgumentException("ranks.yml: invalid rank " + id);
            int order = value.getInt("order", -1);
            var color = TextColor.fromHexString(value.getString("color", ""));
            if (order < 0 || !orders.add(order) || color == null) throw new IllegalArgumentException("ranks.yml: invalid order/color for " + id);
            var nodes = new LinkedHashMap<String, Boolean>();
            for (String entry : value.getStringList("permissions")) {
                boolean allowed = !entry.startsWith("-");
                String key = allowed ? entry : entry.substring(1);
                if (!key.matches("(?:[a-z0-9_-]+\\.)*[a-z0-9_*-]+|\\*"))
                    throw new IllegalArgumentException("ranks.yml: invalid permission " + key);
                nodes.put(key, allowed);
            }
            for (String key : List.of("name", "badge", "icon")) if (value.getString(key, "").isBlank())
                throw new IllegalArgumentException("ranks.yml: missing " + id + "." + key);
            result.put(id, new Rank(id, value.getString("name"), order, value.getString("badge"), value.getString("icon"),
                    color, List.copyOf(value.getStringList("inherits")), Map.copyOf(nodes)));
        }
        if (!result.containsKey("default")) throw new IllegalArgumentException("ranks.yml: missing default rank");
        ranks = Map.copyOf(result);
        for (String id : ranks.keySet()) collect(id, new HashSet<>(), new LinkedHashMap<>());
    }
    public Rank get(String id) {
        var rank = ranks.get(id);
        if (rank == null) throw new IllegalArgumentException("Unknown rank: " + id);
        return rank;
    }
    public List<String> ids() { return ranks.values().stream().sorted(Comparator.comparingInt(Rank::order)).map(Rank::id).toList(); }
    public Rank primary(Collection<String> memberships) {
        return memberships.stream().map(this::get).min(Comparator.comparingInt(Rank::order)).orElse(get("default"));
    }
    public Map<String, Boolean> permissions(Collection<String> memberships) {
        var result = new LinkedHashMap<String, Boolean>();
        collect("default", new HashSet<>(), result);
        memberships.stream().map(this::get).sorted(Comparator.comparingInt(Rank::order).reversed())
                .forEach(rank -> collect(rank.id(), new HashSet<>(), result));
        return result;
    }
    private void collect(String id, Set<String> path, Map<String, Boolean> result) {
        if (!path.add(id)) throw new IllegalArgumentException("ranks.yml: inheritance cycle at " + id);
        var rank = get(id);
        for (String parent : rank.parents()) collect(parent, path, result);
        result.putAll(rank.permissions());
        path.remove(id);
    }
    /** Exact nodes beat progressively broader wildcard grants/denials. */
    public static Boolean resolve(Map<String, Boolean> nodes, String permission) {
        if (nodes.containsKey(permission)) return nodes.get(permission);
        int end = permission.length();
        while ((end = permission.lastIndexOf('.', end - 1)) >= 0) {
            String wildcard = permission.substring(0, end) + ".*";
            if (nodes.containsKey(wildcard)) return nodes.get(wildcard);
        }
        return nodes.get("*");
    }
}
