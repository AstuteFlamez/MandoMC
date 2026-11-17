package net.mandomc.gameplay.guide;

import java.util.*;
import java.util.stream.Collectors;
import net.mandomc.gameplay.items.RecipeDefinition;
import net.mandomc.gameplay.items.RecipeSearch;
import org.bukkit.configuration.ConfigurationSection;

/** Immutable public gameplay notes, including the same acquisition data shown by /recipes. */
public final class GuideKnowledge {
    public record Note(String id, String tags, String facts, String fallback) { }

    private static final Set<String> STOP_WORDS = Set.of(
            "how", "what", "where", "when", "doe", "can", "the", "and", "for", "with", "that", "this",
            "have", "are", "get", "please", "tell", "about", "your");
    private static final Set<String> UPGRADE_WORDS = Set.of(
            "upgrade", "stat", "damage", "speed", "defense", "throw", "guard");
    private final List<Note> notes;
    private final List<Note> items;

    public GuideKnowledge(ConfigurationSection config) {
        this(config, null);
    }

    public GuideKnowledge(ConfigurationSection config, ConfigurationSection recipes) {
        var rows = new ArrayList<Note>();
        for (String id : config.getKeys(false)) {
            var row = Objects.requireNonNull(config.getConfigurationSection(id));
            var note = new Note(id, row.getString("tags", ""), row.getString("facts", ""), row.getString("answer", ""));
            if (note.tags().isBlank() || note.facts().isBlank() || note.fallback().isBlank())
                throw new IllegalArgumentException("Empty droid knowledge " + id);
            rows.add(note);
        }
        notes = List.copyOf(rows);
        var publicItems = new LinkedHashMap<String, String>();
        if (recipes != null) {
            var sources = recipes.getConfigurationSection("sources");
            if (sources != null)
                for (String id : sources.getKeys(false)) publicItems.put(id, clean(sources.getString(id, "")));
            var hidden = new HashSet<>(recipes.getStringList("guide.hidden-results"));
            var hiddenRecipes = new HashSet<>(recipes.getStringList("guide.hidden-recipes"));
            for (String kind : List.of("recipes", "smelting", "stonecutting")) {
                var section = recipes.getConfigurationSection(kind);
                if (section == null) continue;
                for (String id : section.getKeys(false)) {
                    if (hiddenRecipes.contains(id)) continue;
                    var row = section.getConfigurationSection(id);
                    var recipe = switch (kind) {
                        case "smelting" -> RecipeDefinition.parseSmelting(id, row);
                        case "stonecutting" -> RecipeDefinition.parseStonecutting(id, row);
                        default -> RecipeDefinition.parse(id, row);
                    };
                    if (hidden.contains(recipe.result())) continue;
                    publicItems.merge(recipe.result(), instructions(recipe, sources), (source, steps) -> source + " " + steps);
                }
            }
            hidden.forEach(publicItems::remove);
        }
        items = publicItems.entrySet().stream().map(entry -> {
            String answer = label(entry.getKey()) + ": " + entry.getValue();
            return new Note("item:" + entry.getKey(), label(entry.getKey()), answer, answer);
        }).toList();
    }

    private static String instructions(RecipeDefinition recipe, ConfigurationSection sources) {
        var counts = new LinkedHashMap<String, Integer>();
        for (String line : recipe.shape())
            for (char symbol : line.toCharArray())
                if (symbol != ' ') counts.merge(recipe.ingredients().get(symbol), 1, Integer::sum);
        String method = recipe.stonecutting() ? "a stonecutter (choose the style before upgrading the saber)"
                : recipe.smelting() ? (recipe.blasting() ? "a blast furnace" : "a furnace") : "a crafting table";
        String ingredients = counts.entrySet().stream()
                .map(entry -> entry.getValue() + " " + label(entry.getKey())).collect(Collectors.joining(", "));
        String acquisition = sources == null ? "" : counts.keySet().stream().limit(5)
                .filter(sources::isString).map(id -> label(id) + ": " + clean(sources.getString(id)))
                .filter(s -> s.length() <= 300).collect(Collectors.joining(" "));
        return "Make " + recipe.amount() + " " + label(recipe.result()) + " at " + method + " using " + ingredients
                + ". " + acquisition + " Use /recipes for the layout and ingredient sources.";
    }

