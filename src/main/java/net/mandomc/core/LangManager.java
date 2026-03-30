package net.mandomc.core;

import net.mandomc.MandoMC;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Manages player-facing messages loaded from lang.yml.
 *
 * Messages are keyed with dot-separated paths matching the YAML structure,
 * e.g. {@code "bounties.refunded"} or {@code "parkour.finish.time"}.
 *
 * Use {@link #get(String)} for plain messages and
 * {@link #get(String, String...)} for messages with inline placeholder
 * replacement (pairs of placeholder → value arguments).
 */
public final class LangManager {

    private static FileConfiguration lang;

    private LangManager() {}

    /**
     * Loads (or reloads) lang.yml from the plugin data folder.
     * If the file does not exist it is copied from the bundled resource.
     * Defaults from the bundled resource are merged so that new keys added
     * in later versions are always available.
     */
    public static void load() {
        MandoMC plugin = MandoMC.getInstance();
        File file = new File(plugin.getDataFolder(), "lang.yml");

        if (!file.exists()) {
            plugin.saveResource("lang.yml", false);
        }

        lang = YamlConfiguration.loadConfiguration(file);

        // Merge bundled defaults so newly added keys always fall back gracefully.
        InputStream resource = plugin.getResource("lang.yml");
        if (resource != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8));
            lang.setDefaults(defaults);
        }
    }

    /**
     * Returns the translated message for the given dot-separated key.
     * Colour codes ({@code &} prefix) are resolved automatically.
     * If the key is missing a descriptive fallback is returned so nothing
     * silently disappears in-game.
     *
     * @param key dot-separated path matching lang.yml structure
     * @return coloured message string
     */
    public static String get(String key) {
        String raw = lang.getString(key);
        if (raw == null) {
            MandoMC.getInstance().getLogger().warning("[LangManager] Missing lang key: " + key);
            raw = "&cMissing lang key: " + key;
        }
        return color(raw);
    }

    /**
     * Returns the translated message with placeholder substitution.
     *
     * Placeholders and their replacements are supplied as consecutive pairs:
     * <pre>
     *     LangManager.get("bounties.last-location",
     *         "%x%", "100",
     *         "%y%", "64",
     *         "%z%", "-200");
     * </pre>
     *
     * @param key          dot-separated lang key
     * @param replacements alternating placeholder / value pairs
     * @return coloured message string with placeholders substituted
     */
    public static String get(String key, String... replacements) {
        String value = get(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            value = value.replace(replacements[i], replacements[i + 1]);
        }
        return value;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
