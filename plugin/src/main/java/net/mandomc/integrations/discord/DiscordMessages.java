package net.mandomc.integrations.discord;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.configuration.ConfigurationSection;

/** Real client click events for joining and copying; account codes remain literal text. */
public final class DiscordMessages {
    public static final TextColor BLUE = TextColor.color(0x5865F2);
    public static final String DEFAULT_INVITE = "https://discord.gg/bruuZCG5Pk";
    private DiscordMessages() { }
    public static Component render(ConfigurationSection messages, String key, String code) {
        String invite = messages.getString("invite-url", DEFAULT_INVITE);
        var join = Component.text("Discord", BLUE).clickEvent(ClickEvent.openUrl(invite))
                .hoverEvent(Component.text("Click to join the MandoMC Discord"));
        var copy = Component.text(code, BLUE).clickEvent(ClickEvent.copyToClipboard(code))
                .hoverEvent(Component.text("Click to copy your code"));
        var command = Component.text("Copy command", BLUE).clickEvent(ClickEvent.copyToClipboard("/link code:" + code))
                .hoverEvent(Component.text("Copy the full Discord /link command"));
        return decorate(MiniMessage.miniMessage().deserialize(messages.getString(key),
                Placeholder.component("discord", join), Placeholder.component("code", copy),
                Placeholder.component("copy-command", command)), invite);
    }
    public static Component decorate(Component input, String invite) {
        return input.replaceText(builder -> builder.matchLiteral("Discord")
                .replacement((match, value) -> value.color(BLUE)))
                .replaceText(builder -> builder.matchLiteral(invite).replacement((match, value) -> value.color(BLUE)
                        .clickEvent(ClickEvent.openUrl(invite)).hoverEvent(Component.text("Click to join the MandoMC Discord"))));
    }
}
