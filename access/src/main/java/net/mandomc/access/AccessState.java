package net.mandomc.access;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;

/** Immutable snapshots are published only after their durable atomic replacement. */
public record AccessState(boolean enabled, UUID owner, Map<UUID, String> players) {
    public AccessState {
        players = Map.copyOf(players);
        if (!players.containsKey(owner)) throw new IllegalArgumentException("Maintenance owner must remain listed");
        if (players.values().stream().anyMatch(name -> !name.matches("[A-Za-z0-9_]{1,16}")))
            throw new IllegalArgumentException("Invalid player name");
    }

    public boolean allows(UUID player) { return !enabled || players.containsKey(player); }

    public AccessState withEnabled(boolean value) { return new AccessState(value, owner, players); }

    public AccessState add(UUID id, String name) {
        var next = new java.util.HashMap<>(players);
        next.put(id, name);
        return new AccessState(enabled, owner, next);
    }

    public AccessState remove(UUID id) {
        if (owner.equals(id)) throw new IllegalArgumentException("The maintenance owner cannot be removed");
        var next = new java.util.HashMap<>(players);
        next.remove(id);
        return new AccessState(enabled, owner, next);
    }

    public void save(Path path) throws IOException {
        var yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        yaml.set("enabled", enabled);
        yaml.set("owner", owner.toString());
        players.forEach((id, name) -> yaml.set("players." + id, name));
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".maintenance-", ".tmp");
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                var bytes = ByteBuffer.wrap(yaml.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    public static AccessState load(Path path) throws Exception {
        var yaml = new YamlConfiguration();
        yaml.load(path.toFile());
        if (yaml.getInt("schema-version", -1) != 1 || !yaml.isBoolean("enabled"))
            throw new IllegalArgumentException("Invalid maintenance state schema");
        UUID owner = UUID.fromString(yaml.getString("owner", ""));
        var section = yaml.getConfigurationSection("players");
        if (section == null) throw new IllegalArgumentException("Missing maintenance players");
        var players = new java.util.HashMap<UUID, String>();
        for (String key : section.getKeys(false)) players.put(UUID.fromString(key), section.getString(key, ""));
        return new AccessState(yaml.getBoolean("enabled"), owner, players);
    }
}
