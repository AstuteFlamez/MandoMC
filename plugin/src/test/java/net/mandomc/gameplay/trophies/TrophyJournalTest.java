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
}
