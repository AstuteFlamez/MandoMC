package net.mandomc.gameplay.tutorial;

import org.junit.Test;
import static org.junit.Assert.*;
import static net.mandomc.gameplay.tutorial.TutorialProgress.Step;

public class TutorialProgressTest {
    @Test public void laterActionsCannotSkipObjectives() {
        var progress = new TutorialProgress();
        assertFalse(progress.advance(Step.CLAIM)); assertFalse(progress.kill(3));
        assertFalse(progress.fly(true, true, 10_000, 10_000)); assertEquals(Step.WARP, progress.step());
        assertTrue(progress.advance(Step.WARP)); assertFalse(progress.advance(Step.WARP)); assertEquals(Step.MAP, progress.step());
    }
    @Test public void allThreeGuardsAreRequiredAfterDisband() {
        var progress = new TutorialProgress();
        for (var step : new Step[]{Step.WARP, Step.MAP, Step.WILDERNESS, Step.CREATE, Step.CLAIM}) assertTrue(progress.advance(step));
        assertFalse(progress.kill(3)); assertEquals(Step.DISBAND, progress.step());
        progress.advance(Step.DISBAND); assertFalse(progress.kill(3)); assertFalse(progress.kill(3));
        assertTrue(progress.kill(3)); assertEquals(Step.SABER, progress.step()); assertFalse(progress.kill(3));
    }
    @Test public void placementMustCompleteBeforeFlightCanCount() {
        var progress = new TutorialProgress(); while (progress.step() != Step.PLACE) progress.advance(progress.step());
        assertFalse(progress.fly(true, true, 250, 100)); assertEquals(0, progress.flightMillis());
        assertFalse(progress.advance(Step.FLIGHT)); assertTrue(progress.advance(Step.PLACE));
        assertEquals(Step.FLIGHT, progress.step());
    }
    @Test public void onlyAirbornePilotingCountsAndServerStallsAreBounded() {
        var progress = new TutorialProgress(); while (progress.step() != Step.FLIGHT) progress.advance(progress.step());
        assertFalse(progress.fly(false, true, 200, 10_000)); assertFalse(progress.fly(true, false, 200, 10_000));
        assertEquals(0, progress.flightMillis());
        assertFalse(progress.fly(true, true, 60_000, 10_000)); assertEquals(250, progress.flightMillis());
        for (int i = 0; i < 38; i++) assertFalse(progress.fly(true, true, 250, 10_000));
        assertTrue(progress.fly(true, true, 250, 10_000)); assertEquals(Step.TARGET, progress.step());
    }
    @Test public void finalStepIsTerminalAndNamesCannotInjectCommands() {
        var progress = new TutorialProgress(); while (progress.step() != Step.COMPLETE) progress.advance(progress.step());
        assertFalse(progress.advance(Step.COMPLETE));
        assertTrue(TutorialProgress.clanName("Rebel_12")); assertFalse(TutorialProgress.clanName("ab"));
        assertFalse(TutorialProgress.clanName("rebel;op")); assertFalse(TutorialProgress.clanName("<red>Clan"));
    }
}
