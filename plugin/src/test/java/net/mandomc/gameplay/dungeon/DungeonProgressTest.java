package net.mandomc.gameplay.dungeon;
import java.util.*;import org.junit.Test;import static org.junit.Assert.*;
public class DungeonProgressTest {
 @Test public void onlyLastOwnedKillAdvancesRoom(){var p=new DungeonProgress(2);p.start();var a=UUID.randomUUID();var b=UUID.randomUUID();p.spawned(List.of(a,b));assertFalse(p.died(UUID.randomUUID()));assertFalse(p.died(a));assertFalse(p.died(a));assertEquals(1,p.room());assertTrue(p.died(b));assertEquals(2,p.room());assertEquals(DungeonProgress.Phase.OPENING,p.phase());}
 @Test public void duplicateDeathsCannotSkipAnotherRoom(){var p=new DungeonProgress(3);p.start();var id=UUID.randomUUID();p.spawned(List.of(id));assertTrue(p.died(id));assertFalse(p.died(id));assertEquals(2,p.room());var next=UUID.randomUUID();p.spawned(List.of(next));assertFalse(p.died(id));assertEquals(DungeonProgress.Phase.COMBAT,p.phase());}
 @Test public void finalKillCompletesWithoutChestRequirements(){var p=new DungeonProgress(1);p.start();var id=UUID.randomUUID();p.spawned(List.of(id));p.died(id);assertEquals(DungeonProgress.Phase.COMPLETE,p.phase());assertEquals(1,p.room());p.reset();assertEquals(DungeonProgress.Phase.IDLE,p.phase());p.start();assertEquals(1,p.room());}
 @Test public void invalidSpawnsCannotSkipCombat(){var p=new DungeonProgress(1);assertThrows(IllegalStateException.class,()->p.spawned(List.of(UUID.randomUUID())));p.start();assertThrows(IllegalStateException.class,()->p.spawned(List.of()));var id=UUID.randomUUID();p.spawned(List.of(id));assertThrows(IllegalStateException.class,()->p.spawned(List.of(UUID.randomUUID())));}
}
