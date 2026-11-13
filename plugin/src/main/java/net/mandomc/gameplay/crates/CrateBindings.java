package net.mandomc.gameplay.crates;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Server-owned container locations survive source/config deployments. Snapshots are immutable. */
public final class CrateBindings {
    public record Point(String world, int x, int y, int z) {
        public Point {
            if (world == null || !world.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("Invalid crate world key");
        }
        Map<String, Object> map() { return Map.of("world", world, "x", x, "y", y, "z", z); }
    }
    private final Path file;
    private final Map<String, List<Point>> positions;

    private CrateBindings(Path file, Map<String, List<Point>> positions) {
        this.file = file;
        var copy = new LinkedHashMap<String, List<Point>>();
        positions.forEach((id, points) -> copy.put(id, List.copyOf(points)));
        this.positions = Map.copyOf(copy);
    }

    public static CrateBindings load(Path file, ConfigurationSection defaults) throws Exception {
        var data = new LinkedHashMap<String, List<Point>>();
        ConfigurationSection source = defaults;
        if (Files.exists(file)) {
            var yaml = new YamlConfiguration();
            yaml.load(file.toFile());
            if (yaml.getInt("schema-version") != 1 || !yaml.isConfigurationSection("crates"))
                throw new IllegalArgumentException("data/crate-blocks.yml: expected schema 1 and crates");
            source = yaml.getConfigurationSection("crates");
        }
        for (String id : source.getKeys(false)) {
            if (!id.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Invalid crate binding ID");
            var points = new ArrayList<Point>();
            String path = source == defaults ? id + ".blocks" : id;
            for (var entry : source.getMapList(path)) {
                var point = new Point(String.valueOf(entry.get("world")), integer(entry.get("x")),
                        integer(entry.get("y")), integer(entry.get("z")));
                if (!points.contains(point)) points.add(point);
            }
            data.put(id, points);
        }
        return new CrateBindings(file, data);
    }

    private static int integer(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() != number.intValue())
            throw new IllegalArgumentException("Crate binding coordinates must be integers");
        return number.intValue();
    }

    public List<Map<String, Object>> maps(String id) {
        return positions.getOrDefault(id, List.of()).stream().map(Point::map).toList();
    }

    public CrateBindings bind(String id, Point point) {
        if (!id.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Invalid crate ID");
        for (var entry : positions.entrySet())
            if (!entry.getKey().equals(id) && entry.getValue().contains(point))
                throw new IllegalArgumentException("That container is already bound to another crate.");
        var next = new LinkedHashMap<>(positions);
        var points = new ArrayList<>(positions.getOrDefault(id, List.of()));
        if (!points.contains(point)) points.add(point);
        next.put(id, List.copyOf(points));
        return new CrateBindings(file, next);
    }

    /** Call off the tick thread; publication to the live owner follows successful durable save. */
    public void save() throws IOException {
        var yaml = new YamlConfiguration();
        yaml.set("schema-version", 1);
        positions.forEach((id, points) -> yaml.set("crates." + id, points.stream().map(Point::map).toList()));
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "crate-blocks-", ".tmp");
        try {
            try (var channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer bytes = java.nio.charset.StandardCharsets.UTF_8.encode(yaml.saveToString());
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
