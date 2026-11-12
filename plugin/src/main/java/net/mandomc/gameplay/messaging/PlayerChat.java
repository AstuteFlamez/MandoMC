package net.mandomc.gameplay.messaging;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.mandomc.gameplay.ranks.RankCatalog;

/** Presentation only: Paper owns signed message delivery and the event recipient set. */
public final class PlayerChat {
    private PlayerChat() { }
    public static Component render(RankCatalog.Rank rank, Component name, Component message) {
        Component result = Component.empty().decoration(TextDecoration.ITALIC, false);
        if (!rank.id().equals("default")) result = result.append(rank.badge(true)).append(Component.space());
        return result.append(name.colorIfAbsent(rank.color()))
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                .append(message.colorIfAbsent(NamedTextColor.GRAY));
    }
}
