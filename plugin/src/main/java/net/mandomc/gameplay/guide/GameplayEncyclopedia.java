package net.mandomc.gameplay.guide;

import java.util.*;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Focused records retain each entity's name beside its statistics, drops and nested configuration. */
final class GameplayEncyclopedia {
    private record Entry(Set<String> names, Set<String> words, GuideKnowledge.Note note) { }
    private final List<Entry> entries;
    private static final Set<String> STOP = Set.of("a", "an", "is", "of", "the", "and", "or", "to", "in", "on",
            "at", "for", "from", "with", "this", "that", "they", "them", "their", "it", "its", "what", "where",
            "when", "who", "how", "why", "do", "does", "can", "could", "should", "would", "i", "me", "my",
            "you", "your", "we", "our", "are", "have", "has", "much", "many", "tell", "about", "please",
            "get", "find", "mandomc", "server");
    private static final Set<String> PRESENTATION = Set.of("schema-version", "custom-model-data", "item-model",
            "material", "messages", "gui", "sound", "sounds", "skin", "blocks", "skills", "aigoalselectors",
            "aitargetselectors", "model", "options", "modules", "faction", "type");

    GameplayEncyclopedia(RepositorySnapshot snapshot, String fallback) {
        var effective = new TreeMap<String, String>();
        snapshot.documents().forEach((path, body) -> {
            if (path.startsWith("plugin/src/main/resources/") && path.endsWith(".yml"))
                effective.put(path.substring("plugin/src/main/resources/".length()), body);
        });
        snapshot.documents().forEach((path, body) -> {
            if (path.startsWith("server/config/common/mandomc/") && path.endsWith(".yml"))
                effective.put(path.substring("server/config/common/mandomc/".length()), body);
            else if (path.startsWith("server/config/common/mythicmobs/")
                    || path.startsWith("server/config/common/weaponmechanics/"))
                effective.put(path.substring("server/config/common/".length()), body);
        });
        var rows = new ArrayList<Entry>();
        effective.forEach((path, body) -> {
            var yaml = new YamlConfiguration();
            try { yaml.loadFromString(body); }
            catch (org.bukkit.configuration.InvalidConfigurationException invalid) {
                throw new IllegalArgumentException("Invalid approved gameplay YAML: " + path, invalid);
            }
            if (path.contains("items/definitions/")) records(rows, path, yaml.getConfigurationSection("items"), fallback);
            else if (path.equals("crates/crates.yml")) crates(rows, yaml, fallback);
            else if (path.startsWith("mythicmobs/") || path.startsWith("weaponmechanics/"))
                records(rows, path, yaml, fallback);
            else if (path.equals("mobs/spawning.yml")) {
                String creatures = Arrays.stream(net.mandomc.gameplay.mobs.EncounterPlan.Kind.values())
                        .map(kind -> kind == net.mandomc.gameplay.mobs.EncounterPlan.Kind.PATROL
                                ? "Tusken raider patrol" : kind.name()).collect(Collectors.joining(" "));
                add(rows, path, creatures + " spawn locations", "Spawn eligibility rules, not live creature coordinates: "
                        + flatten(yaml, "") + " Safezones forbid creature spawning; sites also pass native safety checks.", fallback);
            }
            else if (!path.equals("items/recipes.yml") && !path.startsWith("language/")) {
                String name = path.replace(".yml", "").replace('/', ' ');
                add(rows, path, name, flatten(yaml, ""), fallback);
            }
        });
        entries = List.copyOf(rows);
    }

