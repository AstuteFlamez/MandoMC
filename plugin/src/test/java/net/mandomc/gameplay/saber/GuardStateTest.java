package net.mandomc.gameplay.saber;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class GuardStateTest {
    private YamlConfiguration config() {
        return YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
                getClass().getResourceAsStream("/sabers.yml"), java.nio.charset.StandardCharsets.UTF_8));
    }
    private SaberSettings settings() { return SaberSettings.read(config()); }

    @Test public void costTracksDamageAtFiveTimesIncludingFractionalAndLargeHits() {
        var g = new GuardState();var s = settings();
        assertEquals(5, s.damageMultiplier(), 0);
        g.hit(0, s, .5);assertEquals(2.5, g.heat(), 0);
        g.hit(1, s, 6);assertEquals(32.5, g.heat(), 0);
        g.hit(2, s, 3);assertEquals(47.5, g.heat(), 0);
        assertTrue(g.hit(3, s, 1000));assertEquals(100, g.heat(), 0);
    }
    @Test public void nonDamageCannotSpendChargeOrDelayRecovery() {
        var g = new GuardState();var s = settings();g.hit(0, s, 6);
        for (double damage : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertFalse(g.hit(99, s, damage));
        g.cool(100, s);assertEquals(29.5, g.heat(), 0);
    }
    @Test public void exhaustionStaysDisabledUntilItHasChargeAndNeverInstantlyRefills() {
        var g = new GuardState();var s = settings();
        assertTrue(g.hit(4, s, 20));assertEquals(80, g.stunTicks(4));
        for (int tick = 5; tick < 104; tick++) g.cool(tick, s);
        assertTrue(g.disabled(103));assertEquals(100, g.heat(), 0);
        g.cool(104, s);assertFalse(g.disabled(104));assertEquals(99.5, g.heat(), 0);
        assertFalse(g.canThrow(104, s));
    }
    @Test public void stunMinimumStillAppliesWithFasterConfiguredRecovery() {
        var c = config();c.set("guard-recovery-delay-ticks", 1);
        var s = SaberSettings.read(c);var g = new GuardState();g.hit(0, s, 20);
        for (int tick = 1; tick < 80; tick++) g.cool(tick, s);
        assertTrue(g.disabled(79));assertFalse(g.disabled(80));assertEquals(60.5, g.heat(), 0);
    }
    @Test public void fullThrowDrainsToQuarterChargeThenWaitsFiveSecondsAndRepairs() {
        var g = new GuardState();var s = settings();g.thrown(s);
        assertTrue(g.recovering());assertEquals(0, g.heat(), 0);
        for (int tick = 1; tick <= 20; tick++) {
            g.cool(tick, s);assertEquals(tick * 3.75, g.heat(), 1e-9);
        }
        assertEquals(100, s.recoveryDelay());
        for (int tick = 21; tick < 120; tick++) g.cool(tick, s);
        assertEquals(75, g.heat(), 0);assertFalse(g.canThrow(119, s));
        g.cool(120, s);assertEquals(74.5, g.heat(), 0);
        for (int tick = 121; tick <= 218; tick++) g.cool(tick, s);
        assertFalse(g.canThrow(218, s));g.cool(219, s);assertTrue(g.canThrow(219, s));
        for (int tick = 220; tick < 300; tick++) g.cool(tick, s);
        assertEquals(0, g.heat(), 0);assertFalse(g.recovering());
    }
    @Test public void throwRequiresItsEntireCostIncludingPreviouslyReservedDrain() {
        var g = new GuardState();var s = settings();
        assertTrue(g.canThrow(0, s));g.hit(0, s, 5.02);assertFalse(g.canThrow(0, s));
        g.cool(100, s);assertTrue(g.canThrow(100, s));
        g.thrown(s);assertFalse(g.canThrow(100, s));
        var exact = new GuardState();exact.hit(0, s, 5);assertTrue(exact.canThrow(0, s));
        exact.thrown(s);
        for (int tick = 1; tick <= 20; tick++) exact.cool(tick, s);
        assertEquals(100, exact.heat(), 0);assertTrue(exact.disabled(20));
    }
    @Test public void combatActivityRestartsTheFullQuietWindowWithoutSpendingCharge() {
        var g = new GuardState();var s = settings();g.hit(0, s, 6);
        g.activity(99);g.cool(100, s);assertEquals(30, g.heat(), 0);
        g.cool(198, s);assertEquals(30, g.heat(), 0);
        g.cool(199, s);assertEquals(29.5, g.heat(), 0);
        g.activity(200);g.cool(299, s);assertEquals(29.5, g.heat(), 0);
        g.cool(300, s);assertEquals(29, g.heat(), 0);
    }
    @Test public void reflectionAfterThrowAddsScaledCostAndRestartsQuietTime() {
        var g = new GuardState();var s = settings();g.thrown(s);
        for (int tick = 1; tick <= 20; tick++) g.cool(tick, s);
        g.hit(40, s, 4);assertEquals(95, g.heat(), 0);
        g.cool(139, s);assertEquals(95, g.heat(), 0);
        g.cool(140, s);assertEquals(94.5, g.heat(), 0);
    }
    @Test public void additiveDefaultsAndInvalidTuningAreValidated() {
        var c = config();c.set("throw-cost-percent", null);c.set("throw-drain-ticks", null);
        c.set("guard-damage-multiplier", null);
        assertEquals(75, SaberSettings.read(c).throwCostPercent(), 0);
        assertEquals(20, SaberSettings.read(c).throwDrainTicks());
        assertEquals(5, SaberSettings.read(c).damageMultiplier(), 0);
        c.set("guard-damage-multiplier", 2);assertEquals(2, SaberSettings.read(c).damageMultiplier(), 0);
        c.set("guard-damage-multiplier", 0);
        assertThrows(IllegalArgumentException.class, () -> SaberSettings.read(c));
        c.set("guard-damage-multiplier", 5);c.set("throw-cost-percent", 101);
        assertThrows(IllegalArgumentException.class, () -> SaberSettings.read(c));
        c.set("throw-cost-percent", 75);c.set("throw-drain-ticks", 0);
        assertThrows(IllegalArgumentException.class, () -> SaberSettings.read(c));
    }
    @Test public void frontAndBackAreNotEquivalent() {
        assertTrue(GuardState.facing(1,80));assertFalse(GuardState.facing(-1,80));assertFalse(GuardState.facing(0,80));
    }
    @Test public void rejectsMissingAndInvalidSettings() {
        var c=new YamlConfiguration();c.set("schema-version",2);
        assertThrows(IllegalArgumentException.class,()->SaberSettings.read(c));
    }
}
