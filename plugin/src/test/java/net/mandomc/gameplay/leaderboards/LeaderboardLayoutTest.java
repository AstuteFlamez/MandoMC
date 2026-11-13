package net.mandomc.gameplay.leaderboards;

import java.util.List;
import java.util.stream.IntStream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.Test;
import static org.junit.Assert.*;

public class LeaderboardLayoutTest {
    @Test public void emptyPartialAndFullBoardsKeepTheSameTenPositions() {
        for (int count : List.of(0, 2, 10, 12)) {
            var rows = IntStream.rangeClosed(1, count).mapToObj(i -> Component.text(i + ". Player: " + i)).toList();
            var text = LeaderboardLayout.render(Component.text("TITLE").decorate(TextDecoration.BOLD), Component.text("TOP TEN PLAYERS"), rows,
                    rank -> Component.text(rank + "."));
            assertEquals(TextDecoration.State.NOT_SET, text.decoration(TextDecoration.BOLD));
            var lines = PlainTextComponentSerializer.plainText().serialize(text).split("\n", -1);
            assertEquals(13, lines.length);
            assertEquals("TITLE", lines[0]);
            assertEquals("TOP TEN PLAYERS", lines[1]);
            assertEquals("", lines[2]);
            for (int rank = 1; rank <= 10; rank++) {
                assertEquals(rank <= count ? rank + ". Player: " + rank : rank + ".", lines[rank + 2]);
            }
        }
    }
}
