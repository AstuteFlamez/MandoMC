package net.mandomc.gameplay.lightsaber;

import org.bukkit.util.Vector;

/**
 * Stateless throw-path math helpers for lightsaber arcs.
 */
public final class SaberThrowMath {

    private SaberThrowMath() {}

    public static Vector targetOffset(double progress, Vector side, Vector forward, double radius, double arcHeight) {
        double angle = progress * Math.PI;
        Vector offset = side.clone().multiply(Math.cos(angle) * radius)
                .add(forward.clone().multiply(Math.sin(angle) * radius));
        offset.setY(Math.sin(angle) * arcHeight);
        return offset;
    }

    public static Vector moveDelta(Vector currentPosition, Vector arcCenter, Vector targetOffset, double moveMultiplier) {
        Vector targetPoint = arcCenter.clone().add(targetOffset);
        return targetPoint.subtract(currentPosition).multiply(moveMultiplier);
    }
}
