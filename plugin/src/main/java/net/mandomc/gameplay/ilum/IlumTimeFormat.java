package net.mandomc.gameplay.ilum;

/** Display tenths only; persisted elapsed/best times remain in milliseconds. */
public final class IlumTimeFormat {
    private IlumTimeFormat() { }
    public static String format(long millis) {
        long tenths = Math.round(Math.max(0, millis) / 100.0);
        return String.format(java.util.Locale.ROOT, "%d:%02d.%d", tenths / 600, (tenths / 10) % 60, tenths % 10);
    }
}