    private static void records(List<Entry> rows, String path, ConfigurationSection section, String fallback) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            var value = section.getConfigurationSection(key);
            if (value == null) continue;
            String name = key + " " + value.getString("name", value.getString("Display", ""));
            String facts;
            if (path.startsWith("weaponmechanics/")) {
                name += " " + value.getString("Info.Weapon_Item.Name", "");
                facts = "Weapon statistics: " + select(value, List.of("Damage", "Shoot.Fully_Automatic_Shots_Per_Second",
                        "Shoot.Projectile_Speed", "Reload.Magazine_Size", "Reload.Reload_Duration", "Reload.Ammo.Ammos",
                        "Scope", "Info.Weapon_Item.Lore"));
            } else if (path.startsWith("mythicmobs/")) {
                facts = select(value, List.of("Health", "Damage", "Drops", "Options.MovementSpeed", "Options.FollowRange"));
                var attacks = value.getStringList("Skills").stream()
                        .filter(s -> s.matches(".*\\bdamage\\{.*")).toList();
                if (!attacks.isEmpty()) facts += " Scripted attack mechanics: " + attacks;
                if (value.contains("Health")) facts += " Health is in hit points (2 hit points per heart)."
                        + " Damage here is base melee damage; scripted attacks may have separate damage.";
            } else facts = flatten(value, "");
            add(rows, path + ":" + key, name, facts, fallback);
        }
    }

    private static String select(ConfigurationSection section, List<String> paths) {
        var parts = new ArrayList<String>();
        for (String key : paths) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection nested) parts.add(flatten(nested, key + " "));
            else if (value != null) parts.add(GuideTerms.readable(key.replace('.', ' ')) + ": " + value + ".");
        }
        return String.join(" ", parts);
    }

    private static String flatten(ConfigurationSection section, String prefix) {
        var parts = new ArrayList<String>();
        for (String key : section.getKeys(false)) {
            if (PRESENTATION.contains(key.toLowerCase(Locale.ROOT))) continue;
            Object value = section.get(key);
            String label = GuideTerms.readable(prefix + key);
            if (value instanceof ConfigurationSection nested) parts.add(flatten(nested, label + " "));
            else if (value instanceof List<?> list && list.size() > 40) continue;
            else if (value != null) parts.add(label + ": " + value + ".");
        }
        return String.join(" ", parts);
    }

    private static void crates(List<Entry> rows, YamlConfiguration yaml, String fallback) {
        var crates = yaml.getConfigurationSection("crates");
        if (crates == null) return;
        for (String key : crates.getKeys(false)) {
            var rewards = crates.getMapList(key + ".rewards");
            double total = rewards.stream().mapToDouble(r -> ((Number) r.get("weight")).doubleValue()).sum();
            if (total <= 0) continue;
            var summary = new ArrayList<String>();
            for (var reward : rewards) {
                String item = Objects.toString(reward.get("item"));
                double chance = ((Number) reward.get("weight")).doubleValue() * 100 / total;
                String facts = "The " + key + " crate awards " + reward.get("amount") + " " + item
                        + " with a " + String.format(Locale.ROOT, "%.4f", chance) + "% chance per key."
                        + " Reward weights are relative; this percentage is calculated from all configured weights."
                        + " Use /crates preview " + key + " to inspect rewards.";
                add(rows, "crate:" + key + ":" + item, key + " crate " + item, facts, fallback);
                summary.add(GuideTerms.readable(item) + " " + String.format(Locale.ROOT, "%.4f%%", chance));
            }
            add(rows, "crate:" + key, key + " crate rewards chances", String.join("; ", summary), fallback);
        }
    }

    private static void add(List<Entry> rows, String id, String name, String facts, String fallback) {
        if (facts.isBlank()) return;
        String title = GuideTerms.readable(RepositorySearch.playerText(name));
        String text = RepositorySearch.playerText(title + ": " + facts);
        // Each chunk carries the entity name, even when a long record spans several passages.
        for (int start = 0; start < text.length(); start += 2400) {
            String part = title + ": " + text.substring(start, Math.min(text.length(), start + 2400));
            rows.add(new Entry(content(title), content(part),
                    new GuideKnowledge.Note("encyclopedia:" + id + ":" + start, title, part, fallback)));
        }
    }

    List<GuideKnowledge.Note> find(String question) {
        var query = content(question);
        var ranked = entries.stream().filter(e -> e.names().stream().anyMatch(n -> n.length() > 2 && GuideTerms.matches(n, query)))
                .filter(e -> query.stream().filter(q -> GuideTerms.matches(q, e.words())).count() >= Math.min(2, query.size()))
                .sorted(Comparator.comparingDouble((Entry e) -> score(e, query)).reversed()).toList();
        var used = new HashSet<String>();
        return ranked.stream().filter(e -> used.add(e.note().id().replaceAll(":\\d+$", "")))
                .limit(4).map(Entry::note).toList();
    }

    private static Set<String> content(String text) {
        return GuideTerms.tokens(text).stream().filter(t -> !STOP.contains(t)).collect(Collectors.toSet());
    }

    private static double score(Entry entry, Set<String> query) {
        return entry.names().stream().filter(n -> GuideTerms.matches(n, query)).count() * 8.0
                + query.stream().filter(q -> GuideTerms.matches(q, entry.words())).count()
                + (query.contains("health") && entry.words().contains("health") ? 4 : 0);
    }

    int size() { return entries.size(); }
}
