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
    @Test public void sharedDisplayNameKeepsRankColorAndRichLiteralNickname() {
        var name = Component.text("<red>Nick").clickEvent(ClickEvent.suggestCommand("/msg Nick "))
                .hoverEvent(Component.text("Player details"));
        var result = PlayerChat.displayName(rank("admin"), name);
        assertEquals("Icon <red>Nick", ChatStyle.plain(result));
        assertEquals(name.color(NamedTextColor.AQUA), result.children().getLast());
        assertEquals("<red>Nick", ChatStyle.plain(PlayerChat.displayName(rank("default"), name)));
    }
    @Test public void lifecycleTranslationsUseNameOnceAndKeepOldNameAndActions() {
        var name = PlayerChat.displayName(rank("admin"), Component.text("<red>Nick")
                .clickEvent(ClickEvent.suggestCommand("/msg Nick ")).hoverEvent(Component.text("Details")));
        for (String key : List.of("multiplayer.player.joined", "multiplayer.player.joined.renamed", "multiplayer.player.left")) {
            var input = Component.translatable(key, Component.text("Account").hoverEvent(Component.text("Native player details")), Component.text("OldAccount"))
                    .clickEvent(ClickEvent.runCommand("/list"));
            var styled = new ChatStyle().format(input, ChatCategory.STATUS);
            var result = PlayerChat.lifecycleName(styled, "Account", Component.text("Account"), name);
            var translation = (net.kyori.adventure.text.TranslatableComponent) result.children().get(1);
            assertEquals(key, translation.key());
            var argument = (Component) translation.arguments().getFirst().value();
            assertEquals(name, argument.children().getFirst());
            assertEquals(Component.text("Native player details"), argument.hoverEvent().value());
            assertEquals(((net.kyori.adventure.text.TranslatableComponent) styled.children().get(1)).arguments().get(1), translation.arguments().get(1));
            assertEquals(input.clickEvent(), translation.clickEvent());
            assertEquals(result, PlayerChat.lifecycleName(result, "Account", Component.text("Account"), name));
        }
    }
    @Test public void literalLifecycleNamesKeepWordBoundariesAndDoNotReparseMarkup() {
        var name = PlayerChat.displayName(rank("admin"), Component.text("<red>Nick"));
        var result = PlayerChat.lifecycleName(new ChatStyle().format(Component.text("Account left; OtherAccount stayed."), ChatCategory.STATUS),
                "Account", Component.text("Account"), name);
        assertEquals(ChatStyle.PREFIX + "Icon <red>Nick left; OtherAccount stayed.", ChatStyle.plain(result));
    }
    @Test public void rankedChatAddsOneAuthoritativeBadge() {
        var result = PlayerChat.render(rank("admin"), Component.text("Nick"), Component.text("hello"));
        assertEquals("Icon Nick » hello", ChatStyle.plain(result));
        assertFalse(ChatStyle.plain(result).contains(ChatStyle.PREFIX));
    }
}
