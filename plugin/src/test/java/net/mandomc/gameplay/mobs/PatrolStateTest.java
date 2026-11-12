package net.mandomc.gameplay.mobs;

import java.util.Set;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class PatrolStateTest {
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
    private final UUID raider = UUID.randomUUID(), massiff = UUID.randomUUID(), bantha = UUID.randomUUID();
    private final UUID other = UUID.randomUUID();

    @Test public void allMembersShareOneReservationAndIndependentPlayersCanHaveTheirOwnPatrol() {
        var state = new PatrolState();
        state.update("first", Set.of(raider, massiff, bantha), Set.of(raider, massiff));
        state.update("second", Set.of(other), Set.of(other));
        assertTrue(state.claim(a, "first", 0));
        assertFalse(state.claim(a, "second", 0));
        assertTrue(state.claim(b, "second", 0));
        for (UUID member : Set.of(raider, massiff, bantha)) {
            assertEquals("first", state.memberGroup(member));
            assertFalse(state.claimMember(b, member, 1));
        }
        assertFalse(state.claimMember(a, other, 1));
        assertTrue(state.claimMember(c, massiff, 1));
        assertFalse(state.claimMember(c, other, 1));
    }

    @Test public void patrolEndsOnLastHostileRatherThanFirstDeathOrRemainingPassiveBantha() {
        var state = new PatrolState();
        state.update("first", Set.of(raider, massiff, bantha), Set.of(raider, massiff));
        state.claim(a, "first", 0);
        state.memberGone(raider);
        assertEquals("first", state.playerGroup(a));
        state.memberGone(massiff);
        assertNull(state.playerGroup(a));
        assertNull(state.memberGroup(bantha));
        assertTrue(state.groups().isEmpty());
    }

    @Test public void playerLifecycleReleasesOnlyThatPlayerAndDoesNotDespawnOrStealOtherEncounters() {
        var state = new PatrolState();
        state.update("first", Set.of(raider), Set.of(raider));
        state.update("second", Set.of(other), Set.of(other));
        state.claim(a, "first", 0); state.claim(b, "first", 0); state.claim(c, "second", 0);
        state.release(a);
        assertNull(state.playerGroup(a));
        assertEquals("first", state.playerGroup(b));
        assertEquals("second", state.playerGroup(c));
        assertEquals(2, state.groups().size());
        state.retainPlayers(Set.of(c));
        assertNull(state.playerGroup(b));
        assertEquals("second", state.playerGroup(c));
    }

    @Test public void observedEngagementKeepsReservationAndGenuineDisengagementExpiresWithoutDeletingTheGroup() {
        var state = new PatrolState();
        state.update("first", Set.of(raider), Set.of(raider));
        state.claim(a, "first", 0); state.touch(a, 20_000);
        state.expire(30_000, 30_000); assertEquals("first", state.playerGroup(a));
        state.expire(50_000, 30_000); assertNull(state.playerGroup(a));
        assertEquals(Set.of("first"), state.groups());
    }

    @Test public void despawnUnloadAndRestoredMembershipDoNotLeaveStaleLocks() {
        var state = new PatrolState();
        state.track("first", raider, true); state.track("first", bantha, false);
        state.claim(a, "first", 0);
        state.retainGroups(Set.of());
        assertNull(state.playerGroup(a)); assertNull(state.memberGroup(raider));
        state.update("first", Set.of(raider, massiff), Set.of(raider, massiff));
        assertTrue(state.claim(a, "first", 1));
        state.update("first", Set.of(massiff), Set.of(massiff));
        assertNull(state.memberGroup(raider)); assertEquals("first", state.playerGroup(a));
    }
}
