package net.mandomc.gameplay.messaging;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Immutable startup snapshot of explicitly owned message templates, including current overrides. */
public final class MessageCatalog {
    public record Match(ChatCategory category, boolean ammo, String source) {}
    private record Rule(Pattern pattern, Match match) {}
    private static final Pattern TOKEN = Pattern.compile("\\{[a-zA-Z_][a-zA-Z0-9_-]*}|<([a-zA-Z][a-zA-Z0-9_-]*)>|%[a-zA-Z0-9_-]+%");
    private static final Pattern DENIAL = Pattern.compile("(?:^|[.-])(?:denied|invalid|unknown|unavailable|missing|offline|failed|failure|full|empty|insufficient|limit|unsafe|blocked|forbidden|protected|saving|busy|no|not)(?:[.-]|$)");
    private static final Set<String> SUCCESS = Set.of("success", "arrived", "claimed", "key-earned", "finished", "won", "saved", "created", "completed", "deployed", "packed", "home-set");
    private final List<Rule> rules;
    private final Map<String,ChatCategory> categories=new java.util.HashMap<>();

    public MessageCatalog(JavaPlugin plugin, YamlConfiguration settings) throws Exception {
        var result = new LinkedHashMap<String, Rule>();
        var sources = settings.getMapList("sources");
        if (sources.isEmpty()) throw new IllegalArgumentException("chat-style.yml: sources required");
        for (var sourceEntry : sources) {
            String source=java.util.Objects.requireNonNull(sourceEntry.get("file")).toString();
            var category = ChatCategory.valueOf(java.util.Objects.requireNonNull(sourceEntry.get("category")).toString());
            var config = load(plugin, source);
            List<String> admin = sourceEntry.get("admin-paths") instanceof List<?> list
                    ? list.stream().map(Object::toString).toList() : List.of();
            for (var entry : config.getValues(true).entrySet()) {
                String path = entry.getKey();
                if(source.equals("language/territory.yml")&&Set.of("title","legend","orientation","position","safezone","warzone","wilderness","claims-legend").contains(path))continue;
                if (!source.equals("language/messages.yml") && !source.startsWith("language/") && !path.startsWith("messages.") && !path.startsWith("text.")) continue;
                Object value = entry.getValue();
                if (value instanceof String text) {
                    categories.put(source+":"+path,choose(source,path,text,category,admin));
                    if(source.equals("crates/crates.yml")&&path.equals("messages.won"))text += "<item>";
                    add(result, text, choose(source, path, text, category, admin), false, source + ":" + path);
                }
                else if (value instanceof List<?> list) for (Object line : list)
                    if (line instanceof String text) add(result, text, choose(source, path, text, category, admin), false, source + ":" + path);
            }
        }
        File weapons = new File(plugin.getDataFolder().getParentFile(), "WeaponMechanics/weapons");
        if (weapons.isDirectory()) try (var paths = Files.walk(weapons.toPath())) {
            for (var path : paths.filter(p -> p.toString().endsWith(".yml")).toList()) {
                var config = YamlConfiguration.loadConfiguration(path.toFile());
                for (var entry : config.getValues(true).entrySet()) {
                    if (!entry.getKey().endsWith("Out_Of_Ammo_Mechanics") || !(entry.getValue() instanceof List<?> list)) continue;
                    for (Object mechanic : list) {
                        String text = mechanic.toString();
                        if (text.startsWith("Message{message=") && text.endsWith("}"))
                            add(result, text.substring(16, text.length()-1), ChatCategory.ERROR, true, "WeaponMechanics:" + entry.getKey());
                    }
                }
            }
        }
        rules = List.copyOf(result.values());
    }

    private static YamlConfiguration load(JavaPlugin plugin, String path) throws Exception {
        var config = new YamlConfiguration();
        File file = new File(plugin.getDataFolder(), path);
        if (file.isFile()) config.load(file);
        var resource = plugin.getResource(path);
        if (resource != null) try (var reader = new InputStreamReader(resource, StandardCharsets.UTF_8)) {
            config.setDefaults(YamlConfiguration.loadConfiguration(reader)); config.options().copyDefaults(true);
        }
        return config;
    }

    private static ChatCategory choose(String source, String path, String text, ChatCategory fallback, List<String> admin) {
        if (admin.contains(path)) return ChatCategory.ADMIN;
        if (path.startsWith("combat.")) return ChatCategory.COMBAT;
        if (DENIAL.matcher(path).find() || text.startsWith("<red>") && !path.endsWith("raid-started")) return ChatCategory.ERROR;
        String leaf = path.substring(path.lastIndexOf('.')+1);
        if (SUCCESS.contains(leaf) && fallback != ChatCategory.SOCIAL && fallback != ChatCategory.ECONOMY && fallback != ChatCategory.EVENT) return ChatCategory.SUCCESS;
        if (path.contains("usage") || path.contains("help")) return ChatCategory.INFO;
        return fallback;
    }

    private static void add(Map<String, Rule> result, String template, ChatCategory category, boolean ammo, String source) {
        Pattern pattern = templatePattern(template);
        if (pattern == null) return;
        var rule = new Rule(pattern, new Match(category, ammo, source));
        var old = result.get(pattern.pattern());
        // Never label a shared public template as staff-only merely because one admin path uses it.
        if (old == null || old.match.category() == ChatCategory.ADMIN && category != ChatCategory.ADMIN) result.put(pattern.pattern(), rule);
    }

    public static Pattern templatePattern(String template) {
        var matcher = TOKEN.matcher(template); var replaced = new StringBuffer(); int number = 0;
        while (matcher.find()) {
            if (matcher.group(1) != null && TagResolver.standard().has(matcher.group(1))) continue;
            matcher.appendReplacement(replaced, "MMCTOKEN" + number++ + "END");
        }
        matcher.appendTail(replaced);
        String plain = ChatStyle.withoutPrefix(ChatStyle.plain(MiniMessage.miniMessage().deserialize(replaced.toString())))
                .replaceAll("[&§][0-9a-fk-orA-FK-OR]", "");
        if (plain.replaceAll("MMCTOKEN\\d+END", "").replaceAll("[^\\p{L}]", "").length() < 8) return null;
        var token = Pattern.compile("MMCTOKEN\\d+END").matcher(plain); var regex = new StringBuilder("\\A"); int last = 0;
        while (token.find()) { regex.append(Pattern.quote(plain.substring(last,token.start()))).append(".{0,2048}?"); last = token.end(); }
        regex.append(Pattern.quote(plain.substring(last))).append("\\z");
        return Pattern.compile(regex.toString(), Pattern.DOTALL);
    }

    public Match match(Component input) {
        String plain = ChatStyle.withoutPrefix(ChatStyle.plain(input));
        if (plain.length() > 8192) return null;
        for (var rule : rules) if (rule.pattern.matcher(plain).matches()) return rule.match;
        return null;
    }
    public int size() { return rules.size(); }
    public ChatCategory category(String source,String path){return categories.getOrDefault(source+":"+path,ChatCategory.INFO);}
}
