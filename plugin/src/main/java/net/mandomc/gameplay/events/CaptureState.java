package net.mandomc.gameplay.events;

import java.util.Set;

/** Capture belongs to one contestant until entirely cleared, even when enemies enter. */
public final class CaptureState {
    private String holder;
    private int ticks;
    public String holder() { return holder; }
    public int ticks() { return ticks; }
    public void reset() { holder = null; ticks = 0; }
    public String update(Set<String> present, int elapsedTicks, int requiredTicks) {
        if (holder != null && present.contains(holder)) ticks += elapsedTicks;
        else {
            reset();
            if (present.size() == 1) holder = present.iterator().next();
        }
        return holder != null && ticks >= requiredTicks ? holder : null;
    }
}
