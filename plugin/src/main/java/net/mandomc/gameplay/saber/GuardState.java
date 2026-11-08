package net.mandomc.gameplay.saber;

/** Tick-based guard state, independent of item wear and enchantments. */
public final class GuardState {
    private double heat;
    private int lastHit;
    private int disabledUntil;
    public boolean disabled(int tick) { return tick < disabledUntil; }
    public double heat() { return heat; }
    public boolean hit(int tick, SaberSettings s) {
        heat = Math.min(s.capacity(), heat + s.cost()); lastHit = tick;
        if (heat >= s.capacity()) { disabledUntil = tick + s.overheat(); return true; }
        return false;
    }
    public void cool(int tick, SaberSettings s) {
        if (disabledUntil != 0 && tick >= disabledUntil) { heat = 0; disabledUntil = 0; }
        if (!disabled(tick) && tick - lastHit >= s.recoveryDelay()) heat = Math.max(0, heat - s.cooling());
    }
    public static boolean facing(double dot, double halfAngle) { return dot >= Math.cos(Math.toRadians(halfAngle)); }
}
