package net.mandomc.gameplay.items;

import java.util.Random;
import java.util.random.RandomGenerator;
import org.junit.Test;
import static org.junit.Assert.*;

public class OreDropQuantityTest {
    @Test public void sharedRuleUsesExactlyOneInclusiveOneToFiveRoll() {
        for (int result = 1; result <= 5; result++) {
            final int value = result;
            var random = new RandomGenerator() {
                int calls;
                @Override public long nextLong() { throw new AssertionError("Wrong random method"); }
                @Override public int nextInt(int origin, int bound) {
                    assertEquals(1, origin); assertEquals(6, bound); assertEquals(0, calls++); return value;
                }
            };
            assertEquals(result, OreDropQuantity.roll(random));
        }
    }
    @Test public void repeatableStreamCoversAllFiveOutcomesWithinTheFinalBounds() {
        var counts = new int[6]; var random = new Random(84105);
        for (int i = 0; i < 10000; i++) {
            int amount = OreDropQuantity.roll(random);
            assertTrue(amount >= 1 && amount <= 5); counts[amount]++;
        }
        for (int amount = 1; amount <= 5; amount++) assertTrue(counts[amount] >= 1500 && counts[amount] <= 2500);
    }
}