    private static String clean(String text) {
        return text.replace("\\n", " ").replaceAll("<[^>]+>", "").replaceAll("\\s+", " ").strip();
    }

    private static String label(String id) {
        return id.replace("minecraft:", "").replace('_', ' ');
    }

    private static String term(String value) {
        return value.length() > 3 && value.endsWith("s") ? value.substring(0, value.length() - 1) : value;
    }

    private static boolean acquisition(String normalized) {
        return normalized.matches(".*\\b(?:where|obtain|get|find|mine|craft|make|buy)\\b.*");
    }

    public List<Note> find(String question) {
        String normalized = RecipeSearch.normalize(question);
        var terms = GuideTerms.tokens(question).stream().map(GuideKnowledge::term)
                .filter(t -> t.length() > 2 && !STOP_WORDS.contains(t)).distinct().toList();
        boolean changing = terms.stream().anyMatch(UPGRADE_WORDS::contains);
        boolean upgrade = changing || terms.contains("forge");
        var exact = items.stream().filter(note -> {
            var words = Arrays.stream(RecipeSearch.normalize(note.tags()).split(" +"))
                    .map(GuideKnowledge::term).toList();
            return !words.isEmpty() && words.stream().allMatch(word -> GuideTerms.matches(word, Set.copyOf(terms)));
        }).sorted(Comparator.comparingInt((Note note) -> note.tags().length()).reversed()).limit(3).toList();
        if (!exact.isEmpty() && (!upgrade || acquisition(normalized) && !changing)) return exact;
        if (!upgrade && (terms.contains("saber") || terms.contains("lightsaber"))
                && Collections.disjoint(terms, List.of("dark", "darksaber", "black", "koth")))
            return topic("sabers");
        boolean advice = normalized.matches("(?:what (?:should|can|do) i do|what to do|what now|what next)"
                + "(?: next| now| here| first| on mandomc| in mandomc| as a beginner)*")
                || normalized.matches(".*(?:beginner advice|beginner tips|just joined|new to (?:the server|mandomc)"
                + "|what do you recommend).*")
                || normalized.matches(".*\\bwhere (?:should|can|do) i (?:build|make|start|put) "
                        + "(?:my |a )?(?:first|starter|new) (?:mandomc )?base\\b.*");
        if (advice) return topic("beginner");
        return notes.stream()
                .filter(note -> !(note.id().equals("forge") && !upgrade) && !(note.id().equals("sabers") && upgrade))
                .filter(note -> score(note, terms) > 0)
                .sorted(Comparator.comparingInt((Note note) -> score(note, terms)).reversed()).limit(3).toList();
    }

    private List<Note> topic(String id) {
        return notes.stream().filter(note -> note.id().equals(id)).toList();
    }

    /** Keep first steps and acquisition instructions exact rather than generating unsupported gameplay steps. */
    public Optional<String> verifiedAnswer(String question, List<Note> selected) {
        if (selected.isEmpty()) return Optional.empty();
        var first = selected.getFirst();
        if (first.id().equals("beginner")) return Optional.of(first.fallback());
        String normalized = RecipeSearch.normalize(question);
        if (first.id().equals("mining") && normalized.contains("fortune")) return Optional.of(first.fallback());
        if (first.id().equals("death") && normalized.matches(".*\\b(?:die|death|dying|killed)\\b.*"))
            return Optional.of(first.fallback());
        if (!acquisition(RecipeSearch.normalize(question))) return Optional.empty();
        return first.id().startsWith("item:") || first.id().equals("sabers")
                ? Optional.of(first.fallback()) : Optional.empty();
    }

    private int score(Note note, List<String> terms) {
        var tags = Arrays.stream(RecipeSearch.normalize(note.tags()).split(" +"))
                .map(GuideKnowledge::term).collect(Collectors.toSet());
        return (int) terms.stream().filter(word -> GuideTerms.matches(word, tags)).count();
    }
}
