package net.mandomc.gameplay.travel;
import org.bukkit.Location;
import org.junit.Test;
import static org.junit.Assert.*;
public class MovementTest {
    @Test public void cameraRotationDoesNotCancel() {
        assertFalse(TeleportService.hasMoved(new Location(null, 1, 70, 2, 0, 0),
                new Location(null, 1, 70, 2, 179, -85)));
    }
    @Test public void walkingJumpingAndFallingCancel() {
        var origin = new Location(null, 1, 70, 2);
        for (var offset : new double[][] {{0.01,0,0},{0,0,0.01},{0,0.01,0},{0,-0.01,0}}) {
            assertTrue(TeleportService.hasMoved(origin, origin.clone().add(offset[0],offset[1],offset[2])));
        }
    }
    @Test public void coordinateNoiseDoesNotCancel() {
        var origin = new Location(null, 1, 70, 2);
        assertFalse(TeleportService.hasMoved(origin, origin.clone().add(0.00001,0,0)));
    }
}
