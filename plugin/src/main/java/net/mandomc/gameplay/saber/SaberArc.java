package net.mandomc.gameplay.saber;

import java.util.Arrays;
import java.util.random.RandomGenerator;
import org.bukkit.util.Vector;

/** A single convex oval, aimed through the selected mob without crossing the two legs. */
public final class SaberArc {
    public record Style(double side, double width, double rise, double recovery, double bank,
            double spin, double tempo) {
        public static Style sample(SaberSettings settings, RandomGenerator random) {
            double variation = settings.variation();
            double side = random.nextDouble() < 0.3 * variation ? 1 : -1;
            return new Style(side, settings.arcWidth() * vary(random, variation, .55, 1.25),
                    settings.arcHeight() * vary(random, variation, .15, 1.8),
                    settings.returnHeight() * vary(random, variation, .55, 1.45),
                    settings.bankDegrees() * vary(random, variation, .45, 1.5),
                    settings.spinDegrees() * vary(random, variation, .75, 1.4) * -side,
                    vary(random, variation, .88, 1.15));
        }

        private static double vary(RandomGenerator random, double amount, double min, double max) {
            return 1 + (random.nextDouble(min, max) - 1) * amount;
        }
    }

    private final Vector origin;
    private final Vector forward;
    private final Vector lateral;
    private final Vector up;
    private final Vector localRight;
    private final Vector localUp;
    private final Vector localForward;
    private final double reach;
    private final double width;
    private final double rise;
    private final double recovery;
    private final double scale;
    private final double attackTime;
    private final double[] distances = new double[769];
    private final double[] speedLimits = new double[769];

    public SaberArc(Vector origin, Vector direction, Vector right, double reach,
            double targetDistance, Style style) {
        this.origin = origin.clone();
        this.forward = direction.clone().normalize();
        this.lateral = right.clone().normalize().multiply(style.side());
        this.up = right.clone().normalize().crossProduct(forward).normalize();
        this.reach = reach;
        // Enough width to round the ends while keeping the loop compact compared with its reach.
        this.width = Math.min(reach * .3, reach * .06 + style.width() * .85);
        this.rise = Math.min(style.rise(), reach * .16);
        this.recovery = Math.min(style.recovery(), reach * .38);
        double maximum = 0;
        for (int i = 0; i <= 768; i++) maximum = Math.max(maximum, local(i / 768.0).length());
        this.scale = reach / (maximum + .001);
        double cutDistance = targetDistance > 0 ? targetDistance : reach * .72;
        double low = 0, high = .5;
        for (int i = 0; i < 50; i++) {
            double mid = (low + high) * .5;
            if (local(mid).length() * scale < cutDistance) low = mid;
            else high = mid;
        }
        this.attackTime = (low + high) * .5;
        // Rotate the complete oval so its outward leg crosses the target. Pulling one waypoint
        // onto the target would bend across the return and recreate the figure-eight.
        this.localForward = targetDistance > 0 ? local(attackTime).normalize() : new Vector(0, 0, 1);
        this.localRight = new Vector(0, 1, 0).crossProduct(localForward).normalize();
        this.localUp = localForward.clone().crossProduct(localRight).normalize();
        Vector[] samples = new Vector[distances.length];
        samples[0] = origin.clone();
        Vector previous = origin;
        for (int i = 1; i < distances.length; i++) {
            Vector current = base((double) i / (distances.length - 1));
            samples[i] = current;
            distances[i] = distances[i - 1] + current.distance(previous);
            previous = current;
        }
        Arrays.fill(speedLimits, 10);
        for (int i = 1; i < samples.length - 1; i++) {
            Vector before = samples[i].clone().subtract(samples[i - 1]);
            Vector after = samples[i + 1].clone().subtract(samples[i]);
            double span = .5 * (before.length() + after.length());
            double curvature = before.normalize().subtract(after.normalize()).length() / Math.max(1e-8, span);
            speedLimits[i] = Math.sqrt(.25 / Math.max(1e-8, curvature));
        }
        // Plan braking before a bend instead of discovering it at the old leg junction.
        for (int i = speedLimits.length - 2; i >= 0; i--) {
            speedLimits[i] = Math.min(speedLimits[i], Math.sqrt(speedLimits[i + 1] * speedLimits[i + 1]
                    + .2 * (distances[i + 1] - distances[i])));
        }
        for (int i = 1; i < speedLimits.length; i++) {
            speedLimits[i] = Math.min(speedLimits[i], Math.sqrt(speedLimits[i - 1] * speedLimits[i - 1]
                    + .2 * (distances[i] - distances[i - 1])));
        }
    }

    /** Analytic closed loop; one circulation direction and no piecewise path junctions. */
    private Vector local(double t) {
        double angle = 2 * Math.PI * t;
        double sine = Math.sin(angle), envelope = (1 - Math.cos(angle)) * .5;
        double height = .6 * envelope * (rise * (1 + sine) + recovery * (1 - sine)) * .5;
        if (t > .5) height += .6 * recovery * Math.pow(sine, 4);
        return new Vector(width * sine, height, reach * envelope);
    }

    private Vector base(double t) {
        if (t <= 0 || t >= 1) return origin.clone();
        Vector point = local(t).multiply(scale);
        return origin.clone().add(lateral.clone().multiply(point.dot(localRight)))
                .add(up.clone().multiply(point.dot(localUp))).add(forward.clone().multiply(point.dot(localForward)));
    }

    public double attackTime() { return attackTime; }
    public double turnTime() { return .5; }

    public Vector position(double progress, Vector targetOffset, Vector hand) {
        double t = Math.clamp(progress, 0, 1);
        double attack = smooth(t / attackTime);
        double recover = smooth((t - turnTime()) / (1 - turnTime()));
        return base(t).add(targetOffset.clone().multiply(attack * (1 - recover)))
                .add(hand.clone().subtract(origin).multiply(recover));
    }

    public double speedLimit(double distance) {
        double index = progressAt(distance) * (distances.length - 1);
        int low = Math.min((int) index, distances.length - 2);
        return speedLimits[low] + (speedLimits[low + 1] - speedLimits[low]) * (index - low);
    }

    public double length() { return distances[distances.length - 1]; }

    public double distanceAt(double progress) {
        double index = Math.clamp(progress, 0, 1) * (distances.length - 1);
        int low = Math.min((int) index, distances.length - 2);
        return distances[low] + (distances[low + 1] - distances[low]) * (index - low);
    }

    public double progressAt(double distance) {
        double clamped = Math.clamp(distance, 0, length());
        int high = Arrays.binarySearch(distances, clamped);
        if (high >= 0) return (double) high / (distances.length - 1);
        high = -high - 1;
        int low = high - 1;
        double local = (clamped - distances[low]) / (distances[high] - distances[low]);
        return (low + local) / (distances.length - 1);
    }

    private static double smooth(double value) {
        double t = Math.clamp(value, 0, 1);
        // Zero first AND second derivatives at both ends preserve the spline's smooth joins.
        return t * t * t * (10 + t * (-15 + 6 * t));
    }
}
