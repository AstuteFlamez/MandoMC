package net.mandomc.gameplay.guide;
import org.junit.Test;
import static org.junit.Assert.*;
public class GroqBrainTest {
    @Test public void providerSchemaConstrainsScopeAndDiagnosticsNeverExposeArbitraryMessages() {
        var format=GroqBrain.responseFormat();
        assertTrue(format.getAsJsonObject("json_schema").get("strict").getAsBoolean());
        var schema=format.getAsJsonObject("json_schema").getAsJsonObject("schema");
        assertFalse(schema.get("additionalProperties").getAsBoolean());
        assertEquals(6,schema.getAsJsonObject("properties").getAsJsonObject("scope").getAsJsonArray("enum").size());
        assertEquals("IOException",GroqBrain.failureReason(new java.io.IOException("private-path-or-token")));
        assertEquals("Groq unavailable (HTTP 429)",GroqBrain.failureReason(new java.io.IOException("Groq unavailable (HTTP 429)")));
    }
    @Test public void scopesKeepGeneralKnowledgeSeparateFromServerFacts() {
        try(var brain=new GroqBrain("","test","You are M4-ND.")) {
            var notes=java.util.List.of(new GuideKnowledge.Note("test","","SECRET_TEST_SERVER_FACT","fallback"));
            assertTrue(brain.systemPrompt(GuideConversation.Topic.SERVER,notes).contains("SECRET_TEST_SERVER_FACT"));
            assertFalse(brain.systemPrompt(GuideConversation.Topic.LORE,notes).contains("SECRET_TEST_SERVER_FACT"));
            assertFalse(brain.systemPrompt(GuideConversation.Topic.REVIEW,notes).contains("SECRET_TEST_SERVER_FACT"));
            assertTrue(brain.systemPrompt(GuideConversation.Topic.LORE,notes).contains("canon/Legends"));
            assertTrue(brain.systemPrompt(GuideConversation.Topic.SERVER,notes).contains("ONLY from verified"));
            assertTrue(brain.systemPrompt(GuideConversation.Topic.REVIEW,notes).contains("UW-Madison"));
            assertTrue(brain.systemPrompt(GuideConversation.Topic.REVIEW,notes).contains("answer ONLY the allowed part"));
        }
    }
    @Test public void outsideAndAmbiguousRepliesNeverDisplayModelGeneratedFacts() throws Exception {
        try(var brain=new GroqBrain("","test","Droid","REDIRECT","CLARIFY","UNKNOWN")) {
            var outside=brain.scopedAnswer("{\"scope\":\"outside\",\"answer\":\"Walmart is a store.\"}",50,java.util.List.of());
            assertEquals("REDIRECT",outside.text());assertFalse(outside.supported());assertEquals(50,outside.tokens());
            var clarify=brain.scopedAnswer("{\"scope\":\"clarify\",\"answer\":\"Invented meaning\"}",60,java.util.List.of());
            assertEquals("CLARIFY",clarify.text());assertFalse(clarify.supported());
            var missing=brain.scopedAnswer("{\"scope\":\"server\",\"answer\":\"Invented server mechanics\"}",60,java.util.List.of());
            assertEquals("UNKNOWN",missing.text());
            for(String scope:java.util.List.of("star_wars","minecraft","conversation")) {
                var accepted=brain.scopedAnswer("{\"scope\":\""+scope+"\",\"answer\":\"Helpful reply.\"}",70,java.util.List.of());
                assertTrue(accepted.supported());assertEquals("Helpful reply.",accepted.text());
            }
        }
    }
    @Test public void malformedOrUnclassifiedRepliesFailClosed() throws Exception {
        try(var brain=new GroqBrain("","test","Droid")) {
            for(String raw:java.util.List.of("Walmart is a store.","{\"scope\":\"general\",\"answer\":\"No\"}",
                    "{\"scope\":\"conversation\",\"answer\":null}","{\"scope\":\"conversation\",\"answer\":42}",
                    "{\"scope\":\"conversation\",\"answer\":\"ok\",\"extra\":\"data\"}")) {
                assertThrows(java.io.IOException.class,()->brain.scopedAnswer(raw,1,java.util.List.of()));
            }
        }
    }
    @Test public void canonPrimerRemainsSeparateFromServerEvidence() {
        try(var brain=new GroqBrain("","test","Droid","redirect","clarify","unknown","CANON_REFERENCE")) {
            assertTrue(brain.systemPrompt(GuideConversation.Topic.LORE,java.util.List.of()).contains("CANON_REFERENCE"));
            assertTrue(brain.systemPrompt(GuideConversation.Topic.SERVER,java.util.List.of()).contains("never evidence for server mechanics"));
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
