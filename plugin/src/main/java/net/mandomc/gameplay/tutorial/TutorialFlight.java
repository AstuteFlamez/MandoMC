package net.mandomc.gameplay.tutorial;

import net.mandomc.gameplay.vehicles.VehicleMotion;
import net.mandomc.gameplay.tutorial.TutorialProgress.Step;

/** Restricts lesson keys without changing the normal aircraft's motion or mouse steering. */
public final class TutorialFlight {
    private TutorialFlight() { }

    public static VehicleMotion.Input controls(Step step, VehicleMotion.Input raw, boolean grounded) {
        double forward = switch (step) {
            case FLIGHT -> Math.max(0, raw.forward());
            case STOP -> Math.min(0, raw.forward());
            default -> 0;
        };
        double side = step == Step.RIGHT ? Math.min(0, raw.side())
                : step == Step.LEFT ? Math.max(0, raw.side()) : 0;
        return new VehicleMotion.Input(forward, side, false,
                step == Step.LAND && raw.sneak() && !grounded, raw.yaw(), raw.pitch());
    }
}
