package net.mandomc.gameplay.tutorial;

/** Independent sections; only successful observed actions advance their objectives. */
public final class TutorialProgress {
    public enum Step { HUB, WARP, MINING, SMELTING, CRAFTING, MAP, WILDERNESS, CREATE, CLAIM, DISBAND,
        COMBAT, RECIPES, SABER, BUY, SELL, PLACE, BOARD, FLIGHT, RIGHT, LEFT, STOP, TARGET, LAND, EVENTS, COMPLETE }
    public enum Section {
        SURVIVAL(Step.WARP), CLANS(Step.MAP), COMBAT(Step.COMBAT), SHOPS(Step.BUY), VEHICLES(Step.PLACE);
        private final Step first;
        Section(Step first) { this.first = first; }
        public Step first() { return first; }
    }
    private Step step = Step.HUB;
    private Section section;
    private int kills;
    private long flightMillis;
    public Step step() { return step; }
    public Section section() { return section; }
    public int kills() { return kills; }
    public long flightMillis() { return flightMillis; }
    public void hub() { step = Step.HUB; section = null; kills = 0; flightMillis = 0; }
    public boolean start(Section selected) {
        if (step != Step.HUB) return false;
        section = selected; step = selected.first(); kills = 0; flightMillis = 0; return true;
    }
    public boolean advance(Step expected) {
        if (step != expected || step == Step.COMPLETE || step == Step.HUB) return false;
        step = switch (step) {
            case CRAFTING, DISBAND, SABER, SELL, LAND -> Step.COMPLETE;
            default -> Step.values()[step.ordinal() + 1];
        };
        return true;
    }
    public boolean kill(int required) { return step == Step.COMBAT && ++kills >= required && advance(Step.COMBAT); }
    public boolean fly(boolean piloting, boolean airborne, long elapsed, long required) {
        if (step != Step.FLIGHT || !piloting || !airborne) return false;
        flightMillis += Math.max(0, Math.min(250, elapsed));
        return flightMillis >= required && advance(Step.FLIGHT);
    }
    public static boolean clanName(String name) { return name.matches("[A-Za-z0-9_]{3,16}"); }
}
