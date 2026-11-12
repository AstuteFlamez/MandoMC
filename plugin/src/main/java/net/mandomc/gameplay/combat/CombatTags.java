package net.mandomc.gameplay.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Main-thread deadlines, independent of tick rate. The clock supplies monotonic milliseconds. */
public final class CombatTags {
    private final Map<UUID, Long> deadlines = new HashMap<>();
    private final LongSupplier clock;
    private final long duration;

    public CombatTags(int seconds, LongSupplier clock) {
        if (seconds < 1 || seconds > 300) throw new IllegalArgumentException("combat/combat.yml: tag-seconds must be 1..300");
        this.duration = seconds * 1000L;
        this.clock = clock;
    }

    public void tag(UUID player) { deadlines.put(player, clock.getAsLong() + duration); }

    public int tenths(UUID player) {
        Long deadline = deadlines.get(player);
        if (deadline == null) return 0;
        long remaining = deadline - clock.getAsLong();
        if (remaining <= 0) {
            deadlines.remove(player);
            return 0;
        }
        return (int) ((remaining + 99) / 100);
    }

    public boolean active(UUID player) { return tenths(player) > 0; }
    public void clear(UUID player) { deadlines.remove(player); }
    public void clear() { deadlines.clear(); }
}
