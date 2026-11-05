package net.mandomc.gameplay.lightsaber;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SaberThrowMathTest {

    @Test
    void computesExpectedArcOffsetsAtKeyProgressPoints() {
        Vector side = new Vector(1, 0, 0);
        Vector forward = new Vector(0, 0, 1);

        Vector start = SaberThrowMath.targetOffset(0.0, side, forward, 2.5, 0.5);
        Vector middle = SaberThrowMath.targetOffset(0.5, side, forward, 2.5, 0.5);
        Vector end = SaberThrowMath.targetOffset(1.0, side, forward, 2.5, 0.5);

        assertEquals(2.5, start.getX(), 1.0e-6);
        assertEquals(0.0, start.getY(), 1.0e-6);
        assertEquals(0.0, start.getZ(), 1.0e-6);

        assertEquals(0.0, middle.getX(), 1.0e-6);
        assertEquals(0.5, middle.getY(), 1.0e-6);
        assertEquals(2.5, middle.getZ(), 1.0e-6);

        assertEquals(-2.5, end.getX(), 1.0e-6);
        assertEquals(0.0, end.getY(), 1.0e-6);
        assertEquals(0.0, end.getZ(), 1.0e-6);
    }

    @Test
    void computesMoveDeltaTowardArcTargetWithMultiplier() {
        Vector current = new Vector(0, 0, 0);
        Vector arcCenter = new Vector(2, 0, 0);
        Vector targetOffset = new Vector(1, 0.5, 0);

        Vector move = SaberThrowMath.moveDelta(current, arcCenter, targetOffset, 0.5);

        assertEquals(1.5, move.getX(), 1.0e-6);
        assertEquals(0.25, move.getY(), 1.0e-6);
        assertEquals(0.0, move.getZ(), 1.0e-6);
    }
}
