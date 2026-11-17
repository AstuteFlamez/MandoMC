package net.mandomc.gameplay.guide;
import org.junit.Test;
import static org.junit.Assert.*;
public class GroqBrainTest {
    @Test public void scopesKeepGeneralKnowledgeSeparateFromServerFacts() {
        try(var brain=new GroqBrain("","test","You are M4-ND.")) {
            var notes=java.util.List.of(new GuideKnowledge.Note("test","","SECRET_TEST_SERVER_FACT","fallback"));
            assertTrue(brain.systemPrompt(GuideConversation.Topic.SERVER,notes).contains("SECRET_TEST_SERVER_FACT"));
            assertFalse(brain.systemPrompt(GuideConversation.Topic.LORE,notes).contains("SECRET_TEST_SERVER_FACT"));
            assertFalse(brain.systemPrompt(GuideConversation.Topic.GENERAL,notes).contains("SECRET_TEST_SERVER_FACT"));
            assertTrue(brain.systemPrompt(GuideConversation.Topic.LORE,notes).contains("canon from Legends"));
            assertTrue(brain.systemPrompt(GuideConversation.Topic.SERVER,notes).contains("ONLY from verified"));
        }
    }
    @Test public void providerResetIntervalsRespectSecondsAndMixedUnits(){
        assertEquals(86400,GroqBrain.resetDelay("1m26.4s"));
        assertEquals(3390,GroqBrain.resetDelay("3.39s"));
        assertEquals(60000,GroqBrain.resetDelay("unknown"));
    }
    @Test public void repliesDoNotExposeFormattingTags(){
        assertEquals("Hold a wrench.\nKeep crouching.",GroqBrain.plainAnswer("<aqua>Hold a wrench.<newline><gray>Keep crouching."));
        String plain = GroqBrain.plainAnswer("**Three** homes. `mandomc.travel.homes.unlimited` bypasses homes-limit.");
        assertFalse(plain.contains("mandomc."));assertFalse(plain.contains("homes-limit"));
        assertEquals("One. Two. Three. Four.",GroqBrain.plainAnswer("One. Two. Three. Four. Five."));
        assertEquals("Look in desert or badlands.",GroqBrain.plainAnswer("Look in minecraft:desert or minecraft:badlands."));
        assertFalse(plain.contains("**"));assertFalse(plain.contains("`"));assertTrue(plain.contains("unlimited"));
    }
}
