package net.mandomc.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Moves existing settings before defaults are installed; conflicting files are never overwritten.
 */
public final class ConfigFiles {
    private ConfigFiles() {}

    public static void migrate(JavaPlugin plugin) throws IOException {
        var paths = new Properties();
        try (var input = plugin.getResource("config-layout.properties")) {
            if (input == null) throw new IOException("Missing config-layout.properties");
            paths.load(input);
        }
        Path root = plugin.getDataFolder().toPath();
        Path oldItems = root.resolve("items");
        if (Files.isDirectory(oldItems)) {
            try (var files = Files.list(oldItems)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String name = file.getFileName().toString();
                    if (name.endsWith(".yml")
                            && !name.equals("catalog.yml")
                            && !name.equals("recipes.yml")) {
                        paths.setProperty("items/" + name, "items/definitions/" + name);
                    }
                }
            }
        }
        migrate(root, paths);
    }

    public static void migrate(Path root, Properties paths) throws IOException {
        // Validate every conflict before moving anything, so a repair is easy to review.
        for (String old : paths.stringPropertyNames()) {
            Path source = root.resolve(old), target = root.resolve(paths.getProperty(old));
            if (Files.exists(source)
                    && Files.exists(target)
                    && Files.mismatch(source, target) != -1) {
                throw new IOException(
                        "Conflicting configs: "
                                + old
                                + " and "
                                + paths.getProperty(old)
                                + "; reconcile them and remove the old file before restarting");
            }
        }
        for (String old : paths.stringPropertyNames()) {
            Path source = root.resolve(old), target = root.resolve(paths.getProperty(old));
            if (!Files.exists(source)) continue;
            Files.createDirectories(target.getParent());
            if (Files.exists(target)) Files.delete(source); // Identical duplicate, validated above.
            else Files.move(source, target);
        }
    }
}
