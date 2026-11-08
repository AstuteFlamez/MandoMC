package net.mandomc.gameplay.combat;

import java.util.function.Predicate;
import org.junit.Test;
import static org.junit.Assert.*;

public class CombatBoundaryTest {
    private final Predicate<CombatBoundary.Cell> zone = p -> p.x() >= -144 && p.x() <= -129
            && p.y() >= -64 && p.y() <= 319 && p.z() >= -144 && p.z() <= -129;

    @Test public void allFourFacesBlockEntry() {
        assertTrue(CombatBoundary.enters(-145, 73, -136, -143.9, 73, -136, zone));
        assertTrue(CombatBoundary.enters(-127, 73, -136, -128.1, 73, -136, zone));
        assertTrue(CombatBoundary.enters(-136, 73, -145, -136, 73, -143.9, zone));
        assertTrue(CombatBoundary.enters(-136, 73, -127, -136, 73, -128.1, zone));
    }
    @Test public void crossedZoneBlocksEvenWhenBothEndpointsAreOutside() {
        assertTrue(CombatBoundary.enters(-160, 73, -136, -120, 73, -136, zone));
        assertTrue(CombatBoundary.enters(-136, 400, -136, -136, -100, -136, zone));
    }
    @Test public void thinDiagonalClipIsNotMissedBySampling() {
        assertTrue(CombatBoundary.enters(-1, .5, .999, 1, .5, 1.0009,
                p -> p.x() == 0 && p.y() == 0 && p.z() == 0));
    }
    @Test public void exactCornerTouchDoesNotCountAsEntering() {
        assertFalse(CombatBoundary.enters(-1, .5, 1, 1, .5, -1,
                p -> p.x() == 0 && p.z() == 0));
    }
    @Test public void leavingAndMovementParallelToBorderAreAllowed() {
        assertFalse(CombatBoundary.enters(-143.5, 73, -136, -145, 73, -136, zone));
        assertFalse(CombatBoundary.enters(-144.01, 73, -140, -144.01, 73, -132, zone));
        assertFalse(CombatBoundary.enters(-144.01, 73, -136, -144.01, 73, -136, zone));
    }
    @Test public void exactNegativeBoundaryIsPartOfSafeSide() {
        assertTrue(CombatBoundary.enters(-144.1, 73, -136, -144, 73, -136, zone));
        assertFalse(CombatBoundary.enters(-144.1, 73, -136, -144.000001, 73, -136, zone));
    }
    @Test public void wallIsLocalAndOnlyOnSafeSide() {
        var wall = CombatBoundary.wall(new CombatBoundary.Cell(-146, 73, -136), 6, 1, 4, zone);
        assertFalse(wall.isEmpty());
        assertTrue(wall.stream().allMatch(zone));
        assertTrue(wall.stream().allMatch(p -> p.x() == -144));
        assertTrue(wall.stream().allMatch(p -> p.y() >= 72 && p.y() <= 77));
    }
    @Test public void globalProtectionAndUnprotectedWorldHaveNoFalseEdges() {
        var center = new CombatBoundary.Cell(0, 70, 0);
        assertTrue(CombatBoundary.wall(center, 6, 1, 4, p -> true).isEmpty());
        assertTrue(CombatBoundary.wall(center, 6, 1, 4, p -> false).isEmpty());
    }
    @Test public void invalidAndExtremeMovesFailClosed() {
        assertTrue(CombatBoundary.enters(0, 0, 0, Double.NaN, 0, 0, p -> false));
        assertTrue(CombatBoundary.enters(0, 0, 0, 1000000, 0, 0, p -> false));
    }
}
