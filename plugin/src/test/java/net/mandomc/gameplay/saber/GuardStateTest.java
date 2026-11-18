package net.mandomc.gameplay.saber;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class GuardStateTest {
    private YamlConfiguration config() { return YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
            getClass().getResourceAsStream("/combat/sabers.yml"), java.nio.charset.StandardCharsets.UTF_8)); }
    private SaberSettings settings() { return SaberSettings.read(config()); }
    @Test public void costTracksDamageIncludingFractionalHitsAndClampsAtEmpty() {
        var g = new GuardState(); var s = settings();
        g.hit(0, s, .5); assertEquals(2.5, g.heat(), 0);
        g.hit(1, s, 6); assertEquals(32.5, g.heat(), 0);
        assertTrue(g.hit(2, s, 1000)); assertEquals(100, g.heat(), 0);
    }
    @Test public void combatActivityNeverDelaysRegeneration() {
        var g = new GuardState(); var s = settings(); g.hit(0, s, 6);
        g.activity(1); g.cool(1, s); assertEquals(29.5, g.heat(), 0);
        g.hit(2, s, 1); g.cool(2, s); assertEquals(34, g.heat(), 0);
        for (double damage : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) assertFalse(g.hit(3, s, damage));
        g.cool(3, s); assertEquals(33.5, g.heat(), 0);
    }
    @Test public void exhaustionRetainsMinimumStunWhileChargeRecoversImmediately() {
        var g = new GuardState(); var s = settings(); assertTrue(g.hit(0, s, 20));
        g.cool(1, s); assertEquals(99.5, g.heat(), 0); assertTrue(g.disabled(1));
        for (int tick = 2; tick <= 80; tick++) g.cool(tick, s);
        assertFalse(g.disabled(80)); assertEquals(60, g.heat(), 0);
    }
    @Test public void throwDrainAndRecoveryHappenTogetherWithoutAPause() {
        var g = new GuardState(); var s = settings(); g.thrown(s);
        for (int tick = 1; tick <= 20; tick++) { g.cool(tick, s); assertEquals(tick * 2.05, g.heat(), 1e-9); }
        assertEquals(0, s.recoveryDelay()); assertEquals(41, g.heat(), 1e-9);
        assertFalse(g.disabled(20)); assertTrue(g.canThrow(20, s));
        g.cool(21, s); assertEquals(40.5, g.heat(), 1e-9);
    }
    @Test public void enduranceChangesAdmissionAndReservedDrainByTheSameAmount() {
        var g = new GuardState(); var s = settings(); g.hit(0, s, 12);
        assertFalse(g.canThrow(0, s)); assertFalse(g.canThrow(0, s, .8));
        g.cool(1, s); g.cool(2, s); assertTrue(g.canThrow(2, s, .8));
        g.thrown(s, .8); assertFalse(g.canThrow(2, s, .8));
        for (int tick = 3; tick < 23; tick++) g.cool(tick, s);
        assertEquals(89.8, g.heat(), 1e-9);
    }
    @Test public void exactlyFundedThrowDoesNotIgnoreSimultaneousRecovery() {
        var g = new GuardState(); var s = settings(); g.hit(0, s, 9.8);
        assertTrue(g.canThrow(0, s)); g.thrown(s);
        for (int tick = 1; tick <= 20; tick++) g.cool(tick, s);
        assertEquals(90, g.heat(), 1e-9); assertFalse(g.disabled(20));
    }
    @Test public void higherRecoveryRestoresMoreAndCannotOverfill() {
        var normal = new GuardState(); var upgraded = new GuardState(); var s = settings();
        normal.hit(0, s, 6); upgraded.hit(0, s, 6);
        for (int tick = 1; tick <= 20; tick++) { normal.cool(tick, s); upgraded.cool(tick, s, 1.75); }
        assertEquals(20, normal.heat(), 0); assertEquals(12.5, upgraded.heat(), 0);
        for (int tick = 21; tick < 100; tick++) upgraded.cool(tick, s, 1.75);
        assertEquals(0, upgraded.heat(), 0); assertFalse(upgraded.recovering());
    }
    @Test public void recoveryAppliesDuringThrowDrainAtEveryTier() {
        var g = new GuardState(); var s = settings(); g.thrown(s);
        for (int tick = 1; tick <= 20; tick++) g.cool(tick, s, 1.75);
        assertEquals(33.5, g.heat(), 1e-9);
    }
    @Test public void criticalThrowUsesVanillaSharpnessAfterTheCriticalMultiplier() {
        assertEquals(12, SaberService.criticalDamage(8, 0), 0);
        assertEquals(13, SaberService.criticalDamage(8, 1), 0);
        assertEquals(15, SaberService.criticalDamage(8, 5), 0);
    }
    @Test public void invalidTuningFailsValidation() {
        var c = config(); c.set("guard-damage-multiplier", 0);
        assertThrows(IllegalArgumentException.class, () -> SaberSettings.read(c));
        var g = new GuardState(); var s = settings();
        assertThrows(IllegalArgumentException.class, () -> g.thrown(s, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> g.cool(1, s, .5));
    }
    @Test public void frontAndBackAreNotEquivalent() {
        assertTrue(GuardState.facing(1,80)); assertFalse(GuardState.facing(-1,80)); assertFalse(GuardState.facing(0,80));
    }
}
