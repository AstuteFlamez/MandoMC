package com.astuteflamez.mandomc.features.events.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.astuteflamez.mandomc.features.events.EventManager;
import com.astuteflamez.mandomc.features.events.GameEvent;
import com.astuteflamez.mandomc.features.events.guis.EventMenu;

import java.util.ArrayList;
import java.util.List;

public class EventCommand implements CommandExecutor, TabCompleter {

    private final EventManager manager;

    public EventCommand(EventManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            if (sender instanceof Player player) {
                new EventMenu(manager).open(player);
            } else {
                sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7/event gui");
                if (sender.hasPermission("mandomc.event.admin")) {
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7/event start <id>");
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7/event stop");
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7/event forceend");
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7/event next <id>");
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7/event reroll");
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7/event status");
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7/event list");
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7/event reload");
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7/event enable <id>");
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7/event disable <id>");
                }
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("gui")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cPlayers only.");
                return true;
            }

            new EventMenu(manager).open(player);
            return true;
        }

        if (!sender.hasPermission("mandomc.event.admin")) {
            sender.sendMessage("§4§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cYou do not have permission to run this command.");
            return true;
        }

        switch (sub) {
            case "start" -> {
                if (args.length < 2) {
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §cUsage: /event start <id>");
                    return true;
                }

                if (manager.startEvent(args[1], true)) {
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Started event §f" + args[1]);
                } else {
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Could not start event §f" + args[1]);
                }
            }

            case "stop", "forceend" -> {
                GameEvent active = manager.getActiveEvent();
                if (active == null) {
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7No active event.");
                    return true;
                }

                manager.forceEndActiveEvent(true);
                sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Force-ended active event.");
            }

            case "next" -> {
                if (args.length < 2) {
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Usage: /event next <id>");
                    return true;
                }

                if (manager.queueEvent(args[1])) {
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Queued next event §f" + args[1]);
                } else {
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Could not queue event §f" + args[1]);
                }
            }

            case "reroll" -> {
                manager.rerollNextEvent();
                GameEvent queued = manager.getQueuedEvent();
                if (queued == null) {
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7No eligible event could be selected.");
                } else {
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Rerolled next event to §f" + queued.getDisplayName());
                }
            }

            case "reload" -> {
                manager.reload();
                sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Reloaded events.yml");
            }

            case "status" -> {
                GameEvent active = manager.getActiveEvent();
                GameEvent queued = manager.getQueuedEvent();

                sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Event Status");
                sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7State: §f" + manager.getState());
                sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Active: §f" + (active == null ? "None" : active.getDisplayName()));
                sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Queued: §f" + (queued == null ? "None" : queued.getDisplayName()));
                sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Next hour in: §f" + manager.getSecondsUntilNextHour() + "s");
            }

            case "list" -> {
                sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Configured Events:");
                for (String id : manager.getEventIds()) {
                    sender.sendMessage("§7- §f" + id);
                }
            }

            case "enable" -> {
                if (args.length < 2) {
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Usage: /event enable <id>");
                    return true;
                }

                if (manager.setEnabled(args[1], true)) {
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Enabled §f" + args[1]);
                } else {
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Failed to enable §f" + args[1]);
                }
            }

            case "disable" -> {
                if (args.length < 2) {
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Usage: /event disable <id>");
                    return true;
                }

                if (manager.setEnabled(args[1], false)) {
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Disabled §f" + args[1]);
                } else {
                    sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Failed to disable §f" + args[1]);
                }
            }

            default -> sender.sendMessage("§e§lᴍᴀɴᴅᴏᴍᴄ §r§8» §7Unknown subcommand.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            suggestions.add("gui");

            if (sender.hasPermission("mandomc.event.admin")) {
                suggestions.add("start");
                suggestions.add("stop");
                suggestions.add("forceend");
                suggestions.add("next");
                suggestions.add("reroll");
                suggestions.add("status");
                suggestions.add("list");
                suggestions.add("reload");
                suggestions.add("enable");
                suggestions.add("disable");
            }

            return filter(suggestions, args[0]);
        }

        if (args.length == 2 && sender.hasPermission("mandomc.event.admin")) {
            String sub = args[0].toLowerCase();
            if (sub.equals("start") || sub.equals("next") || sub.equals("enable") || sub.equals("disable")) {
                return filter(manager.getEventIds(), args[1]);
            }
        }

        return suggestions;
    }

    private List<String> filter(List<String> values, String input) {
        String lower = input.toLowerCase();
        return values.stream()
                .filter(v -> v.toLowerCase().startsWith(lower))
                .toList();
    }
}