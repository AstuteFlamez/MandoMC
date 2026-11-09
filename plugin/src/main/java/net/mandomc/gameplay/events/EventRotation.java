package net.mandomc.gameplay.events;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Legacy weighted rotation and cooldown fallback, using only currently registered events. */
public final class EventRotation {
    public record Entry(String id, int weight, int cooldownCycles, boolean enabled) {
        public Entry {
            if (id == null || id.isBlank() || weight < 0 || weight > 10000 || cooldownCycles < 0 || cooldownCycles > 100)
                throw new IllegalArgumentException("events: invalid id, weight or cooldown-cycles");
        }
    }
    private final Map<String, Integer> cooldowns = new HashMap<>();
    private String previous;
    public String choose(List<Entry> entries, RandomGenerator random) {
        var enabled = entries.stream().filter(e -> e.enabled() && e.weight() > 0).toList();
        var eligible = enabled.stream().filter(e -> !e.id().equals(previous)
                && cooldowns.getOrDefault(e.id(), 0) == 0).toList();
        if (eligible.isEmpty()) eligible = enabled;
        int total = eligible.stream().mapToInt(Entry::weight).sum();
        if (total == 0) return null;
        int roll = random.nextInt(total);
        for (var entry : eligible) {roll -= entry.weight();if (roll < 0) return entry.id();}
        throw new IllegalStateException("event rotation failed");
    }
    public void started(Entry entry) {
        cooldowns.replaceAll((id, value) -> Math.max(0, value - 1));
        cooldowns.put(entry.id(), entry.cooldownCycles());previous = entry.id();
    }
}
