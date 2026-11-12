package net.mandomc.gameplay.boba;

import java.util.function.BooleanSupplier;

/** Purchased time is spent only while thrusting. Pauses never round it up to a free second. */
public final class ThrustFuel {
    public static final long SECOND = 1_000_000_000L;
    public static final long FUEL_INTERVAL = 100_000_000L;
    private long credit;
    private boolean warned;
    public ThrustFuel(long credit) { this.credit = Math.max(0, Math.min(SECOND, credit)); }
    public long credit() { return credit; }
    public boolean start(BooleanSupplier purchase) {
        if (credit > 0) return true;
        if (!purchase.getAsBoolean()) return false;
        credit = FUEL_INTERVAL;
        return true;
    }
    public boolean spend(long elapsed, BooleanSupplier purchase) {
        if (elapsed < 0) throw new IllegalArgumentException("Negative powered interval");
        while (elapsed > credit) {
            elapsed -= credit;
            credit = 0;
            if (!purchase.getAsBoolean()) return false;
            credit = FUEL_INTERVAL;
        }
        credit -= elapsed;
        return true;
    }
    public double seconds(int coal) { return (coal * (double) FUEL_INTERVAL + credit) / SECOND; }
    public boolean warning(int coal) {
        double seconds = seconds(coal);
        if (seconds > 5) warned = false;
        if (seconds > 0 && seconds <= 5 && !warned) { warned = true; return true; }
        return false;
    }
}
