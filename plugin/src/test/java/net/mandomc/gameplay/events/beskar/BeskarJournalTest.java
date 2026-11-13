package net.mandomc.gameplay.events.beskar;

import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import net.mandomc.gameplay.events.beskar.MineSurvey.Pos;

public class BeskarJournalTest {
    private List<BeskarJournal.Node> nodes(){
        var result=new ArrayList<BeskarJournal.Node>();
        for(int i=0;i<25;i++)result.add(new BeskarJournal.Node(new Pos(i,12,0),"minecraft:deepslate[axis="+(i%2==0?"x":"z")+"]",i%3==0));
        return result;
    }
    @Test public void exactOrientedHostsAndConsumedStateSurviveAtomicRoundTrip()throws Exception {
        var dir=Files.createTempDirectory("beskar-journal-test");var p=dir.resolve("receipt.yml");
        try {
            var journal=new BeskarJournal(p);var state=new BeskarJournal.State("minecraft:mandalore_review",UUID.randomUUID().toString(),nodes());
            journal.save(state);assertEquals(state,journal.load());assertFalse(Files.exists(dir.resolve("receipt.yml.tmp")));
            var changed=new ArrayList<>(state.nodes());changed.set(1,changed.get(1).mined(true));journal.save(new BeskarJournal.State(state.world(),state.generation(),changed));
            assertTrue(journal.load().nodes().get(1).mined());assertEquals("minecraft:deepslate[axis=z]",journal.load().nodes().get(1).original());
        }finally{Files.deleteIfExists(p);Files.deleteIfExists(dir);}
    }
    @Test public void incompleteOrDuplicateRecoveryPlansAreRejected()throws Exception {
        var dir=Files.createTempDirectory("beskar-invalid-test");var p=dir.resolve("receipt.yml");var journal=new BeskarJournal(p);
        try {
            journal.save(new BeskarJournal.State("minecraft:mandalore_review","generation",nodes().subList(0,24)));
            assertThrows(IllegalArgumentException.class,journal::load);
            var copy=nodes();copy.set(1,copy.get(0));journal.save(new BeskarJournal.State("minecraft:mandalore_review","generation",copy));
            assertThrows(IllegalArgumentException.class,journal::load);
        }finally{Files.deleteIfExists(p);Files.deleteIfExists(dir);}
    }
    @Test public void independentGenerationReceiptsDoNotOverwriteCrashRecoveryHistory()throws Exception {
        var dir=Files.createTempDirectory("beskar-history-test");var first=dir.resolve("first.yml");var second=dir.resolve("second.yml");
        try {
            var old=new BeskarJournal.State("minecraft:mandalore_review","old",nodes());var next=new BeskarJournal.State(old.world(),"new",nodes());
            new BeskarJournal(first).save(old);new BeskarJournal(second).save(next);
            assertEquals(old,new BeskarJournal(first).load());assertEquals(next,new BeskarJournal(second).load());
        }finally{Files.deleteIfExists(first);Files.deleteIfExists(second);Files.deleteIfExists(dir);}
    }
}
