package net.mandomc.review.ilum;

public final class ProgressTest {
    public static void main(String[] args) {
        int checks = 0;
        Progress alice = new Progress(0, 0), bob = new Progress(0, 0);
        if (!alice.advance(true).equals(alice)) throw new AssertionError("Closed gate bypass"); checks++;
        for (int panel = 0; panel < 3; panel++) alice = alice.answer(panel, true);
        if (!alice.ready(true) || bob.ready(true)) throw new AssertionError("Shared puzzle state"); checks++;
        if (!alice.answer(0, false).equals(alice)) throw new AssertionError("Wrong answer resets solved panels"); checks++;
        if (!alice.answer(0, true).equals(alice)) throw new AssertionError("Repeat click changes progress"); checks++;
        alice = alice.advance(true);
        if (alice.room() != 1 || alice.seals() != 0 || bob.room() != 0) throw new AssertionError("Independent checkpoint"); checks++;
        for (int room = 0; room < 30; room++) {
            Progress state = new Progress(room, 0);
            if (room % 5 % 2 == 0) {
                for (int mask = 0; mask < 7; mask++) {
                    if (new Progress(room, mask).advance(true).room() != room) throw new AssertionError("Incomplete seals pass");
                    checks++;
                }
                state = state.answer(0, true).answer(1, true).answer(2, true);
            }
            Progress saved = new Progress(state.room(), state.seals());
            if (!saved.equals(state)) throw new AssertionError("Restart state roundtrip"); checks++;
            if (state.advance(room % 5 % 2 == 0).room() != room + 1) throw new AssertionError("Route progression"); checks++;
        }
        if (new Progress(30, 0).advance(false).room() != 30) throw new AssertionError("Completion overflow"); checks++;
        System.out.println("ILUM RULES PASSED: " + checks + " assertions");
    }
}
