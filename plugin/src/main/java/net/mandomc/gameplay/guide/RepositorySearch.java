package net.mandomc.gameplay.guide;

import java.util.*;
import java.util.stream.Collectors;
import net.mandomc.gameplay.items.RecipeSearch;
import net.kyori.adventure.text.minimessage.MiniMessage;

/** Small immutable lexical index: only the best bounded passages, never whole files, become model context. */
public final class RepositorySearch {
    private static final Set<String> STOP = Set.of("how", "what", "where", "when", "why", "who", "can", "could",
            "should", "would", "the", "and", "for", "with", "from", "this", "that", "have", "has", "are", "does",
            "do", "you", "your", "my", "please", "tell", "about", "want", "help", "get", "find", "finding", "obtain",
            "know", "work", "works", "many", "set", "happen", "happens", "affect", "affected", "mandomc", "minecraft", "server", "java", "code");
    private record Passage(String path, String text, Set<String> words, Map<String,Integer> counts,
            Set<String> pathWords, double authority) { }
    private final String commit, fallback;
    private final List<Passage> passages;
    private final Map<String, Integer> frequencies;
    private final double averageLength;

    public RepositorySearch(RepositorySnapshot snapshot, String fallback) {
        this.commit = snapshot.manifest().commit();
        this.fallback = fallback;
        var rows = new ArrayList<Passage>();
        snapshot.documents().forEach((path, text) -> {
            String publicText = playerText(text);
            if (path.endsWith(".md")) publicText = playerSections(publicText);
            String status = publicText.lines().limit(20).filter(l -> l.startsWith("Status:")).findFirst().orElse("");
            var pathWords = tokens(path.replace(".java", "").replace(".yml", "").replace(".md", ""));
            double authority = path.endsWith("language/handbook.yml") ? 2.2 : path.startsWith("server/config/common/") ? 1.5 : path.contains("/resources/") ? 1.3 : path.endsWith(".java") ? 1.1 : 1;
            String heading = "";
            for (String paragraph : publicText.split("\\n\\s*\\n")) {
                if (paragraph.isBlank()) continue;
                if (paragraph.startsWith("#")) heading = paragraph.lines().findFirst().orElse("");
                for (int start = 0; start < paragraph.length(); start += 800) {
                    String body = (status + "\n" + heading + "\n"
                            + paragraph.substring(start, Math.min(paragraph.length(), start + 1000))).strip();
                    var counts = words(body).stream().collect(Collectors.toMap(w -> w, w -> 1, Integer::sum));
                    rows.add(new Passage(path, body, Set.copyOf(counts.keySet()), Map.copyOf(counts), pathWords, authority));
                }
            }
        });
        passages = List.copyOf(rows);
        var counts = new HashMap<String, Integer>();
        for (Passage passage : passages) for (String word : passage.words()) counts.merge(word, 1, Integer::sum);
        frequencies = Map.copyOf(counts);
        averageLength = passages.stream().mapToInt(p -> p.counts().values().stream()
                .mapToInt(Integer::intValue).sum()).average().orElse(1);
    }

    static String playerText(String text) {
        String plain = MiniMessage.miniMessage().stripTags(text.replace("<newline>", "\n")
                .replace("<key:key.sneak>", "Sneak").replace("<key:key.use>", "Use")
                .replace("<key:key.attack>", "Attack"));
        plain = java.util.regex.Pattern.compile("\\bmandomc\\.[a-z0-9_.*-]+")
                .matcher(plain).replaceAll(m -> m.group().substring(8).replace('.', ' ') + " permission");
        return java.util.regex.Pattern.compile("(?<![/\\w])[a-z][a-z0-9]*(?:[-_][a-z0-9]+)+(?!\\w)")
                .matcher(plain).replaceAll(m -> m.group().replace('_', ' ').replace('-', ' '));
    }

    private static String playerSections(String markdown) {
        var result = new StringBuilder();
        int skippedDepth = 0;
        for (String line : markdown.split("\n")) {
            if (line.startsWith("#")) {
                int depth = 0;
                while (depth < line.length() && line.charAt(depth) == '#') depth++;
                if (skippedDepth > 0 && depth <= skippedDepth) skippedDepth = 0;
                String title = line.toLowerCase(Locale.ROOT);
                if (List.of("verification", "rollout", "deployment", "migration", "ownership", "implementation",
                        "historical", "ordered queue", "roadmap", "planned", "future work", "pause note")
                        .stream().anyMatch(title::contains)) skippedDepth = depth;
            }
            if (skippedDepth == 0) result.append(line).append('\n');
        }
        return result.toString();
    }

    static Set<String> tokens(String text) { return new HashSet<>(words(text)); }

    private static List<String> words(String text) {
        return Arrays.stream(RecipeSearch.normalize(text.replaceAll("([a-z])([A-Z])", "$1 $2")).replace("speederbike", "speeder bike").split(" +"))
                .filter(w -> w.length() > 2 && !STOP.contains(w)).map(w -> switch (w) {
                    case "die", "dying", "died" -> "death";
                    case "money" -> "credit";
                    case "ship", "starship", "aircraft" -> "vehicle";
                    default -> w.startsWith("repair") ? "repair" : w.length() > 3 && w.endsWith("s") ? w.substring(0, w.length() - 1) : w;
                }).toList();
    }

    public List<GuideKnowledge.Note> find(String question) {
        var words = new HashSet<>(tokens(question));
        if (words.contains("speeder") || words.contains("xwing") || words.contains("uwing")
                || words.contains("tiefighter")) words.add("vehicle");
        if (words.isEmpty()) return List.of();
        int required = Math.min(2, words.size());
        String focus = List.of("repair", "death", "fortune").stream().filter(words::contains).findFirst().orElse("");
        var usedFiles = new HashSet<String>();
        var usedText = new HashSet<String>();
        return passages.stream().filter(p -> focus.isEmpty() || p.words().contains(focus)).filter(p -> words.stream().filter(p.words()::contains).count() >= required)
                .sorted(Comparator.comparingDouble((Passage p) -> score(p, words)).reversed())
                .filter(p -> usedFiles.add(logicalPath(p.path())) && usedText.add(RecipeSearch.normalize(p.text())))
                .limit(3).map(p -> new GuideKnowledge.Note("repository:" + commit + ":" + p.path(), question,
                        "PUBLIC EVIDENCE @ " + commit + " / " + p.path() + ":\n" + p.text(), fallback)).toList();
    }

    private double score(Passage passage, Set<String> words) {
        double score = 0;
        for (String word : words) if (passage.words().contains(word)) {
            double rarity = Math.log(1 + (double) passages.size() / (1 + frequencies.getOrDefault(word, 0)));
            double tf = passage.counts().getOrDefault(word, 0);
            double length = passage.counts().values().stream().mapToInt(Integer::intValue).sum();
            double frequency = tf * 2.2 / (tf + 1.2 * (.25 + .75 * length / averageLength));
            score += rarity * frequency * (passage.pathWords().contains(word) ? 1.7 : 1);
        }
        return score * passage.authority();
    }
    private static String logicalPath(String path) {
        return path.replace("server/config/common/mandomc/", "plugin/src/main/resources/");
    }
    public int passageCount() { return passages.size(); }
}
