package net.mandomc.gameplay.clans;

import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClanStateTest {
    private final ClanState state=new ClanState();
    private ClanState.Clan clan(String name){return state.create(name,UUID.randomUUID(),1000);}
    @Test public void invitationsCannotExceedFourMembersOrDuplicateMembership() {
        var a=clan("Alpha");for(int i=0;i<3;i++)state.join(a,UUID.randomUUID(),4,1000);
        assertEquals(4,a.members.size());
        assertThrows(IllegalArgumentException.class,()->state.join(a,UUID.randomUUID(),4,1000));
        var b=clan("Bravo");assertThrows(IllegalArgumentException.class,()->state.join(b,a.leader,4,1000));
    }
    @Test public void namesAreBoundedUniqueAndCaseInsensitive() {
        clan("Alpha");assertThrows(IllegalArgumentException.class,()->clan("alpha"));
        assertThrows(IllegalArgumentException.class,()->clan("<red>bad"));
        assertThrows(IllegalArgumentException.class,()->clan("ab"));
    }
    @Test public void fourClaimsMaximumIncludingAcrossWorlds() {
        var a=clan("Alpha");for(int i=0;i<4;i++)state.claims.put(new ClanState.Chunk("world"+i,0,0),a.id);
        assertThrows(IllegalArgumentException.class,()->state.claim(a,new ClanState.Chunk("fifth",0,0),4,c->true));
    }
    @Test public void rivalClaimsNeedOneWholeChunkIncludingDiagonalsAndNegativeCoordinates() {
        var a=clan("Alpha");var b=clan("Bravo");
        state.claim(a,new ClanState.Chunk("world",-1,-1),4,c->true);
        for(int x=-2;x<=0;x++)for(int z=-2;z<=0;z++) {
            var c=new ClanState.Chunk("world",x,z);
            assertThrows(IllegalArgumentException.class,()->state.claim(b,c,4,ignored->true));
        }
        state.claim(b,new ClanState.Chunk("world",1,1),4,c->true);
    }
    @Test public void ownAdjacentClaimsAllowedButStaticProtectionBuffersApply() {
        var a=clan("Alpha");state.claim(a,new ClanState.Chunk("world",0,0),4,c->true);
        state.claim(a,new ClanState.Chunk("world",1,0),4,c->true);
        assertThrows(IllegalArgumentException.class,()->state.claim(a,new ClanState.Chunk("world",2,0),4,c->c.x()!=3));
    }
    @Test public void claimsRequireAnEdgeInTheSameWorldAfterTheFirst() {
        for(int[] step:List.of(new int[]{1,0},new int[]{-1,0},new int[]{0,1},new int[]{0,-1})) {
            var local=new ClanState();var a=local.create("Alpha",UUID.randomUUID(),0);
            local.claim(a,new ClanState.Chunk("world",-4,-7),4,c->true);
            local.claim(a,new ClanState.Chunk("world",-4+step[0],-7+step[1]),4,c->true);
            assertEquals(2,local.claims.size());
        }
        var a=clan("Alpha");state.claim(a,new ClanState.Chunk("world",0,0),4,c->true);
        for(var chunk:List.of(new ClanState.Chunk("world",1,1),new ClanState.Chunk("world",-1,-1),
                new ClanState.Chunk("world",2,0),new ClanState.Chunk("other",1,0)))
            assertThrows(IllegalArgumentException.class,()->state.claim(a,chunk,4,c->true));
        assertEquals(1,state.claims.size());
    }
    @Test public void cardinalConnectedClaimsStillStopAtFour() {
        var a=clan("Alpha");for(int x=0;x<4;x++)state.claim(a,new ClanState.Chunk("world",x,0),4,c->true);
        assertThrows(IllegalArgumentException.class,()->state.claim(a,new ClanState.Chunk("world",4,0),4,c->true));
    }
    @Test public void unclaimCannotRemoveABridgeButEdgesAndLastChunkAreAllowed() {
        var a=clan("Alpha");for(int x=0;x<3;x++)state.claim(a,new ClanState.Chunk("world",x,0),4,c->true);
        assertThrows(IllegalArgumentException.class,()->state.unclaim(a,new ClanState.Chunk("world",1,0)));
        assertEquals(3,state.claims.size());state.unclaim(a,new ClanState.Chunk("world",0,0));
        state.unclaim(a,new ClanState.Chunk("world",1,0));state.unclaim(a,new ClanState.Chunk("world",2,0));assertTrue(state.claims.isEmpty());
    }
    @Test public void diagonalRemaindersDoNotCountAsConnectedAndSquaresCanLoseACorner() {
        var a=clan("Alpha");for(var c:List.of(new ClanState.Chunk("world",0,0),new ClanState.Chunk("world",1,0),new ClanState.Chunk("world",1,1)))state.claim(a,c,4,ignored->true);
        assertThrows(IllegalArgumentException.class,()->state.unclaim(a,new ClanState.Chunk("world",1,0)));
        state.claim(a,new ClanState.Chunk("world",0,1),4,c->true);state.unclaim(a,new ClanState.Chunk("world",1,0));assertEquals(3,state.claims.size());
    }
    @Test public void grandfatheredIslandsMayShrinkOrGrowButCannotBeSplitFurther() {
        var a=clan("Alpha");for(int x:List.of(0,1,2,10))state.claims.put(new ClanState.Chunk("world",x,0),a.id);
        assertThrows(IllegalArgumentException.class,()->state.unclaim(a,new ClanState.Chunk("world",1,0)));
        state.unclaim(a,new ClanState.Chunk("world",10,0));state.claim(a,new ClanState.Chunk("world",3,0),4,c->true);
        assertEquals(4,state.claims.size());
    }
    @Test public void sequentialAcceptancesAndLegacyOverLimitClansCannotExceedFour() {
        var a=clan("Alpha");for(int i=0;i<2;i++)state.join(a,UUID.randomUUID(),4,0);
        state.join(a,UUID.randomUUID(),4,0);assertThrows(IllegalArgumentException.class,()->state.join(a,UUID.randomUUID(),4,0));
        a.members.add(UUID.randomUUID());var retained=Set.copyOf(a.members);
        assertThrows(IllegalArgumentException.class,()->state.join(a,UUID.randomUUID(),4,0));assertEquals(retained,a.members);
    }
    @Test public void pairwiseKillsAreAntisymmetricAndUnrelatedClansStayAtZero() {
        var a=clan("Alpha");var b=clan("Bravo");var c=clan("Charlie");
        state.kill(a.id,b.id,1000);state.kill(a.id,b.id,1001);state.kill(b.id,a.id,1002);
        assertEquals(1,state.score(a.id,b.id));assertEquals(-1,state.score(b.id,a.id));assertEquals(0,state.score(a.id,c.id));
        state.kill(a.id,a.id,1003);assertEquals(0,state.score(a.id,a.id));
    }
    private void unlock(ClanState.Clan a,ClanState.Clan b){for(int i=0;i<10;i++)state.kill(a.id,b.id,2000+i);}
    @Test public void firstBreachConsumesTenWarpointsAndFreezesOnlyThatPairForAnHour() {
        var a=clan("Alpha");var b=clan("Bravo");var c=clan("Charlie");
        for(int i=0;i<9;i++)state.kill(a.id,b.id,1000+i);
        assertFalse(state.canRaid(a.id,b.id,2000,10));state.kill(a.id,b.id,2000);
        assertTrue(state.canRaid(a.id,b.id,2000,10));assertFalse(state.canRaid(c.id,b.id,2000,10));
        assertTrue(state.breach(a.id,b.id,3000,3600000,10));
        assertEquals(0,state.score(a.id,b.id));assertTrue(state.canRaid(a.id,b.id,3001,10));
        assertFalse(state.canRaid(b.id,a.id,3001,10));
        for(int i=0;i<20;i++)state.kill(b.id,a.id,4000+i);
        assertEquals(0,state.score(a.id,b.id));state.kill(a.id,c.id,4000);assertEquals(1,state.score(a.id,c.id));
        assertFalse(state.breach(a.id,b.id,5000,3600000,10));
        assertTrue(state.canRaid(a.id,b.id,3602999,10));assertFalse(state.canRaid(a.id,b.id,3603000,10));
        state.kill(b.id,a.id,3603000);assertEquals(-1,state.score(a.id,b.id));
    }
    @Test public void aSecondRaidRequiresTenNewKills() {
        var a=clan("Alpha");var b=clan("Bravo");unlock(a,b);state.breach(a.id,b.id,3000,1000,10);
        assertFalse(state.breach(a.id,b.id,4001,1000,10));
        for(int i=0;i<10;i++)state.kill(a.id,b.id,5000+i);
        assertTrue(state.breach(a.id,b.id,6000,1000,10));
    }
    @Test public void abandonedClansCannotRecruitOrClaim() {
        var a=clan("Alpha");a.abandonedAt=2000;
        assertThrows(IllegalArgumentException.class,()->state.join(a,UUID.randomUUID(),5,3000));
        assertThrows(IllegalArgumentException.class,()->state.claim(a,new ClanState.Chunk("world",0,0),4,c->true));
    }
    @Test public void disbandRemovesClaimsAndOnlyItsOwnRivalries() {
        var a=clan("Alpha");var b=clan("Bravo");var c=clan("Charlie");
        state.claim(a,new ClanState.Chunk("world",0,0),4,ignored->true);
        state.kill(a.id,b.id,1000);state.kill(b.id,c.id,1000);state.remove(a);
        assertTrue(state.claims.isEmpty());assertNull(state.member(a.leader));assertEquals(1,state.rivalries.size());
        assertEquals(1,state.score(b.id,c.id));
    }
    @Test public void restartPreservesClaimsMembershipAbandonmentHomeAndRaidDeadline() throws Exception {
        var a=clan("Alpha");var b=clan("Bravo");unlock(a,b);state.breach(a.id,b.id,3000,3600000,10);
        state.claim(a,new ClanState.Chunk("minecraft:test",-2,4),4,c->true);
        a.home=new ClanState.Home("minecraft:test",-20.5,65,70.5,90,10);b.abandonedAt=12345;
        Path folder=Files.createTempDirectory("clan-state-test"),file=folder.resolve("clans.yml");
        try {
            Files.writeString(file,ClanStorage.encode(state));var loaded=ClanStorage.load(file);
            assertEquals(a.name,loaded.member(a.leader).name);assertEquals(a.home,loaded.clans.get(a.id).home);
            assertEquals(12345,loaded.clans.get(b.id).abandonedAt);assertEquals(state.claims,loaded.claims);
            assertTrue(loaded.canRaid(a.id,b.id,3500,10));assertEquals(0,loaded.score(a.id,b.id));
        }finally{Files.deleteIfExists(file);Files.deleteIfExists(folder);}
    }
}
