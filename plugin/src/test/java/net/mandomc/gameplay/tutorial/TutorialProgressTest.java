package net.mandomc.gameplay.tutorial;

import org.junit.Test;
import static org.junit.Assert.*;
import static net.mandomc.gameplay.tutorial.TutorialProgress.*;

public class TutorialProgressTest {
    @Test public void hubAllowsAnySectionButLaterActionsCannotSkipObjectives() {
        for (var section : Section.values()) {
            var progress = new TutorialProgress();
            assertEquals(Step.HUB, progress.step()); assertFalse(progress.advance(Step.HUB));
            assertTrue(progress.start(section)); assertEquals(section.first(), progress.step());
            assertFalse(progress.start(Section.SHOPS)); assertFalse(progress.advance(Step.COMPLETE));
            progress.hub(); assertTrue(progress.start(Section.SHOPS));
        }
    }
    @Test public void threeDifferentTargetsMustDieBeforeRecipesAndSaber() {
        var progress = new TutorialProgress(); progress.start(Section.COMBAT);
        assertFalse(progress.kill(3)); assertFalse(progress.kill(3)); assertTrue(progress.kill(3));
        assertEquals(Step.RECIPES, progress.step()); assertFalse(progress.kill(3));
        assertTrue(progress.advance(Step.RECIPES)); assertEquals(Step.SABER, progress.step());
        assertTrue(progress.advance(Step.SABER)); assertEquals(Step.COMPLETE, progress.step());
    }
    @Test public void eachSectionEndsWithoutStartingAnUnselectedLesson() {
        for (var section : Section.values()) {
            var progress = new TutorialProgress(); progress.start(section);
            for (int i = 0; i < 20 && progress.step() != Step.COMPLETE; i++) assertTrue(progress.advance(progress.step()));
            assertEquals(Step.COMPLETE, progress.step()); assertEquals(section, progress.section());
            assertFalse(progress.advance(Step.COMPLETE));
        }
    }
    @Test public void airborneTimeRequiresPilotingAndDoesNotCountServerStalls() {
        var p = new TutorialProgress(); p.start(Section.VEHICLES);
        assertFalse(p.fly(true, true, 250, 100)); p.advance(Step.PLACE); p.advance(Step.BOARD);
        assertFalse(p.fly(false, true, 200, 1000)); assertFalse(p.fly(true, false, 200, 1000));
        assertEquals(0, p.flightMillis()); assertFalse(p.fly(true, true, 60000, 1000));
        assertEquals(250, p.flightMillis());
        assertFalse(p.fly(true, true, 250, 1000)); assertFalse(p.fly(true, true, 250, 1000));
        assertTrue(p.fly(true, true, 250, 1000)); assertEquals(Step.RIGHT, p.step());
    }
    @Test public void clanNamesCannotInjectCommands() {
        assertTrue(TutorialProgress.clanName("Rebel_12")); assertFalse(TutorialProgress.clanName("ab"));
        assertFalse(TutorialProgress.clanName("rebel;op")); assertFalse(TutorialProgress.clanName("<red>Clan"));
    }
}
