package net.mandomc.gameplay.vehicles;

import static org.junit.Assert.*;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;

public class VehicleMotionTest {
    private VehicleDefinition definition(String id) {
        return VehicleDefinition.read(
                id,
                YamlConfiguration.loadConfiguration(
                        new java.io.InputStreamReader(
                                getClass().getResourceAsStream("/vehicles/" + id + ".yml"),
                                java.nio.charset.StandardCharsets.UTF_8)));
    }

    private VehicleMotion.Input input(double front, boolean jump, boolean sneak) {
        return new VehicleMotion.Input(front, 0, jump, sneak, 0, 0);
    }

    @Test
    public void flightLatchesThrustThenBrakesWithoutNeedingFuel() {
        var d = definition("xwing");
        var motion = new VehicleMotion();
        motion.tick(d, input(1, false, false), false, 0);
        VehicleMotion.Step step = null;
        for (int i = 0; i < 1000; i++) step = motion.tick(d, input(0, false, false), false, 0);
        assertEquals(d.speed(), step.z(), 1e-9);
        for (int i = 0; i < 100; i++) step = motion.tick(d, input(-1, false, false), false, 0);
        assertEquals(0, step.z(), 1e-9);
    }

    @Test
    public void boostNeedsNewPressHasCooldownAndExpires() {
        var d = definition("tiefighter");
        var motion = new VehicleMotion();
        for (int i = 0; i < 100; i++) motion.tick(d, input(1, false, false), false, 0);
        assertEquals(d.speed(), motion.tick(d, input(1, false, false), false, 0).z(), 1e-9);
        motion.tick(d, input(0, false, false), false, 0);
        assertTrue(motion.tick(d, input(1, false, false), false, 0).z() > d.speed());
        VehicleMotion.Step step = null;
        for (int i = 0; i < 40; i++) step = motion.tick(d, input(0, false, false), false, 0);
        assertEquals(d.speed(), step.z(), 1e-9);
        assertEquals(d.speed(), motion.tick(d, input(1, false, false), false, 0).z(), 1e-9);
    }

    @Test
    public void sneakDescendsInAirAndExitsOnlyWhenLanded() {
        var d = definition("uwing");
        var motion = new VehicleMotion();
        var step = motion.tick(d, input(0, false, true), false, 0);
        assertFalse(step.dismount());
        assertTrue(step.y() < 0);
        assertTrue(motion.tick(d, input(0, false, true), true, 0).dismount());
    }

    @Test
    public void speederStopsWhenReleasedAndCannotJumpInAir() {
        var d = definition("speederbike");
        var motion = new VehicleMotion();
        for (int i = 0; i < 60; i++) motion.tick(d, input(1, false, false), true, 0);
        assertEquals(.42, motion.tick(d, input(1, true, false), true, 0).y(), 0);
        assertEquals(-.2, motion.tick(d, input(1, true, false), false, -.2).y(), 0);
        for (int i = 0; i < 60; i++) motion.tick(d, input(0, false, false), true, 0);
        assertEquals(0, motion.tick(d, input(0, false, false), true, 0).z(), 0);
    }

    @Test
    public void pitchIsBoundedAndReverseIsSlower() {
        var motion = new VehicleMotion();
        var d = definition("xwing");
        var step = motion.tick(d, new VehicleMotion.Input(1, 1, false, false, 720, 90), false, 0);
        assertEquals(30, step.pitch(), 0);
        assertTrue(Double.isFinite(step.x()));
        motion = new VehicleMotion();
        d = definition("speederbike");
        for (int i = 0; i < 100; i++) step = motion.tick(d, input(-1, false, false), true, 0);
        assertEquals(-d.speed() * .4, step.z(), 1e-9);
    }

    @Test
    public void definitionsHaveFixedModelsNoFuelOrSkins() {
        for (String id : java.util.List.of("speederbike", "uwing", "xwing", "tiefighter")) {
            var c =
                    YamlConfiguration.loadConfiguration(
                            new java.io.InputStreamReader(
                                    getClass().getResourceAsStream("/vehicles/" + id + ".yml")));
            assertFalse(c.contains("fuel"));
            assertFalse(c.contains("skins"));
            assertEquals(id, definition(id).id());
        }
        assertEquals(java.util.List.of("seatone"), definition("uwing").passengers());
    }
}
