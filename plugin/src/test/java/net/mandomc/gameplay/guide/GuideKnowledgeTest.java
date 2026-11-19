package net.mandomc.gameplay.guide;
import java.io.InputStreamReader;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;
public class GuideKnowledgeTest {
    @Test public void unrelatedQuestionsHaveNoFactsAndSupportedQuestionsFindTheirTopic(){
        var config=YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/guide/protocol-droid.yml")));
        var knowledge=new GuideKnowledge(config.getConfigurationSection("knowledge"));
        assertTrue(knowledge.find("write me a chocolate cake recipe for dinner").stream().allMatch(n->n.id().equals("recipes")));
        assertTrue(knowledge.find("what is the capital of France").isEmpty());
        assertEquals("jawas",knowledge.find("where can I buy electronic components from jawas").getFirst().id());
        assertEquals("identity",knowledge.find("who are you").getFirst().id());
        assertEquals("forge",knowledge.find("how do saber upgrades work").getFirst().id());
    }
    @Test public void verifiedAnswersUseCurrentRecipeSourcesRatherThanUpgradeAdvice(){
        var config=YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/guide/protocol-droid.yml")));
        var recipes=YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/items/recipes.yml")));
        var knowledge=new GuideKnowledge(config.getConfigurationSection("knowledge"),recipes);
        var coal=knowledge.find("where to get coal").getFirst();
        assertEquals("item:minecraft:coal",coal.id());
        for(String planet:java.util.List.of("Tatooine","Hoth","Naboo"))assertTrue(coal.facts().contains(planet));
        assertFalse(coal.facts().contains("Mandalore"));
        assertTrue(coal.facts().contains("pickaxe"));
        assertFalse(coal.facts().contains("<gray>"));assertFalse(coal.facts().contains("\\n"));
        assertEquals(coal.fallback(),knowledge.verifiedAnswer("where to get coal",knowledge.find("where to get coal")).orElseThrow());
        assertTrue(knowledge.verifiedAnswer("what should I do",knowledge.find("what should I do")).orElseThrow().contains("/tutorial"));
        assertTrue(knowledge.verifiedAnswer("how do saber upgrades work",knowledge.find("how do saber upgrades work")).isEmpty());
        var saber=knowledge.find("how to get saber").getFirst();
        assertEquals("sabers",saber.id());assertTrue(saber.facts().contains("craft one at a Saber Workbench"));assertTrue(saber.facts().contains("Ilum"));
        assertTrue(knowledge.find("how do I get a lightsaber").stream().noneMatch(n->n.id().equals("forge")));
        assertEquals("forge",knowledge.find("how do saber upgrades work").getFirst().id());
        assertEquals("item:blue_kyber",knowledge.find("where can I get blue kyber crystals").getFirst().id());
        var coil=knowledge.find("how do I craft precision emitter coils").getFirst();
        assertEquals("item:precision_emitter_coil",coil.id());assertTrue(coil.facts().contains("2 electrum"));assertTrue(coil.facts().contains("brown Jawa"));
        assertTrue(knowledge.find("what is the capital of France").isEmpty());
        assertFalse(config.getString("text.question").toLowerCase().contains("groq"));
        for(String question:java.util.List.of("what to do", "what should I do", "what do I do next?", "I just joined, any advice?")){
            var advice=knowledge.find(question).getFirst();assertEquals("beginner",advice.id());
            assertTrue(advice.facts().contains("/tutorial"));assertTrue(advice.facts().contains("Naboo"));
        }
        assertEquals("item:minecraft:coal",knowledge.find("what do I do with coal").getFirst().id());
        assertTrue(knowledge.verifiedAnswer("How does Fortune affect custom ore drops?",knowledge.find("How does Fortune affect custom ore drops?")).orElseThrow().contains("exactly one"));
        assertTrue(knowledge.verifiedAnswer("What happens to my gear when I die?",knowledge.find("What happens to my gear when I die?")).orElseThrow().contains("drops at the death site"));
        assertEquals("item:saber_workbench",knowledge.find("how do I craft a saber workbench").getFirst().id());
        assertEquals("item:durasteel",knowledge.find("where can i find durastel").getFirst().id());
        assertTrue(knowledge.find("how do I craft robes helmet").getFirst().facts().contains("Banthas drop"));
        var base="I go to UW-Madison; where should I build my first MandoMC base?";
        assertEquals("beginner",knowledge.find(base).getFirst().id());
        var advice=knowledge.verifiedAnswer(base,knowledge.find(base)).orElseThrow();
        assertTrue(advice.contains("Naboo") && advice.contains("/c map"));
        assertFalse(advice.contains("48") || advice.contains("Wisconsin"));
    }
}
