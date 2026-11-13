package net.mandomc.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;

/** Utility command messages share a language section and never parse player input as markup. */
public final class CommandMessages {
    private final ConfigurationSection language;
    public CommandMessages(ConfigurationSection language) { this.language = language; }
    public Component text(String key, String value) {
        String template = language.getString("utilities." + key);
        if (template == null) throw new IllegalStateException("language/messages.yml: missing utilities." + key);
        return MiniMessage.miniMessage().deserialize(template, Placeholder.unparsed("value", value));
    }
    public void send(CommandSender sender, String key) { send(sender, key, ""); }
    public void send(CommandSender sender, String key, String value) {
        net.mandomc.gameplay.messaging.ChatMessages.send(sender,"language/messages.yml","utilities."+key,text(key,value));
    }
}
