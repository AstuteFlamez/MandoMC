package net.mandomc.gameplay.abilities.command;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import net.mandomc.gameplay.abilities.model.AbilityClass;
import net.mandomc.gameplay.abilities.model.AbilityPlayerProfile;
import net.mandomc.gameplay.abilities.service.AbilityService;

/**
 * Selects a player's combat class.
 */
public class ClassCommand implements TabExecutor {
    private static final String PERMISSION = "mandomc.abilities.class";
    private final AbilityService abilityService;

    public ClassCommand(AbilityService abilityService) {
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

        if (args.length != 1) {
            player.sendMessage(ChatColor.YELLOW + "Usage: /class <jedi|sith|mandalorian>");
            return true;
        }

        AbilityClass.fromInput(args[0]).ifPresentOrElse(clazz -> {
            if (clazz == AbilityClass.UNSET) {
                player.sendMessage(ChatColor.RED + "Unknown class.");
                return;
            }
            AbilityPlayerProfile profile = abilityService.profile(player.getUniqueId());
            if (profile.selectedClass() == clazz) {
                player.sendMessage(ChatColor.YELLOW + "You are already a " + clazz.name().toLowerCase() + ".");
                return;
            }
            abilityService.switchClass(player, clazz);
            player.sendMessage(ChatColor.GREEN + "Class set to " + clazz.name().toLowerCase() + ". Existing binds were reset.");
        }, () -> player.sendMessage(ChatColor.RED + "Unknown class."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.stream(AbilityClass.values())
                    .filter(v -> v != AbilityClass.UNSET)
                    .map(v -> v.name().toLowerCase())
                    .filter(v -> v.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
