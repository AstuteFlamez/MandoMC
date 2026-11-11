package net.mandomc.gameplay.saber;

/** Player-owned charge; native item damage only displays this state. */
public final class GuardState {
    private double heat;
    private int lastHit;
    private int disabledUntil;
    private double pendingDrain;
    private int drainTicks;
    private boolean empty;
    public boolean disabled(int tick) { return tick < disabledUntil || empty; }
    public int stunTicks(int tick) { return Math.max(empty ? 1 : 0, disabledUntil - tick); }
    public double heat() { return heat; }
    public boolean recovering() { return heat > 0 || drainTicks > 0; }
    public void activity(int tick) { lastHit = tick; }
    public boolean hit(int tick, SaberSettings s, double damage) {
        if (!Double.isFinite(damage) || damage <= 0) return false;
        return drain(damage * s.damageMultiplier(), tick, s);
    }
    public boolean canThrow(int tick, SaberSettings s) {
        return !disabled(tick) && s.capacity() - heat - pendingDrain + 1e-9 >= s.capacity() * s.throwCostPercent() / 100;
    }
    public void thrown(SaberSettings s) {
        pendingDrain += s.capacity() * s.throwCostPercent() / 100;
        drainTicks = s.throwDrainTicks();
    }
    private boolean drain(double amount, int tick, SaberSettings s) {
        double previous = heat;
        heat = Math.min(s.capacity(), heat + amount);
        // Fractional per-tick drain must still exhaust an exactly funded throw.
        if (s.capacity() - heat < 1e-9) heat = s.capacity();
        empty = heat >= s.capacity();
        lastHit = tick;
        if (previous < s.capacity() && heat >= s.capacity()) {
            disabledUntil = tick + s.overheat();
            return true;
        }
        return false;
    }
    public boolean cool(int tick, SaberSettings s) {
        if (drainTicks > 0) {
            double amount = pendingDrain / drainTicks;
            pendingDrain -= amount;
            drainTicks--;
            return drain(amount, tick, s);
        } else if (tick - lastHit >= s.recoveryDelay()) {
            // Exhaustion never grants an instant refill when its lockout ends.
            heat = Math.max(0, heat - s.cooling());
            empty = heat >= s.capacity();
        }
        return false;
    }
    public static boolean facing(double dot, double halfAngle) { return dot >= Math.cos(Math.toRadians(halfAngle)); }
}
