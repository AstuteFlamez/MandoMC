package net.mandomc.gameplay.abilities.command;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import net.mandomc.gameplay.abilities.service.AbilityService;

/**
 * Admin command for skill token get/give/drop test workflows.
 */
public class SkillTokenCommand implements TabExecutor {
    private static final String PERMISSION = "mandomc.abilities.tokens.admin";
    private final AbilityService abilityService;

    public SkillTokenCommand(AbilityService abilityService) {
        this.abilityService = abilityService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /skilltoken <get|give|drop> ...");
            return true;
        }
        String action = args[0].toLowerCase(Locale.ROOT);
        return switch (action) {
            case "get" -> handleGet(sender, args);
            case "give" -> handleGive(sender, args);
            case "drop" -> handleDrop(sender, args);
            default -> {
                sender.sendMessage(ChatColor.RED + "Unknown action.");
                yield true;
            }
        };
    }

    private boolean handleGet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /skilltoken get.");
            return true;
        }
        int amount = parseAmount(args[1], sender);
        if (amount < 0) {
            return true;
        }
        abilityService.addTokens(player, amount);
        player.sendMessage(ChatColor.GREEN + "Added " + amount + " skill tokens.");
        return true;
    }

    private boolean handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /skilltoken give <player> <amount>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }
        int amount = parseAmount(args[2], sender);
        if (amount < 0) {
            return true;
        }
        abilityService.addTokens(target, amount);
        sender.sendMessage(ChatColor.GREEN + "Gave " + amount + " tokens to " + target.getName() + ".");
        target.sendMessage(ChatColor.GREEN + "You received " + amount + " skill tokens.");
        return true;
    }

    private boolean handleDrop(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use /skilltoken drop.");
            return true;
        }
        int amount = parseAmount(args[1], sender);
        if (amount < 0) {
            return true;
        }
        abilityService.addTokens(player, -amount);
        player.sendMessage(ChatColor.YELLOW + "Removed " + amount + " skill tokens.");
        return true;
    }

    private int parseAmount(String raw, CommandSender sender) {
        try {
            int amount = Integer.parseInt(raw);
            if (amount < 0) {
                sender.sendMessage(ChatColor.RED + "Amount must be >= 0.");
                return -1;
            }
            return amount;
        } catch (NumberFormatException ignored) {
            sender.sendMessage(ChatColor.RED + "Amount must be a number.");
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("get", "give", "drop").stream()
                    .filter(v -> v.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
