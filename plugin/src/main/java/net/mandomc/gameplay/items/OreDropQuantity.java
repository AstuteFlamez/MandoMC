package net.mandomc.gameplay.items;

import java.util.random.RandomGenerator;

/** One final quantity rule for ordinary themed ore and event-owned ore nodes. */
public final class OreDropQuantity {
    private OreDropQuantity() { }

    public static int roll(RandomGenerator random) { return random.nextInt(1, 6); }
}
