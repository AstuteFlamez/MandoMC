package net.mandomc.gameplay.abilities.command;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import net.mandomc.gameplay.abilities.config.AbilityDefinition;
import net.mandomc.gameplay.abilities.model.AbilityKind;
import net.mandomc.gameplay.abilities.model.AbilityPlayerProfile;
import net.mandomc.gameplay.abilities.service.AbilityService;

/**
 * Command-based binding helper for test iteration.
 */
public class BindCommand implements TabExecutor {
    private static final String PERMISSION = "mandomc.abilities.bind";
    private final AbilityService abilityService;

    public BindCommand(AbilityService abilityService) {
        this.abilityService = abilityService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            player.sendMessage(ChatColor.RED + "You do not have permission.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /bind <slot(1-9)|clear> [abilityId]");
            return true;
        }
        if ("clear".equalsIgnoreCase(args[0])) {
            if (args.length < 2) {
                player.sendMessage(ChatColor.YELLOW + "Usage: /bind clear <slot(1-9)>");
                return true;
            }
            int slot = parseSlot(args[1]);
            if (slot < 0) {
                player.sendMessage(ChatColor.RED + "Slot must be 1-9.");
                return true;
            }
            abilityService.clearBinding(player, slot);
            player.sendMessage(ChatColor.GREEN + "Cleared binding for slot " + (slot + 1) + ".");
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /bind <slot(1-9)> <abilityId>");
            return true;
        }
        int slot = parseSlot(args[0]);
        if (slot < 0) {
            player.sendMessage(ChatColor.RED + "Slot must be 1-9.");
            return true;
        }
        boolean ok = abilityService.bindAbility(player, slot, args[1]);
        player.sendMessage(ok
                ? ChatColor.GREEN + "Bound " + args[1] + " to slot " + (slot + 1) + " (left_click)."
                : ChatColor.RED + "Could not bind that ability.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "clear").stream()
                    .filter(v -> v.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && !"clear".equalsIgnoreCase(args[0])) {
            AbilityPlayerProfile profile = abilityService.profile(player.getUniqueId());
            return abilityService.abilitiesForClass(profile.selectedClass()).stream()
                    .filter(def -> def.bindable() || def.kind() == AbilityKind.FORCE_JUMP)
                    .map(AbilityDefinition::id)
                    .filter(id -> id.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    private int parseSlot(String arg) {
        try {
            int value = Integer.parseInt(arg);
            if (value < 1 || value > 9) {
                return -1;
            }
            return value - 1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
