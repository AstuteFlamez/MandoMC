package net.mandomc.review.ilum;

/** Immutable, per-player state: no shared door, timer, inventory or puzzle reset. */
public record Progress(int room, int seals) {
    public Progress {
        if (room < 0 || room > 30 || seals < 0 || seals > 7) {
            throw new IllegalArgumentException("Invalid Ilum progress");
        }
    }

    public Progress answer(int panel, boolean correct) {
        if (panel < 0 || panel > 2) throw new IllegalArgumentException("Invalid panel");
        return correct ? new Progress(room, seals | (1 << panel)) : this;
    }

    public boolean ready(boolean puzzle) { return !puzzle || seals == 7; }
    public Progress advance(boolean puzzle) {
        if (room == 30 || !ready(puzzle)) return this;
        return new Progress(room + 1, 0);
    }
}
