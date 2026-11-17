package net.mandomc.gameplay.guide;

import java.util.*;
import net.mandomc.gameplay.items.RecipeSearch;
import org.bukkit.configuration.ConfigurationSection;

/** Per-player topic context is ephemeral and never shared through the answer cache. */
final class GuideConversation {
    enum Topic { SERVER, LORE, REVIEW }
    record Question(String text, Topic topic) { }
    private record Recent(String question, Topic topic, long time) { }
    private final Map<UUID, Recent> recent = new HashMap<>();
    private final ConfigurationSection config;
    private static final long LIFETIME = 300_000;

    GuideConversation(ConfigurationSection config) { this.config = config; }

    Optional<String> smallTalk(String question) {
        String normalized = RecipeSearch.normalize(question);
        for (String kind : List.of("greeting", "farewell", "thanks")) {
            if (config.getStringList("conversation." + kind + ".phrases").stream()
                    .map(RecipeSearch::normalize).anyMatch(normalized::equals)) {
                var replies = config.getStringList("conversation." + kind + ".replies");
                if (!replies.isEmpty()) return Optional.of(replies.get(java.util.concurrent.ThreadLocalRandom.current()
                        .nextInt(replies.size())));
            }
        }
        return Optional.empty();
    }

    Question resolve(UUID player, String text, boolean serverEvidence, long now) {
        recent.entrySet().removeIf(e -> now - e.getValue().time() >= LIFETIME);
        String q = RecipeSearch.normalize(text);
        var previous = recent.get(player);
        boolean followup = q.matches(".*\\b(?:it|its|they|them|their|him|her|those|that)\\b.*")
                || q.matches("(?:and |also )?(?:where|how much|how many|what about)(?: else)?")
                || q.split(" +").length <= 7 && q.matches("(?:and|also|what about) .+");
        if (followup && previous != null) {
            String context = "Current question: " + text + "\nPrevious topic question: " + previous.question();
            recent.put(player, new Recent(previous.question(), previous.topic(), now));
            return new Question(context, previous.topic());
        }
        boolean server = q.matches(".*\\b(?:mandomc|server|in game|ingame|craft|crafting|recipe|recipes|spawn|spawns"
                + "|drop|drops|loot|mine|mining|farm|farming|damage|health|hp|stats|statistics|balance|credits"
                + "|warp|warps|cooldown|cooldowns|crate|crates|upgrade|upgrades|buy|sell)\\b.*");
        boolean lore = q.matches(".*\\b(?:star wars|starwars|canon|legends|lore|jedi|sith|darth|vader|skywalker"
                + "|obi wan|kenobi|yoda|ahsoka|palpatine|mandalorian|grogu|force|republic|empire|death star)\\b.*");
        Topic topic = server ? Topic.SERVER : lore ? Topic.LORE : serverEvidence ? Topic.SERVER : Topic.REVIEW;
        if (topic == Topic.REVIEW && q.matches(".*\\b(?:material|materials|mob|mobs|planet|planets|saber|sabers"
                + "|lightsaber|lightsabers|kyber|beskar|durasteel|agrinium|electrum|jawa|jawas|bantha|banthas"
                + "|massiff|massiffs|dewback|dewbacks|tatooine|batuu|naboo|hoth|ilum)\\b.*")) topic = Topic.SERVER;
        if (recent.size() >= 256) recent.remove(recent.entrySet().stream()
                .min(Comparator.comparingLong(e -> e.getValue().time())).orElseThrow().getKey());
        recent.put(player, new Recent(text, topic, now));
        return new Question(text, topic);
    }

    void forget(UUID player) { recent.remove(player); }
    void clear() { recent.clear(); }
}
