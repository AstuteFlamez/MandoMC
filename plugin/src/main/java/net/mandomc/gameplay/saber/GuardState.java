package net.mandomc.gameplay.saber;

/** Player-owned charge; native item damage only displays this state. */
public final class GuardState {
    private double heat;
    private int disabledUntil;
    private double pendingDrain;
    private int drainTicks;
    private boolean empty;
    public boolean disabled(int tick) { return tick < disabledUntil || empty; }
    public int stunTicks(int tick) { return Math.max(empty ? 1 : 0, disabledUntil - tick); }
    public double heat() { return heat; }
    public boolean recovering() { return heat > 0 || drainTicks > 0; }
    public void activity(int tick) { /* Combat never pauses continuous regeneration. */ }
    public boolean hit(int tick, SaberSettings s, double damage) {
        if (!Double.isFinite(damage) || damage <= 0) return false;
        return drain(damage * s.damageMultiplier(), tick, s);
    }
    public boolean canThrow(int tick, SaberSettings s) { return canThrow(tick, s, 1); }
    public boolean canThrow(int tick, SaberSettings s, double consumption) {
        return !disabled(tick) && s.capacity() - heat - pendingDrain + 1e-9 >= cost(s, consumption);
    }
    public void thrown(SaberSettings s) { thrown(s, 1); }
    public void thrown(SaberSettings s, double consumption) {
        pendingDrain += cost(s, consumption);
        drainTicks = s.throwDrainTicks();
    }
    private static double cost(SaberSettings s, double consumption) {
        if (!Double.isFinite(consumption) || consumption < 0 || consumption > 1) throw new IllegalArgumentException("Invalid charge consumption");
        return s.capacity() * s.throwCostPercent() / 100 * consumption;
    }
    private boolean drain(double amount, int tick, SaberSettings s) {
        double previous = heat;
        heat = Math.min(s.capacity(), heat + amount);
        if (s.capacity() - heat < 1e-9) heat = s.capacity();
        empty = heat >= s.capacity();
        if (previous < s.capacity() && empty) { disabledUntil = tick + s.overheat(); return true; }
        return false;
    }
    public boolean cool(int tick, SaberSettings s) { return cool(tick, s, 1); }
    public boolean cool(int tick, SaberSettings s, double recovery) {
        if (!Double.isFinite(recovery) || recovery < 1) throw new IllegalArgumentException("Invalid charge recovery");
        double amount = 0;
        if (drainTicks > 0) {
            amount = pendingDrain / drainTicks;
            pendingDrain -= amount;
            drainTicks--;
        }
        // Net simultaneous throw drain and regeneration before testing exhaustion.
        double net = amount - s.cooling() * recovery;
        if (net > 0) return drain(net, tick, s);
        heat = Math.max(0, heat + net); empty = heat >= s.capacity(); return false;
    }
    public static boolean facing(double dot, double halfAngle) { return dot >= Math.cos(Math.toRadians(halfAngle)); }
}
