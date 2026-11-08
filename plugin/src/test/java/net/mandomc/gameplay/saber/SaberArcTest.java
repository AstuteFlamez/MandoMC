package net.mandomc.gameplay.saber;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.Test;
import static org.junit.Assert.*;

public class SaberArcTest {
    private final Vector origin = new Vector(10, 70, -20);
    private YamlConfiguration config() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(
                getClass().getResourceAsStream("/sabers.yml"), StandardCharsets.UTF_8));
    }
    private SaberSettings settings() { return SaberSettings.read(config()); }
    private SaberArc arc(double distance, SaberArc.Style style) {
        return new SaberArc(origin, new Vector(0, 0, 1), new Vector(-1, 0, 0), 12, distance, style);
    }

    @Test public void variedThrowsRemainBoundedHitTheChosenTargetAndCatchMovingHand() {
        Vector hand = origin.clone().add(new Vector(3, 1, -2));
        Vector correction = new Vector(.4, -.3, .2);
        Vector target = origin.clone().add(new Vector(0, 0, 8)).add(correction);
        for (int seed = 0; seed < 300; seed++) {
            var style = SaberArc.Style.sample(settings(), new Random(seed));
            SaberArc arc = arc(8, style);
            assertEquals(0, arc.position(0, correction, hand).distance(origin), 1e-9);
            assertEquals(0, arc.position(1, correction, hand).distance(hand), 1e-9);
            assertEquals(0, arc.position(arc.attackTime(), correction, hand).distance(target), 1e-9);
            double highestReturn = 0;
            for (int i = 0; i <= 200; i++) {
                double t = i / 200.0;
                Vector relative = arc.position(t, new Vector(), origin).subtract(origin);
                relative.checkFinite();
                assertTrue("oval remains compact relative to its reach", Math.abs(relative.getX()) < 6);
                assertTrue(relative.length() <= 12.00001);
                if (t > arc.turnTime()) highestReturn = Math.max(highestReturn, relative.getY());
            }
            assertTrue("recovery visibly clears the target", highestReturn > .7);
        }
    }

    @Test public void joinsDoNotKinkAtCutOrTurn() {
        var style = SaberArc.Style.sample(settings(), new Random(42));
        var arc = arc(8, style);
        double step = 1e-5;
        for (double t : new double[]{arc.attackTime(), arc.turnTime()}) {
            Vector left = arc.position(t, new Vector(), origin).subtract(arc.position(t - step, new Vector(), origin));
            Vector right = arc.position(t + step, new Vector(), origin).subtract(arc.position(t, new Vector(), origin));
            assertTrue(left.normalize().dot(right.normalize()) > .9999);
        }
    }

    @Test public void cutAndTurnPreserveAccelerationWithTrackingAndMovingHand() {
        double h = 1e-5;
        Vector offset = new Vector(.5, -.3, .2), hand = origin.clone().add(new Vector(2, .5, -1));
        for (int seed = 0; seed < 100; seed++) {
            var style = SaberArc.Style.sample(settings(), new Random(seed));
            for (double distance : new double[]{0, 8}) {
                var arc = arc(distance, style);
                for (double t : new double[]{arc.attackTime(), arc.turnTime()}) {
                    Vector at = arc.position(t, offset, hand);
                    Vector left = at.clone().subtract(arc.position(t - h, offset, hand).multiply(2))
                            .add(arc.position(t - 2 * h, offset, hand)).multiply(1 / (h * h));
                    Vector right = arc.position(t + 2 * h, offset, hand)
                            .subtract(arc.position(t + h, offset, hand).multiply(2)).add(at).multiply(1 / (h * h));
                    assertTrue("acceleration must not jump between legs",
                            left.distance(right) / Math.max(1, left.length()) < .01);
                }
            }
        }
    }

    @Test public void everyStyleMakesOneConvexLoopWithoutFigureEightCrossovers() {
        for (int seed = 0; seed < 300; seed++) {
            var style = SaberArc.Style.sample(settings(), new Random(seed));
            for (double distance : new double[]{0, 8}) {
                var arc = arc(distance, style);
                Vector[] points = new Vector[256];
                for (int i = 0; i < points.length; i++) points[i] = arc.position(i / 256.0, new Vector(), origin);
                double circulation = 0, sign = 0;
                for (int i = 0; i < points.length; i++) {
                    Vector before = points[(i + 1) % points.length].clone().subtract(points[i]).setY(0);
                    Vector after = points[(i + 2) % points.length].clone().subtract(points[(i + 1) % points.length]).setY(0);
                    double cross = before.clone().crossProduct(after).getY();
                    if (i == 0) sign = Math.signum(cross);
                    assertTrue("the footprint must keep turning one way instead of weaving", cross * sign > 0);
                    circulation += Math.atan2(cross, before.dot(after));
                }
                assertEquals("one complete loop, with no second lobe", 2 * Math.PI, Math.abs(circulation), 1e-6);
            }
        }
    }

    @Test public void variationChangesSilhouetteHeightSpinAndSideWithoutPerFrameJitter() {
        var random = new Random(12);
        double minWidth = 10, maxWidth = 0, minHeight = 10, maxHeight = 0, minSpin = 100, maxSpin = 0;
        boolean left = false, right = false;
        for (int i = 0; i < 200; i++) {
            var style = SaberArc.Style.sample(settings(), random);
            minWidth = Math.min(minWidth, style.width());maxWidth = Math.max(maxWidth, style.width());
            minHeight = Math.min(minHeight, style.recovery());maxHeight = Math.max(maxHeight, style.recovery());
            minSpin = Math.min(minSpin, Math.abs(style.spin()));maxSpin = Math.max(maxSpin, Math.abs(style.spin()));
            left |= style.side() < 0;right |= style.side() > 0;
            var arc = arc(8, style);
            assertEquals(arc.position(.7, new Vector(), origin), arc.position(.7, new Vector(), origin));
        }
        assertTrue(left && right && maxWidth - minWidth > 1 && maxHeight - minHeight > 1.8 && maxSpin - minSpin > 20);
        var config = config();config.set("throw-variation", 0);
        assertEquals(SaberArc.Style.sample(SaberSettings.read(config), new Random(1)),
                SaberArc.Style.sample(SaberSettings.read(config), new Random(2)));
    }

    @Test public void verticalAimAndVeryCloseTargetsRemainFinite() {
        for (int sign : new int[]{-1, 1}) {
            var style = SaberArc.Style.sample(settings(), new Random(9));
            var arc = new SaberArc(origin, new Vector(0, sign, 0), new Vector(-1, 0, 0), 2.5, 1, style);
            for (int i = 0; i <= 100; i++) arc.position(i / 100.0, new Vector(), origin).checkFinite();
            assertEquals(0, arc.position(1, new Vector(), origin).distance(origin), 1e-9);
        }
    }

    @Test public void distanceTimingKeepsLongThrowsMovingThroughTurnAndLevelsTheCatch() {
        var settings = settings();
        assertEquals(18, settings.range(), 0);
        for (int seed = 0; seed < 300; seed++) {
            var style = SaberArc.Style.sample(settings, new Random(seed));
            var arc = new SaberArc(origin, new Vector(0, 0, 1), new Vector(-1, 0, 0), 18, 0, style);
            var motion = new SaberMotion(arc, style, settings, origin, 0);
            Vector previous = origin, priorVelocity = null;
            double farthest = 0;
            int ticks = 0;
            while (motion.progress() < 1 && ticks++ < 100) {
                Vector current = motion.next(previous, origin, new Vector(), false).center();
                Vector velocity = current.clone().subtract(previous);
                farthest = Math.max(farthest, current.getZ() - origin.getZ());
                if (motion.progress() < .97) {
                    assertTrue("no mid-throw stall: " + velocity.length(), velocity.length() > .25);
                    if (priorVelocity != null) {
                        assertTrue("speed changes gently: " + velocity.length() + "/" + priorVelocity.length(),
                                Math.abs(velocity.length() - priorVelocity.length()) < .14);
                        assertTrue("turn is spread across frames",
                                velocity.clone().normalize().dot(priorVelocity.clone().normalize()) > Math.cos(Math.toRadians(50)));
                    }
                }
                priorVelocity = velocity;
                previous = current;
            }
            assertTrue("reaches the extended range", farthest > 17.5);
            assertTrue("bounded catch without a straight catch-up phase: " + ticks, ticks < 60);
            assertEquals(0, previous.distance(origin), 1e-8);
            Vector approach = arc.position(1, new Vector(), origin)
                    .subtract(arc.position(.995, new Vector(), origin)).normalize();
            assertTrue("catch arrives almost level", Math.abs(approach.getY()) < .12);
        }
    }

    @Test public void movingHandAndVerticalAimKeepTiltSmoothAndCatchWithinLifetime() {
        for (Vector aim : new Vector[]{new Vector(0, 0, 1), new Vector(0, .7, 1).normalize(),
                new Vector(0, 1, 0), new Vector(0, -1, 0)}) {
            var style = SaberArc.Style.sample(settings(), new Random(42));
            var arc = new SaberArc(origin, aim, new Vector(-1, 0, 0), 18, 0, style);
            var motion = new SaberMotion(arc, style, settings(), origin, 0);
            var previous = motion.initialPose(origin);
            var up = new org.joml.Vector3f(0, 0, -1);
            var priorNormal = previous.rotation().transform(new org.joml.Vector3f(up));
            double maxTilt = 0;
            Vector hand = origin.clone();
            int ticks = 0;
            do {
                if (ticks++ < 30) hand.add(new Vector(.22, .015, 0));
                var pose = motion.next(previous.center(), hand, new Vector(), false);
                pose.center().checkFinite();
                assertTrue("moving owner does not yank the return", pose.center().distance(previous.center()) < 2);
                var normal = pose.rotation().transform(new org.joml.Vector3f(up));
                double angle = Math.acos(Math.clamp(normal.dot(priorNormal), -1, 1));
                assertTrue("spin plane changes at most six degrees per tick", angle < Math.toRadians(6.01));
                maxTilt = Math.max(maxTilt, Math.acos(Math.clamp(normal.y, -1, 1)));
                priorNormal = normal;
                previous = pose;
            } while ((motion.progress() < 1 || previous.center().distance(hand) >= .6) && ticks < 100);
            assertTrue("return reaches walking owner", ticks < 100);
            assertTrue("tilt responds to the path", maxTilt > Math.toRadians(8));
        }
    }

    @Test public void climbTiltsSpinPlaneAndStationaryRecallKeepsAStableOrientation() {
        var style = SaberArc.Style.sample(settings(), new Random(4));
        var arc = new SaberArc(origin, new Vector(0, .7, 1).normalize(), new Vector(-1, 0, 0), 18, 0, style);
        var motion = new SaberMotion(arc, style, settings(), origin, 0);
        var pose = motion.initialPose(origin);
        Vector last = origin;
        for (int i = 0; i < 8; i++) {
            last = pose.center();
            pose = motion.next(last, origin, new Vector(), false);
        }
        var normal = pose.rotation().transform(new org.joml.Vector3f(0, 0, -1));
        Vector velocity = pose.center().clone().subtract(last).normalize();
        assertTrue("climbing tips the front of the disc upward", normal.z < -.01);
        assertTrue(velocity.getY() > 0);
        var stopped = motion.next(pose.center(), pose.center(), new Vector(), true);
        var stoppedNormal = stopped.rotation().transform(new org.joml.Vector3f(0, 0, -1));
        assertEquals(0, stoppedNormal.distance(normal), 1e-6);
    }

    @Test public void variedCloseAndDistantHitsStayOnTheBladePathAndReturn() {
        for (double distance : new double[]{1, 2, 5, 10, 17}) {
            for (int seed = 0; seed < 20; seed++) {
                var style = SaberArc.Style.sample(settings(), new Random(seed));
                var arc = new SaberArc(origin, new Vector(0, 0, 1), new Vector(-1, 0, 0),
                        Math.min(18, distance + 1.5), distance, style);
                var motion = new SaberMotion(arc, style, settings(), origin, 0);
                var previous = motion.initialPose(origin);
                Vector center = origin.clone().add(new Vector(0, 0, distance));
                var target = BoundingBox.of(center, .3, .7, .3);
                boolean touched = false;
                int ticks = 0;
                while (motion.progress() < 1 && ticks++ < 100) {
                    var next = motion.next(previous.center(), origin, new Vector(), false);
                    int steps = SaberBlade.steps(previous, next);
                    for (int i = 0; i <= steps; i++) {
                        touched |= SaberBlade.touches(target, previous.interpolate(next, (double) i / steps), .22);
                    }
                    previous = next;
                }
                assertTrue("assisted crossing reaches the actual blade sweep", touched);
                assertTrue("close and distant loops both return within lifetime", ticks < 100);
                assertEquals(0, previous.center().distance(origin), 1e-7);
            }
        }
    }

    @Test public void bladeEdgeHitsButSpaceOutsideBladeDoesNot() {
        var pose = SaberBlade.pose(new Vector(), 0, 0, 90);
        var edge = new BoundingBox(-1.15, -.2, -.2, -.85, .2, .2);
        assertTrue(SaberBlade.touches(edge, pose, .22));
        assertFalse(SaberBlade.touches(new BoundingBox(-2, -.2, -.2, -1.6, .2, .2), pose, .22));
        assertFalse(SaberBlade.touches(new BoundingBox(-.1, 1, -.1, .1, 1.5, .1), pose, .22));
    }

    @Test public void rotationAndTranslationAreSubdividedToPreventSkippedContacts() {
        var start = SaberBlade.pose(new Vector(), 0, 0, 0);
        var end = SaberBlade.pose(new Vector(2, 0, 0), 0, 30, 60);
        int steps = SaberBlade.steps(start, end);
        assertTrue(steps > 20);
        var previous = start;
        for (int i = 1; i <= steps; i++) {
            var pose = start.interpolate(end, (double) i / steps);
            assertTrue(previous.base().distance(pose.base()) < .121);
            assertTrue(previous.tip().distance(pose.tip()) < .121);
            previous = pose;
        }
        var transformedCenter = end.transformation().getLeftRotation()
                .transform(new org.joml.Vector3f(.00625f,1.34375f,-.09375f))
                .add(end.transformation().getTranslation());
        assertEquals(0, transformedCenter.length(), 1e-6);
    }

    @Test public void contactCanDamageAgainOnlyAfterLeaving() {
        SaberContacts contacts = new SaberContacts();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertTrue(contacts.enter(first));assertFalse(contacts.enter(first));assertTrue(contacts.enter(second));
        contacts.retain(Set.of(first));assertFalse(contacts.enter(first));assertTrue(contacts.enter(second));
        contacts.retain(Set.of());assertTrue(contacts.enter(first));
    }

    @Test public void oldConfigsGetDefaultsAndInvalidOverridesFail() {
        var config = config();
        config.set("throw-return-height", null);config.set("throw-variation", null);config.set("throw-spin-degrees", null);
        assertEquals(2.4, SaberSettings.read(config).returnHeight(), 0);
        assertEquals(1, SaberSettings.read(config).variation(), 0);
        assertEquals(38, SaberSettings.read(config).spinDegrees(), 0);
        config.set("throw-variation", 1.1);
        assertThrows(IllegalArgumentException.class, () -> SaberSettings.read(config));
    }
}
