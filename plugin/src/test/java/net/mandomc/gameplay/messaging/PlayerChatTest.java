package net.mandomc.gameplay.messaging;

import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.mandomc.gameplay.ranks.RankCatalog;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlayerChatTest {
    private RankCatalog.Rank rank(String id) {
        return new RankCatalog.Rank(id, id, 1, "Badge", "Icon", NamedTextColor.AQUA, List.of(), Map.of());
    }
    @Test public void unrankedChatPreservesLiteralMarkupAndNicknameEvents() {
        var click = ClickEvent.suggestCommand("/msg Nick ");
        var name = Component.text("Nick", NamedTextColor.GOLD).clickEvent(click);
        var body = Component.text("<red><Name> $5");
        var result = PlayerChat.render(rank("default"), name, body);
        assertEquals("Nick » <red><Name> $5", ChatStyle.plain(result));
        assertEquals(name, result.children().getFirst());
        assertEquals(body.color(NamedTextColor.GRAY), result.children().getLast());
    }
    @Test public void rankedChatAddsOneAuthoritativeBadge() {
        var result = PlayerChat.render(rank("admin"), Component.text("Nick"), Component.text("hello"));
        assertEquals("Icon Nick » hello", ChatStyle.plain(result));
        assertFalse(ChatStyle.plain(result).contains(ChatStyle.PREFIX));
    }
}
