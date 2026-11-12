package net.mandomc.gameplay.hud;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;

public record HudSettings(boolean enabled, DateTimeFormatter date, String header, String footer,
                          List<String> lines, String combat, String kills, Map<String, Animation> animations) {
    public record Animation(long interval, List<Component> frames) {
        public Component at(long millis) { return frames.get((int) (Math.max(0, millis) / interval % frames.size())); }
    }
    public static HudSettings read(ConfigurationSection config) {
        if (config.getInt("schema-version", -1) != 1) throw new IllegalArgumentException("hud.yml: expected schema-version 1");
        var date = DateTimeFormatter.ofPattern(required(config, "date-format"), Locale.US)
                .withZone(ZoneId.of(required(config, "time-zone")));
        var lines = config.getStringList("lines");
        if (lines.isEmpty() || lines.size() > 15) throw new IllegalArgumentException("hud.yml: lines must contain 1..15 entries");
        if (lines.stream().filter(line -> line.contains("<combat-or-kills>")).count() != 1)
            throw new IllegalArgumentException("hud.yml: exactly one combat-or-kills row required");
        var animations = new HashMap<String, Animation>();
        for (String id : List.of("logo", "bar", "serverinfo")) {
            long interval = config.getLong("animations." + id + ".interval-ms", 0);
            var frames = config.getStringList("animations." + id + ".frames");
            if (interval < 50 || frames.isEmpty() || frames.size() > 1000) throw new IllegalArgumentException("hud.yml: invalid animation " + id);
            animations.put(id, new Animation(interval, frames.stream().map(MiniMessage.miniMessage()::deserialize).toList()));
        }
        return new HudSettings(config.getBoolean("enabled", true), date, required(config, "header"), required(config, "footer"),
                List.copyOf(lines), required(config, "combat"), required(config, "kills"), Map.copyOf(animations));
    }
    private static String required(ConfigurationSection config, String key) {
        if (!config.isString(key)) throw new IllegalArgumentException("hud.yml: missing " + key);
        return config.getString(key);
    }
    public static String seconds(int tenths) { return Math.max(0, tenths) / 10 + "." + Math.max(0, tenths) % 10; }
}
