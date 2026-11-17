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
}
