package net.mandomc.system.events.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.mandomc.system.events.EventManager;
import net.mandomc.system.events.GameEvent;
import net.mandomc.system.events.guis.EventMenu;
import net.mandomc.core.LangManager;

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
                sender.sendMessage(LangManager.get("events.help.gui"));
                if (sender.hasPermission("mandomc.event.admin")) {
                    sender.sendMessage(LangManager.get("events.help.start"));
                    sender.sendMessage(LangManager.get("events.help.stop"));
                    sender.sendMessage(LangManager.get("events.help.forceend"));
                    sender.sendMessage(LangManager.get("events.help.next"));
                    sender.sendMessage(LangManager.get("events.help.reroll"));
                    sender.sendMessage(LangManager.get("events.help.status"));
                    sender.sendMessage(LangManager.get("events.help.list"));
                    sender.sendMessage(LangManager.get("events.help.reload"));
                    sender.sendMessage(LangManager.get("events.help.enable"));
                    sender.sendMessage(LangManager.get("events.help.disable"));
                }
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("gui")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(LangManager.get("events.players-only"));
                return true;
            }

            new EventMenu(manager).open(player);
            return true;
        }

        if (!sender.hasPermission("mandomc.event.admin")) {
            sender.sendMessage(LangManager.get("events.no-permission"));
            return true;
        }

        switch (sub) {
            case "start" -> {
                if (args.length < 2) {
                    sender.sendMessage(LangManager.get("events.usage.start"));
                    return true;
                }

                if (manager.startEvent(args[1], true)) {
                    sender.sendMessage(LangManager.get("events.start.success", "%id%", args[1]));
                } else {
                    sender.sendMessage(LangManager.get("events.start.failure", "%id%", args[1]));
                }
            }

            case "stop", "forceend" -> {
                GameEvent active = manager.getActiveEvent();
                if (active == null) {
                    sender.sendMessage(LangManager.get("events.stop.no-active"));
                    return true;
                }

                manager.forceEndActiveEvent(true);
                sender.sendMessage(LangManager.get("events.stop.force-ended"));
            }

            case "next" -> {
                if (args.length < 2) {
                    sender.sendMessage(LangManager.get("events.usage.next"));
                    return true;
                }

                if (manager.queueEvent(args[1])) {
                    sender.sendMessage(LangManager.get("events.next.queued", "%id%", args[1]));
                } else {
                    sender.sendMessage(LangManager.get("events.next.failure", "%id%", args[1]));
                }
            }

            case "reroll" -> {
                manager.rerollNextEvent();
                GameEvent queued = manager.getQueuedEvent();
                if (queued == null) {
                    sender.sendMessage(LangManager.get("events.reroll.no-eligible"));
                } else {
                    sender.sendMessage(LangManager.get("events.reroll.success", "%event%", queued.getDisplayName()));
                }
            }

            case "reload" -> {
                manager.reload();
                sender.sendMessage(LangManager.get("events.reload-done"));
            }

            case "status" -> {
                GameEvent active = manager.getActiveEvent();
                GameEvent queued = manager.getQueuedEvent();

                sender.sendMessage(LangManager.get("events.status.header"));
                sender.sendMessage(LangManager.get("events.status.state", "%state%", String.valueOf(manager.getState())));
                sender.sendMessage(LangManager.get("events.status.active", "%active%", active == null ? "None" : active.getDisplayName()));
                sender.sendMessage(LangManager.get("events.status.queued", "%queued%", queued == null ? "None" : queued.getDisplayName()));
                sender.sendMessage(LangManager.get("events.status.next-hour", "%seconds%", String.valueOf(manager.getSecondsUntilNextHour())));
            }

            case "list" -> {
                sender.sendMessage(LangManager.get("events.list.header"));
                for (String id : manager.getEventIds()) {
                    sender.sendMessage(LangManager.get("events.list.entry", "%id%", id));
                }
            }

            case "enable" -> {
                if (args.length < 2) {
                    sender.sendMessage(LangManager.get("events.usage.enable"));
                    return true;
                }

                if (manager.setEnabled(args[1], true)) {
                    sender.sendMessage(LangManager.get("events.enable.success", "%id%", args[1]));
                } else {
                    sender.sendMessage(LangManager.get("events.enable.failure", "%id%", args[1]));
                }
            }

            case "disable" -> {
                if (args.length < 2) {
                    sender.sendMessage(LangManager.get("events.usage.disable"));
                    return true;
                }

                if (manager.setEnabled(args[1], false)) {
                    sender.sendMessage(LangManager.get("events.disable.success", "%id%", args[1]));
                } else {
                    sender.sendMessage(LangManager.get("events.disable.failure", "%id%", args[1]));
                }
            }

            default -> sender.sendMessage(LangManager.get("events.unknown-sub"));
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