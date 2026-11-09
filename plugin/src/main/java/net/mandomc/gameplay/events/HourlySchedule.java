package net.mandomc.gameplay.events;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/** Minute crossings survive tick lag; startup never replays an old scheduled phase. */
public final class HourlySchedule {
    public enum Phase { END_WARNING, STOP, START_WARNING, START }
    private long lastMinute;
    private final int endWarning;
    private final int end;
    private final int startWarning;
    private final int start;
    public HourlySchedule(ZonedDateTime now, int endWarning, int end, int startWarning, int start) {
        for (int value : new int[]{endWarning, end, startWarning, start})
            if (value < 0 || value > 59) throw new IllegalArgumentException("events.yml: schedule minutes must be 0..59");
        this.endWarning = endWarning;this.end = end;this.startWarning = startWarning;this.start = start;
        lastMinute = now.toEpochSecond() / 60;
    }
    public List<Phase> poll(ZonedDateTime now) {
        long current = now.toEpochSecond() / 60;
        if (current <= lastMinute) return List.of();
        // A long outage must not replay multiple complete events.
        if (current - lastMinute > 60) lastMinute = current - 1;
        var phases = new ArrayList<Phase>();
        for (long minute = lastMinute + 1; minute <= current; minute++) {
            int local = java.time.Instant.ofEpochSecond(minute * 60).atZone(now.getZone()).getMinute();
            if (local == endWarning) phases.add(Phase.END_WARNING);
            if (local == end) phases.add(Phase.STOP);
            if (local == startWarning) phases.add(Phase.START_WARNING);
            if (local == start) phases.add(Phase.START);
        }
        lastMinute = current;
        return List.copyOf(phases);
    }
}
