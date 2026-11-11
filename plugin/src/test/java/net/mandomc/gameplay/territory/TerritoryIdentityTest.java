package net.mandomc.gameplay.territory;

import java.util.UUID;
import net.mandomc.gameplay.clans.ClanState;
import org.junit.Test;
import static org.junit.Assert.*;

public class TerritoryIdentityTest {
    @Test public void claimsUseViewerRelationAndOnlyTheActualRaidedDefendersTurnRed() {
        var state = new ClanState();
        var attacker = state.create("Attackers",UUID.randomUUID(),0);
        var defender = state.create("Defenders",UUID.randomUUID(),0);
        var third = state.create("ThirdClan",UUID.randomUUID(),0);
        var wild = TerritoryPolicy.Zone.WILDERNESS;
        assertEquals(TerritoryIdentity.Relation.OWN,TerritoryIdentity.read(wild,defender,defender,state,100).relation());
        assertEquals(TerritoryIdentity.Relation.OTHER,TerritoryIdentity.read(wild,defender,attacker,state,100).relation());
        for(int i=0;i<10;i++)state.kill(attacker.id,defender.id,100);
        assertEquals(TerritoryIdentity.Relation.OTHER,TerritoryIdentity.read(wild,defender,attacker,state,100).relation());
        assertTrue(state.breach(attacker.id,defender.id,100,1000,10));
        assertEquals(TerritoryIdentity.Relation.RAID,TerritoryIdentity.read(wild,defender,attacker,state,101).relation());
        assertEquals(TerritoryIdentity.Relation.OTHER,TerritoryIdentity.read(wild,defender,third,state,101).relation());
        assertEquals(TerritoryIdentity.Relation.OTHER,TerritoryIdentity.read(wild,attacker,defender,state,101).relation());
        assertEquals(TerritoryIdentity.Relation.OWN,TerritoryIdentity.read(wild,defender,defender,state,101).relation());
        assertEquals(TerritoryIdentity.Relation.OTHER,TerritoryIdentity.read(wild,defender,attacker,state,1100).relation());
        assertEquals(TerritoryIdentity.Relation.OTHER,TerritoryIdentity.read(wild,defender,null,state,101).relation());
    }
    @Test public void identityIgnoresChunkButChangesForOwnerMembershipAndStaticZones() {
        var state = new ClanState();var a=state.create("First",UUID.randomUUID(),0);var b=state.create("Second",UUID.randomUUID(),0);
        var wild=TerritoryPolicy.Zone.WILDERNESS;
        assertEquals(TerritoryIdentity.read(wild,a,null,state,0),TerritoryIdentity.read(wild,a,null,state,20));
        assertNotEquals(TerritoryIdentity.read(wild,a,null,state,0),TerritoryIdentity.read(wild,b,null,state,0));
        assertNotEquals(TerritoryIdentity.read(wild,a,null,state,0),TerritoryIdentity.read(wild,a,a,state,0));
        for(var zone:java.util.List.of(TerritoryPolicy.Zone.SAFEZONE,TerritoryPolicy.Zone.WARZONE)) {
            var identity=TerritoryIdentity.read(zone,a,a,state,0);assertNull(identity.owner());assertEquals(TerritoryIdentity.Relation.NONE,identity.relation());
        }
    }
}
