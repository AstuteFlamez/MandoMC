package net.mandomc.gameplay.messaging;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.mandomc.gameplay.ranks.RankCatalog;

/** Presentation only: Paper owns signed message delivery and the event recipient set. */
public final class PlayerChat {
    private PlayerChat() { }
    public static Component displayName(RankCatalog.Rank rank, Component name) {
        Component result = Component.empty().decoration(TextDecoration.ITALIC, false);
        if (!rank.id().equals("default")) result = result.append(rank.badge(true)).append(Component.space());
        return result.append(name.colorIfAbsent(rank.color()));
    }
    /** Replace only the lifecycle name; keep native translations and surrounding rich content. */
    public static Component lifecycleName(Component input, String account, Component rawName, Component name) {
        if (input.equals(name)) return input;
        var children = input.children().stream().map(child -> lifecycleName(child, account, rawName, name)).toList();
        Component node = input.children(List.of());
        if (node instanceof TranslatableComponent translated) {
            var arguments = new ArrayList<>(translated.arguments());
            if (List.of("multiplayer.player.joined", "multiplayer.player.joined.renamed", "multiplayer.player.left").contains(translated.key()) && !arguments.isEmpty()) {
                var original = arguments.getFirst().value();
                if (!(original instanceof Component component && (component.equals(name) || component.children().contains(name)))) {
                    var wrapper = original instanceof Component component ? Component.empty().style(component.style()) : Component.empty();
                    arguments.set(0, TranslationArgument.component(wrapper.append(name)));
                }
            } else {
                for (int i = 0; i < arguments.size(); i++) {
                    if (arguments.get(i).value() instanceof Component value)
                        arguments.set(i, TranslationArgument.component(lifecycleName(value, account, rawName, name)));
                }
            }
            node = translated.arguments(arguments);
        } else if (node instanceof TextComponent) {
            String raw = ChatStyle.plain(rawName);
            String pattern = "(?<![A-Za-z0-9_])" + Pattern.quote(account) + "(?![A-Za-z0-9_])";
            if (!raw.isEmpty() && !raw.equals(account)) pattern = Pattern.quote(raw) + "|" + pattern;
            var match = Pattern.compile(pattern);
            node = node.replaceText(replacement -> replacement.match(match)
                    .replacement(Component.empty().append(name)));
        }
        for (var child : children) node = node.append(child);
        return node;
    }
    public static Component render(RankCatalog.Rank rank, Component name, Component message) {
        return displayName(rank, name).append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                .append(message.colorIfAbsent(NamedTextColor.GRAY));
    }
}
