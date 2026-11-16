package net.mandomc.gameplay.tutorial;

/** Only observed, successful actions advance the ordered lesson. */
public final class TutorialProgress {
    public enum Step { WARP, MAP, WILDERNESS, CREATE, CLAIM, DISBAND, COMBAT, SABER, BUY, SELL, RECIPES, PLACE, BOARD, FLIGHT, RIGHT, LEFT, STOP, TARGET, LAND, EVENTS, COMPLETE }
    private Step step = Step.WARP;
    private int kills;
    private long flightMillis;
    public Step step() { return step; }
    public int kills() { return kills; }
    public long flightMillis() { return flightMillis; }
    public boolean advance(Step expected) {
        if (step != expected || step == Step.COMPLETE) return false;
        step = Step.values()[step.ordinal() + 1];
        return true;
    }
    public boolean kill(int required) {
        return step == Step.COMBAT && ++kills >= required && advance(Step.COMBAT);
    }
    public boolean fly(boolean piloting, boolean airborne, long elapsed, long required) {
        if (step != Step.FLIGHT || !piloting || !airborne) return false;
        flightMillis += Math.max(0, Math.min(250, elapsed));
        return flightMillis >= required && advance(Step.FLIGHT);
    }
    public static boolean clanName(String name) { return name.matches("[A-Za-z0-9_]{3,16}"); }
}
