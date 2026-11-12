package net.mandomc.gameplay.trophies;

import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static net.mandomc.gameplay.trophies.TrophyJournal.*;

public class TrophyJournalTest {
    private Entry entry(){return new Entry(UUID.randomUUID(),UUID.randomUUID(),new Cell("minecraft:devworld",1,2,3),90,"YWJj",Phase.PREPARED);}
    @Test public void completeTransfersSurviveReloadAndKeepExactMetadata()throws Exception {
        var file=Files.createTempDirectory("trophy").resolve("data.yml");var journal=new TrophyJournal(file,20);var entry=entry();journal.reserve(entry);
        assertEquals(entry,new TrophyJournal(file,20).entries().get(entry.id()));var placed=entry.placed();journal.replace(entry,placed);
        assertEquals(placed,new TrophyJournal(file,20).at(entry.cell()));var recipient=UUID.randomUUID();var returned=placed.returning(recipient);journal.replace(placed,returned);
        var restored=new TrophyJournal(file,20);assertNull(restored.at(entry.cell()));assertEquals(recipient,restored.entries().get(entry.id()).owner());assertEquals(entry.item(),returned.item());
        restored.remove(returned);assertTrue(new TrophyJournal(file,20).entries().isEmpty());
    }
    @Test public void duplicateCellsAndReplayedTransfersCannotProduceExtraItems()throws Exception {
        var journal=new TrophyJournal(Files.createTempDirectory("trophy").resolve("data.yml"),20);var first=entry();journal.reserve(first);
        var other=new Entry(UUID.randomUUID(),UUID.randomUUID(),first.cell(),0,"YWJj",Phase.PREPARED);
        assertThrows(IllegalStateException.class,()->journal.reserve(other));journal.replace(first,first.placed());
        assertThrows(IllegalStateException.class,()->journal.replace(first,first.returning(first.owner())));
        assertThrows(IllegalStateException.class,()->journal.remove(first.placed()));assertEquals(1,journal.entries().size());
    }
    @Test public void failedDiskSaveCannotPublishTransfer()throws Exception {
        var file=Files.createTempDirectory("trophy").resolve("data.yml");var journal=new TrophyJournal(file,20);var entry=entry();journal.reserve(entry);
        Files.delete(file);Files.createDirectory(file);Files.writeString(file.resolve("occupied"),"keep");
        assertThrows(Exception.class,()->journal.replace(entry,entry.placed()));assertEquals(entry,journal.at(entry.cell()));
    }
    @Test public void abortBeforeConsumptionAndReturnAfterConsumptionAreDifferentDurableStates()throws Exception {
        var file=Files.createTempDirectory("trophy").resolve("data.yml");var journal=new TrophyJournal(file,20);var entry=entry();journal.reserve(entry);journal.remove(entry);assertTrue(journal.entries().isEmpty());
        journal.reserve(entry);journal.replace(entry,entry.returning(entry.owner()));assertEquals(Phase.RETURNING,new TrophyJournal(file,20).entries().get(entry.id()).phase());
        assertNotEquals(entry.placeReceipt(),entry.returnReceipt());
    }
    @Test public void activeLimitAndInvalidOwnershipTransitionsFailClosed()throws Exception {
        var journal=new TrophyJournal(Files.createTempDirectory("trophy").resolve("data.yml"),1);var e=entry();journal.reserve(e);
        assertThrows(IllegalStateException.class,()->journal.reserve(new Entry(UUID.randomUUID(),e.owner(),new Cell("minecraft:devworld",9,2,3),0,"YWJj",Phase.PREPARED)));
        assertThrows(IllegalStateException.class,()->journal.replace(e,new Entry(e.id(),UUID.randomUUID(),e.cell(),e.yaw(),e.item(),Phase.PLACED)));
        assertThrows(IllegalArgumentException.class,()->new Entry(e.id(),e.owner(),e.cell(),45,e.item(),Phase.PREPARED));
    }
    @Test public void legacyFloorJournalKeepsYawAndMigratesWithoutChangingCustody()throws Exception {
        var file=Files.createTempDirectory("trophy").resolve("data.yml");var e=entry();var y=new org.bukkit.configuration.file.YamlConfiguration();y.set("schema-version",1);String p="entries."+e.id()+".";y.set(p+"owner",e.owner().toString());y.set(p+"world",e.cell().world());y.set(p+"x",e.cell().x());y.set(p+"y",e.cell().y());y.set(p+"z",e.cell().z());y.set(p+"yaw",e.yaw());y.set(p+"item",e.item());y.set(p+"phase","PLACED");Files.writeString(file,y.saveToString());
        var journal=new TrophyJournal(file,20);var restored=journal.at(e.cell());assertEquals(Face.UP,restored.face());assertEquals(e.yaw(),restored.yaw());assertEquals(e.item(),restored.item());journal.replace(restored,restored.returning(e.owner()));assertEquals(Face.UP,new TrophyJournal(file,20).entries().get(e.id()).face());assertEquals(2,org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file.toFile()).getInt("schema-version"));
    }
    @Test public void allSixFacesPersistAndCannotChangeDuringCustodyTransfer()throws Exception {
        for(var face:Face.values()){
            var file=Files.createTempDirectory("trophy-face").resolve("data.yml");var journal=new TrophyJournal(file,20);var old=entry();var e=new Entry(old.id(),old.owner(),old.cell(),old.yaw(),face,old.item(),old.phase());journal.reserve(e);journal.replace(e,e.placed());var restored=new TrophyJournal(file,20).at(e.cell());assertEquals(face,restored.face());assertEquals(face,restored.returning(e.owner()).face());
            var wrong=new Entry(e.id(),e.owner(),e.cell(),e.yaw(),face==Face.UP?Face.DOWN:Face.UP,e.item(),Phase.RETURNING);assertThrows(IllegalStateException.class,()->journal.replace(restored,wrong));
        }
    }
}
