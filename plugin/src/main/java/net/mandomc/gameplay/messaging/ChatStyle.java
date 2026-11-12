package net.mandomc.gameplay.messaging;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Formats an existing component tree without reparsing player text, click events or translations. */
public final class ChatStyle {
    public static final String PREFIX = "MandoMC » ";
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final Pattern LEGACY = Pattern.compile("^(?:\\[(?:MandoMC|ᴍᴀɴᴅᴏᴍᴄ)\\]|(?:MandoMC|ᴍᴀɴᴅᴏᴍᴄ))\\s*[»>:]\\s*", Pattern.CASE_INSENSITIVE);

    public static String plain(Component component) { return PLAIN.serialize(component); }
    public static boolean formatted(Component component) {
        return component.children().size() == 2 && component.children().getFirst() instanceof TextComponent prefix
                && prefix.content().equals(PREFIX) && component.decoration(TextDecoration.ITALIC) == TextDecoration.State.FALSE;
    }
    public static String withoutPrefix(String text) { return LEGACY.matcher(text).replaceFirst(""); }

    public Component format(Component input, ChatCategory category) {
        if (formatted(input)) return input;
        var match = LEGACY.matcher(plain(input));
        Component body = match.find() ? removeLeadingText(input, new AtomicInteger(match.end())) : input;
        TextColor base = firstColor(body, null);
        body = restyle(body, category, base);
        var prefix = Component.text(PREFIX, category.color())
                .decoration(TextDecoration.ITALIC, category == ChatCategory.ADMIN);
        return Component.empty().color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
                .append(prefix).append(body);
    }

    private Component removeLeadingText(Component node, AtomicInteger count) {
        if (count.get() == 0) return node;
        if (node instanceof TextComponent text) {
            int take = Math.min(count.get(), text.content().length());
            count.addAndGet(-take); node = text.content(text.content().substring(take));
        }
        var children = new ArrayList<Component>();
        for (var child : node.children()) children.add(removeLeadingText(child, count));
        return node.children(children);
    }

    private TextColor firstColor(Component node, TextColor inherited) {
        TextColor color = node.color() == null ? inherited : node.color();
        if (!(node instanceof TextComponent text) || !text.content().isBlank()) return color;
        for (var child : node.children()) {
            TextColor result = firstColor(child, color);
            if (result != null) return result;
        }
        return null;
    }

    private Component restyle(Component node, ChatCategory category, TextColor base) {
        if (category == ChatCategory.ADMIN || category == ChatCategory.ERROR)
            node = node.color(NamedTextColor.RED).decoration(TextDecoration.ITALIC, category == ChatCategory.ADMIN);
        else {
            if (node.color() != null && node.color().equals(base)) node = node.color(NamedTextColor.GRAY);
            node = node.decoration(TextDecoration.ITALIC, false);
        }
        List<Component> children = node.children().stream().map(child -> restyle(child, category, base)).toList();
        node = node.children(children);
        if (node instanceof TranslatableComponent translated) {
            node = translated.arguments(translated.arguments().stream().map(arg -> {
                var value = arg.value();
                return value instanceof Component c ? net.kyori.adventure.text.TranslationArgument.component(restyle(c, category, base)) : arg;
            }).toList());
        }
        return node;
    }
}
