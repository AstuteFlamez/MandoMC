package net.mandomc.gameplay.leaderboards;

import java.util.List;
import java.util.function.IntFunction;
import net.kyori.adventure.text.Component;

/** Fixed-height presentation shared by the world and Ilum boards. */
public final class LeaderboardLayout {
    public static final int SLOTS = 10;

    private LeaderboardLayout() { }

    public static Component render(Component title, Component subtitle, List<? extends Component> rows,
                                   IntFunction<Component> emptyRow) {
        Component text = title.append(Component.newline()).append(subtitle).append(Component.newline());
        for (int slot = 0; slot < SLOTS; slot++) {
            text = text.append(Component.newline()).append(slot < rows.size() ? rows.get(slot) : emptyRow.apply(slot + 1));
        }
        return text;
    }
}
