package net.mandomc.integrations.discord;

import java.io.InputStreamReader;
import net.kyori.adventure.text.*;
import net.kyori.adventure.text.event.ClickEvent;
import net.mandomc.gameplay.messaging.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class DiscordMessagesTest {
    private boolean click(Component node, ClickEvent event) {
        return event.equals(node.clickEvent()) || node.children().stream().anyMatch(child -> click(child,event));
    }
    @Test public void inviteAndBothCopyActionsSurviveActualChatFormatting() {
        var messages = YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/language/discord.yml")));
        var result = new ChatStyle().format(DiscordMessages.render(messages,"code","ABCD2345"),ChatCategory.INFO);
        assertTrue(click(result,ClickEvent.openUrl("https://discord.gg/bruuZCG5Pk")));
        assertTrue(click(result,ClickEvent.copyToClipboard("ABCD2345")));
        assertTrue(click(result,ClickEvent.copyToClipboard("/link code:ABCD2345")));
        assertFalse(ChatStyle.plain(result).toLowerCase().contains("production"));
        assertTrue(ChatStyle.plain(result).contains("5,000 credits"));
    }
    @Test public void guideInviteHasRealBlueOpenUrlEvent() {
        var result = DiscordMessages.decorate(Component.text("Join Discord at https://discord.gg/bruuZCG5Pk"),DiscordMessages.DEFAULT_INVITE);
        assertTrue(click(result,ClickEvent.openUrl(DiscordMessages.DEFAULT_INVITE)));
        assertTrue(result.children().stream().anyMatch(child -> DiscordMessages.BLUE.equals(child.color())));
    }
}
