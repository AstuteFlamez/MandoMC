package net.mandomc.gameplay.tutorial;

import static org.junit.Assert.*;
import net.mandomc.gameplay.tutorial.TutorialProgress.Step;
import net.mandomc.gameplay.vehicles.VehicleDefinition;
import net.mandomc.gameplay.vehicles.VehicleMotion;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

public class TutorialFlightTest {
    private VehicleDefinition xwing() {
        return VehicleDefinition.read("xwing", YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
                getClass().getResourceAsStream("/vehicles/xwing.yml"), java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Test public void lessonUsesNormalLatchedThrustAndMouseSteering() {
        var normal = new VehicleMotion();
        var training = new VehicleMotion();
        var definition = xwing();
        for (int tick = 0; tick < 140; tick++) {
            var input = new VehicleMotion.Input(tick == 0 ? 1 : 0, 0, false, false,
                    tick < 70 ? -45 : 90, tick < 30 ? -20 : 0);
            var expected = normal.tick(definition, input, false, 0);
            var actual = training.tick(definition, TutorialFlight.controls(Step.FLIGHT, input, false), false, 0);
            assertEquals(expected, actual);
            if (tick == 60) assertEquals(definition.speed(), Math.hypot(actual.x(), actual.z()), 1e-9);
            if (tick == 100) assertTrue("Changing look direction turns normal thrust", actual.x() < -2);
        }
        for (int tick = 0; tick < 30; tick++) {
            var stop = new VehicleMotion.Input(-1, 0, false, false, 90, 0);
            assertEquals(normal.tick(definition, stop, false, 0),
                    training.tick(definition, TutorialFlight.controls(Step.STOP, stop, false), false, 0));
        }
        var idle = new VehicleMotion.Input(0, 0, false, false, 90, 0);
        assertEquals(0, training.tick(definition, TutorialFlight.controls(Step.TARGET, idle, false), false, 0).x(), 0);
    }

    @Test public void otherKeysCannotBrakeBoostClimbOrBankOutsideTheirLesson() {
        var all = new VehicleMotion.Input(1, -1, true, true, 73, -24);
        for (var step : new Step[]{Step.BOARD, Step.TARGET, Step.LAND, Step.RIGHT, Step.LEFT, Step.STOP}) {
            var input = TutorialFlight.controls(step, all, false);
            assertEquals(0, input.forward(), 0);
            assertFalse(input.jump());
            assertEquals(73, input.yaw(), 0); assertEquals(-24, input.pitch(), 0);
            assertEquals(step == Step.LAND, input.sneak());
            assertEquals(step == Step.RIGHT ? -1 : 0, input.side(), 0);
        }
        var brake = new VehicleMotion.Input(-1, 1, true, true, 30, 10);
        var flight = TutorialFlight.controls(Step.FLIGHT, brake, false);
        assertEquals(0, flight.forward(), 0); assertEquals(0, flight.side(), 0);
        assertFalse(flight.jump()); assertFalse(flight.sneak());
        assertEquals(1, TutorialFlight.controls(Step.LEFT, brake, false).side(), 0);
        assertFalse(TutorialFlight.controls(Step.LAND, all, true).sneak());
    }
}
