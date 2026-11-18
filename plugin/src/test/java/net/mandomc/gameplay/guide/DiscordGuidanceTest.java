package net.mandomc.gameplay.guide;

import java.io.InputStreamReader;
import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiscordGuidanceTest {
    @Test public void joinLinkRewardAndSupportHaveExactLocalAnswers() {
        var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/guide/protocol-droid.yml")));
        var knowledge = new GuideKnowledge(yaml.getConfigurationSection("knowledge"));
        for (String question : List.of("how do I join the discord", "what is the discord invite", "how do I link my account", "who is C0-M5", "how do I get the linking reward", "how do discord ranks sync", "how do I open a support ticket")) {
            var selected = knowledge.find(question);
            assertFalse(question, selected.isEmpty());
            var answer = knowledge.verifiedAnswer(question, selected).orElseThrow(() -> new AssertionError(question));
            assertTrue(question, answer.contains("C0-M5"));
        }
        var answer = knowledge.verifiedAnswer("how do I join the discord", knowledge.find("how do I join the discord")).orElseThrow();
        assertTrue(answer.contains("https://discord.gg/bruuZCG5Pk"));
    }
}
