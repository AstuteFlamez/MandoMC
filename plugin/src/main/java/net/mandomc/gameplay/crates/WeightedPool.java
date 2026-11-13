package net.mandomc.gameplay.crates;

import java.util.List;

/** Exact integer weights: preview percentages and server selection share the same pool. */
public final class WeightedPool<T> {
    public record Entry<T>(T value, int weight) { }
    private final List<Entry<T>> entries;
    private final int total;

    public WeightedPool(List<Entry<T>> entries) {
        if (entries.isEmpty()) throw new IllegalArgumentException("Crate needs rewards");
        this.entries = List.copyOf(entries);
        int sum = 0;
        for (var entry : entries) {
            if (entry.value() == null || entry.weight() < 1 || entry.weight() > 1_000_000) {
                throw new IllegalArgumentException("Crate reward/weight is invalid");
            }
            sum = Math.addExact(sum, entry.weight());
        }
        total = sum;
    }

    public T choose(int ticket) {
        if (ticket < 0 || ticket >= total) throw new IllegalArgumentException("Invalid reward ticket");
        for (var entry : entries) {
            if (ticket < entry.weight()) return entry.value();
            ticket -= entry.weight();
        }
        throw new AssertionError("Unreachable reward ticket");
    }

    public int total() { return total; }
    public List<Entry<T>> entries() { return entries; }
}
