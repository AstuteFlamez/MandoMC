package net.mandomc.gameplay.events;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.mandomc.gameplay.travel.WarpService;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class EventRulesTest {
    @Test public void eventTimeUsesReadableUnitsAtMinuteAndHourBoundaries() {
        assertEquals("0 Seconds",EventManager.formatDuration(0));
        assertEquals("0 Seconds",EventManager.formatDuration(-1));
        assertEquals("1 Second",EventManager.formatDuration(1));
        assertEquals("59 Seconds",EventManager.formatDuration(59));
        assertEquals("1 Min",EventManager.formatDuration(60));
        assertEquals("1 Min 1 Second",EventManager.formatDuration(61));
        assertEquals("59 Mins 59 Seconds",EventManager.formatDuration(3599));
        assertEquals("1 Hour",EventManager.formatDuration(3600));
        assertEquals("1 Hour 1 Min 1 Second",EventManager.formatDuration(3661));
        assertEquals("2 Hours 5 Mins 10 Seconds",EventManager.formatDuration(7510));
    }
    @Test public void enemiesCannotResetTheHolder() {
        var c=new CaptureState();c.update(Set.of("a"),10,3600);
        for(int i=0;i<359;i++) assertNull(c.update(Set.of("a","b"),10,3600));
        assertEquals("a",c.holder());assertEquals(3590,c.ticks());
        assertEquals("a",c.update(Set.of("a","b"),10,3600));
    }
    @Test public void removingTheHolderStartsANewFullCapture() {
        var c=new CaptureState();c.update(Set.of("a"),10,100);c.update(Set.of("a"),50,100);
        c.update(Set.of("b"),10,100);assertEquals("b",c.holder());assertEquals(0,c.ticks());
        assertNull(c.update(Set.of("b"),90,100));assertEquals("b",c.update(Set.of("b"),10,100));
    }
    @Test public void clearingTheHillResetsControlAndProgress() {
        var c=new CaptureState();c.update(Set.of("a"),10,100);c.update(Set.of("a"),50,100);
        c.update(Set.of(),10,100);assertNull(c.holder());assertEquals(0,c.ticks());
        c.update(Set.of("a","b"),10,100);assertNull(c.holder());
        c.update(Set.of("a"),10,100);assertEquals(0,c.ticks());
    }
    @Test public void countdownDoesNotCreditTimeBeforeTheFirstSample() {
        var c=new CaptureState();assertNull(c.update(Set.of("a"),10,10));
        assertEquals(0,c.ticks());assertEquals("a",c.update(Set.of("a"),10,10));
    }
    private HourlySchedule schedule(ZonedDateTime now) {return new HourlySchedule(now,50,55,55,0);}
    @Test public void hourlyScheduleHandlesLagAndDoesNotRepeatAMinute() {
        var t=ZonedDateTime.parse("2026-11-08T12:49:59-06:00[America/Chicago]");var s=schedule(t);
        assertEquals(List.of(HourlySchedule.Phase.END_WARNING),s.poll(t.plusSeconds(4)));
        assertTrue(s.poll(t.plusSeconds(5)).isEmpty());
        assertEquals(List.of(HourlySchedule.Phase.STOP,HourlySchedule.Phase.START_WARNING),s.poll(t.plusMinutes(6)));
        assertEquals(List.of(HourlySchedule.Phase.START),s.poll(t.plusMinutes(11)));
    }
    @Test public void scheduleStartupAndLongOutagesDoNotReplayEvents() {
        var t=ZonedDateTime.parse("2026-11-08T12:00:20-06:00[America/Chicago]");var s=schedule(t);
        assertTrue(s.poll(t.plusSeconds(20)).isEmpty());
        assertTrue(s.poll(t.plusDays(2).plusMinutes(20)).isEmpty());
        assertTrue(s.poll(t.minusMinutes(1)).isEmpty());
    }
    @Test public void repeatedDstHourUsesDistinctMinuteCrossings() {
        var t=ZonedDateTime.parse("2026-11-01T01:59:50-05:00[America/Chicago]");var s=schedule(t);
        assertEquals(List.of(HourlySchedule.Phase.START),s.poll(t.plusSeconds(20)));
        assertTrue(s.poll(t.plusSeconds(25)).isEmpty());
    }
    @Test public void invalidScheduleMinuteIsRejected() {
        assertThrows(IllegalArgumentException.class,()->new HourlySchedule(ZonedDateTime.now(),60,55,55,0));
    }
    @Test public void singleEventCooldownFallsBackToTheEnabledEvent() {
        var rotation=new EventRotation();var e=new EventRotation.Entry("koth",30,1,true);
        assertEquals("koth",rotation.choose(List.of(e),new Random(1)));
        rotation.started(e);assertEquals("koth",rotation.choose(List.of(e),new Random(1)));
    }
    @Test public void rotationExcludesDisabledAndZeroWeightDefinitions() {
        var rotation=new EventRotation();
        assertNull(rotation.choose(List.of(new EventRotation.Entry("koth",30,0,false),
                new EventRotation.Entry("unused",0,0,true)),new Random(1)));
    }
    @Test public void rotationUsesOtherEligibleEventBeforeRepeating() {
        var rotation=new EventRotation();var a=new EventRotation.Entry("a",30,1,true);
        var b=new EventRotation.Entry("b",1,0,true);rotation.started(a);
        assertEquals("b",rotation.choose(List.of(a,b),new Random(1)));
    }
    @Test public void invalidRotationValuesFailClearly() {
        assertThrows(IllegalArgumentException.class,()->new EventRotation.Entry("koth",-1,1,true));
        assertThrows(IllegalArgumentException.class,()->new EventRotation.Entry("koth",30,-1,true));
    }
    private YamlConfiguration koth() {
        return YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(
                getClass().getResourceAsStream("/events/koth.yml"),java.nio.charset.StandardCharsets.UTF_8));
    }
    @Test public void recoveredHillUsesAcceptedTimingAndBoundedCylinder() {
        var s=KothSettings.read(koth());assertEquals("minecraft:hoth",s.world());assertEquals(3600,s.captureTicks());
        assertTrue(s.inside(-148,27,141));assertTrue(s.inside(-140,31,141));
        assertFalse(s.inside(-139.99,27,141));assertFalse(s.inside(-148,31.01,141));
        assertFalse(s.inside(-140,27,149));
    }
    @Test public void malformedOrUnboundedSettingsAreRejected() {
        var c=koth();c.set("settings.radius",Double.NaN);
        var invalid=c;assertThrows(IllegalArgumentException.class,()->KothSettings.read(invalid));
        c=koth();c.set("settings.capture-seconds",0);var zero=c;
        assertThrows(IllegalArgumentException.class,()->KothSettings.read(zero));
        c=koth();c.set("settings.capture-seconds",.5);var fraction=c;
        assertThrows(IllegalArgumentException.class,()->KothSettings.read(fraction));
    }
    @Test public void onlyCurrentWorldsAndTheirLegacyAliasesResolve() {
        assertEquals(List.of("spawn","hoth","tatooine"),WarpService.ids());
        assertEquals("spawn",WarpService.canonical("BlackSpireOutpost"));
        assertEquals("spawn",WarpService.canonical("Batuu"));
        assertEquals("spawn",WarpService.canonical("SpawnWorld"));
        assertNull(WarpService.canonical("SurvivalWorld"));
        assertNull(WarpService.canonical("Earth"));assertEquals("hoth",WarpService.canonical("HOTH"));
        assertEquals("tatooine",WarpService.canonical("TATOOINE"));
        for(String excluded:List.of("alderaan","ilum","endor","jabba","devworld","survival"))
            assertNull(WarpService.canonical(excluded));
    }
    @Test public void rewardsAreExplicitlyDisabled() {
        assertFalse(koth().getBoolean("rewards.enabled",true));
        assertTrue(koth().getStringList("settings.reward-commands").isEmpty());
    }
}
