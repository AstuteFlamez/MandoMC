package net.mandomc.commands;

import java.util.*;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.mandomc.gameplay.ranks.RankService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class RankCommands {
    public RankCommands(RootCommand root, RankService ranks, ConfigurationSection messages) {
        root.register("rank", "mandomc.ranks.manage", (sender, args) -> {
            try {
                if (args.length == 1 && args[0].equals("list")) {
                    send(sender, messages, "list", "", String.join(", ", ranks.catalog().ids())); return;
                }
                if (args.length < 2 || args.length > 3) throw new IllegalArgumentException("usage");
                var target = KnownPlayer.find(sender, args[1]);
                if (args[0].equals("get") && args.length == 2) {
                    send(sender, messages, "current", target.name(), String.join(", ", ranks.memberships(target.id()))); return;
                }
                if (args.length != 3 || !List.of("set", "add", "remove").contains(args[0])) throw new IllegalArgumentException("usage");
                if (!ranks.catalog().ids().contains(args[2])) throw new IllegalArgumentException("unknown");
                var assigned = new HashSet<>(ranks.memberships(target.id()));
                switch (args[0]) {
                    case "set" -> { assigned.clear(); assigned.add(args[2]); }
                    case "add" -> assigned.add(args[2]);
                    case "remove" -> assigned.remove(args[2]);
                }
                if (assigned.isEmpty()) assigned.add("default");
                ranks.change(target.id(), assigned, saved -> send(sender, messages, saved ? "changed" : "save-failed",
                        target.name(), String.join(", ", assigned)));
            } catch (IllegalArgumentException error) { send(sender, messages, error.getMessage(), "", ""); }
        }, (sender, args) -> {
            List<String> options = args.length == 1 ? List.of("list", "get", "set", "add", "remove")
                    : args.length == 3 ? ranks.catalog().ids() : args.length == 2 ? Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !(sender instanceof Player viewer) || viewer.canSee(p)).map(Player::getName).toList() : List.of();
            return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[args.length - 1].toLowerCase(Locale.ROOT))).toList();
        });
    }
    private static void send(CommandSender sender, ConfigurationSection messages, String key, String player, String ranks) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize(messages.getString(key),
                Placeholder.unparsed("player", player), Placeholder.unparsed("ranks", ranks)));
    }
}
