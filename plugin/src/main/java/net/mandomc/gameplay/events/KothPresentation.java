package net.mandomc.gameplay.events;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.gameplay.clans.ClanState;
import net.mandomc.gameplay.messaging.ChatCategory;
import net.mandomc.gameplay.messaging.ChatStyle;
import net.mandomc.gameplay.territory.TerritoryIdentity;
import net.mandomc.gameplay.territory.TerritoryPolicy;
import org.bukkit.Bukkit;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.server.BroadcastMessageEvent;
import org.bukkit.permissions.Permissible;

/** KOTH presentation only. Capture, membership and raid state retain their existing owners. */
final class KothPresentation {
    private static final String NAME = "mandomc:koth:contestant";
    private static final String MARKER = "\uE000koth-contestant\uE001";
    private KothPresentation() {}

    static Component name(ClanState.Clan owner, ClanState.Clan viewer, ClanState state,
                          Component solo, long now) {
        if (owner == null) return solo;
        var identity = TerritoryIdentity.read(TerritoryPolicy.Zone.WILDERNESS, owner, viewer, state, now);
        var color = identity.relation() == TerritoryIdentity.Relation.OWN ? NamedTextColor.GREEN
                : identity.relation() == TerritoryIdentity.Relation.RAID ? NamedTextColor.RED : NamedTextColor.BLUE;
        return Component.text(identity.name(), color);
    }

    static Component template(String template, int seconds, String recipient, boolean chat) {
        var body = MiniMessage.miniMessage().deserialize(template,
                Placeholder.unparsed("player", MARKER), Placeholder.unparsed("clan", MARKER),
                Placeholder.unparsed("seconds", Integer.toString(seconds)),
                Placeholder.unparsed("recipient", recipient));
        // Format the template before inserting rich names: ChatStyle must not flatten name colors.
        if (chat) body = new ChatStyle().format(body, ChatCategory.EVENT);
        return body.replaceText(r -> r.matchLiteral(MARKER)
                .replacement(Component.empty().insertion(NAME)));
    }

    static Component render(Component template, Component name) {
        if (NAME.equals(template.insertion())) return Component.empty().append(name);
        return template.children(template.children().stream().map(c -> render(c, name)).toList());
    }

    static Set<CommandSender> recipients(Set<Permissible> subscriptions, String permission) {
        Set<CommandSender> result = new HashSet<>();
        for (var permissible : subscriptions)
            if (permissible instanceof CommandSender sender && !(sender instanceof BlockCommandSender)
                    && permissible.hasPermission(permission)) result.add(sender);
        return result;
    }

    static void broadcast(Component template, Function<Player, Component> names, Component neutral) {
        String permission = org.bukkit.Server.BROADCAST_CHANNEL_USERS;
        var audience = recipients(Bukkit.getPluginManager().getPermissionSubscriptions(permission), permission);
        var event = new BroadcastMessageEvent(!Bukkit.isPrimaryThread(), render(template, neutral.insertion(NAME)), audience);
        if (!event.callEvent()) return;
        deliver(event, names, neutral);
    }

    static void deliver(BroadcastMessageEvent event, Function<Player, Component> names, Component neutral) {
        if (event.isCancelled()) return;
        for (var recipient : event.getRecipients())
            recipient.sendMessage(render(event.message(), recipient instanceof Player p ? names.apply(p) : neutral));
    }
}
