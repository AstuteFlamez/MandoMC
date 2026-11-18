package net.mandomc.integrations.discord;

import java.net.URI;
import java.util.*;
import net.mandomc.gameplay.ranks.RankCatalog;
import org.bukkit.configuration.ConfigurationSection;

/** An operator must explicitly approve the same stable rank/role IDs at both ends. */
public record DiscordSettings(URI endpoint, Map<String, String> mapping, int intervalSeconds) {
    public static DiscordSettings read(ConfigurationSection config, RankCatalog catalog) {
        if (config.getInt("schema-version", -1) != 1) throw new IllegalArgumentException("Discord config schema must be 1");
        URI endpoint = URI.create(config.getString("endpoint", ""));
        if (!"https".equals(endpoint.getScheme()) || endpoint.getHost() == null || endpoint.getUserInfo() != null
                || endpoint.getQuery() != null || endpoint.getFragment() != null
                || !(endpoint.getPath().isEmpty() || endpoint.getPath().equals("/")))
            throw new IllegalArgumentException("Discord endpoint must be an HTTPS origin without a path");
        var mapping = new TreeMap<String, String>();
        var section = config.getConfigurationSection("rank-roles");
        if (section != null) for (String rank : section.getKeys(false)) {
            catalog.get(rank);
            String role = section.getString(rank, "");
            if (rank.equals("default") || !role.matches("[0-9]{17,20}") || mapping.containsValue(role))
                throw new IllegalArgumentException("Discord rank roles must be unique IDs for non-default ranks");
            mapping.put(rank, role);
        }
        if (mapping.size() > 8) throw new IllegalArgumentException("At most eight Discord rank mappings");
        int seconds = config.getInt("interval-seconds", 30);
        if (seconds < 30 || seconds > 3600) throw new IllegalArgumentException("Discord interval must be 30..3600 seconds");
        return new DiscordSettings(endpoint, Map.copyOf(mapping), seconds);
    }

    /** Compare against the request snapshot before applying. Preserve every unmapped membership. */
    public static Set<String> merge(Set<String> current, Set<String> expected, Set<String> target, Set<String> managed) {
        if (!current.equals(expected)) return null;
        if (!managed.containsAll(target)) throw new IllegalArgumentException("Unmapped Discord rank rejected");
        var result = new HashSet<>(current);
        result.removeAll(managed); result.addAll(target);
        if (result.isEmpty()) result.add("default");
        return Set.copyOf(result);
    }
}
