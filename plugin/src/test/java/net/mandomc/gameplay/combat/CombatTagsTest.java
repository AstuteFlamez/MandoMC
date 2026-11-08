package net.mandomc.gameplay.combat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import static org.junit.Assert.*;

public class CombatTagsTest {
    @Test public void tenthsRoundUpAndExpireAtExactlyFifteenSeconds() {
        var now = new AtomicLong(100);
        var tags = new CombatTags(15, now::get);
        var id = UUID.randomUUID();
        assertFalse(tags.active(id));
        tags.tag(id);
        assertEquals(150, tags.tenths(id));
        now.addAndGet(100);
        assertEquals(149, tags.tenths(id));
        now.set(15099);
        assertEquals(1, tags.tenths(id));
        now.incrementAndGet();
        assertFalse(tags.active(id));
    }
    @Test public void hitsRefreshRatherThanStackAndPlayersRemainIndependent() {
        var now = new AtomicLong();
        var tags = new CombatTags(15, now::get);
        var a = UUID.randomUUID();
        var b = UUID.randomUUID();
        tags.tag(a);
        tags.tag(b);
        now.set(14000);
        tags.tag(a);
        assertEquals(150, tags.tenths(a));
        assertEquals(10, tags.tenths(b));
        now.set(15000);
        assertEquals(140, tags.tenths(a));
        assertFalse(tags.active(b));
    }
    @Test public void clearAndCloseRemoveTagsEvenWithNegativeClockOrigin() {
        var now = new AtomicLong(-100000);
        var tags = new CombatTags(15, now::get);
        var id = UUID.randomUUID();
        tags.tag(id);
        assertTrue(tags.active(id));
        tags.clear(id);
        assertFalse(tags.active(id));
        tags.tag(id);
        tags.clear();
        assertFalse(tags.active(id));
    }
    @Test(expected = IllegalArgumentException.class) public void rejectZeroDuration() { new CombatTags(0, () -> 0); }
    @Test(expected = IllegalArgumentException.class) public void rejectExcessiveDuration() { new CombatTags(301, () -> 0); }
}
