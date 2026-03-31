package net.mandomc.gameplay.lottery.storage;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable snapshot of the current lottery state.
 *
 * Holds the prize pot and the per-player ticket counts. Because this is
 * stored as a single entity in {@link LotteryRepository}, it uses the
 * fixed key {@link #CURRENT_ID}.
 */
public final class LotteryState {

    /** The fixed repository key for the single current lottery state. */
    public static final String CURRENT_ID = "current";

    private final String id;
    private final double pot;
    private final Map<UUID, Integer> tickets;

    /**
     * Creates a lottery state snapshot.
     *
     * @param id      the repository key (always {@link #CURRENT_ID})
     * @param pot     the total prize pool
     * @param tickets per-player ticket counts (defensive copy is taken)
     */
    public LotteryState(String id, double pot, Map<UUID, Integer> tickets) {
        this.id = id;
        this.pot = pot;
        this.tickets = Collections.unmodifiableMap(new HashMap<>(tickets));
    }

    /**
     * Returns an empty lottery state (pot = 0, no tickets).
     *
     * @return empty state
     */
    public static LotteryState empty() {
        return new LotteryState(CURRENT_ID, 0.0, Collections.emptyMap());
    }

    public String getId()              { return id; }
    public double getPot()             { return pot; }
    public Map<UUID, Integer> getTickets() { return tickets; }
}
