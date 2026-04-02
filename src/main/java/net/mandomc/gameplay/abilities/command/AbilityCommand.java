package net.mandomc.gameplay.abilities.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import net.mandomc.core.guis.GUIManager;
import net.mandomc.gameplay.abilities.config.AbilityDefinition;
import net.mandomc.gameplay.abilities.gui.AbilityBindGUI;
import net.mandomc.gameplay.abilities.gui.AbilityTreeGUI;
import net.mandomc.gameplay.abilities.model.AbilityClass;
import net.mandomc.gameplay.abilities.model.AbilityPlayerProfile;
import net.mandomc.gameplay.abilities.service.AbilityService;

/**
 * Main player/admin utility command for ability progression actions.
 */
public class AbilityCommand implements TabExecutor {
    private static final String PERMISSION = "mandomc.abilities.use";
    private static final String ADMIN_PERMISSION = "mandomc.abilities.admin";

    private final GUIManager guiManager;
    private final AbilityService abilityService;

    public AbilityCommand(GUIManager guiManager, AbilityService abilityService) {
        this.guiManager = guiManager;
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

        if (!abilityService.hasSelectedClass(player) && (args.length == 0
                || "tree".equalsIgnoreCase(args[0])
                || "bindgui".equalsIgnoreCase(args[0])
                || "unlock".equalsIgnoreCase(args[0])
                || "level".equalsIgnoreCase(args[0]))) {
            player.sendMessage(ChatColor.RED + "Select a class first with /class <jedi|sith|mandalorian>.");
            return true;
        }

        if (args.length == 0 || "tree".equalsIgnoreCase(args[0])) {
            guiManager.openGUI(new AbilityTreeGUI(guiManager, abilityService), player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "bindgui" -> {
                guiManager.openGUI(new AbilityBindGUI(guiManager, abilityService), player);
                return true;
            }
            case "unlock" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /ability unlock <abilityId>");
                    return true;
                }
                AbilityService.UnlockResult result = abilityService.unlockNextLevel(player, args[1]);
                switch (result) {
                    case SUCCESS -> player.sendMessage(ChatColor.GREEN + "Unlocked next level for " + args[1] + ".");
                    case NO_CLASS_SELECTED -> player.sendMessage(ChatColor.RED + "Select a class first.");
                    case UNKNOWN_ABILITY -> player.sendMessage(ChatColor.RED + "Unknown ability id: " + args[1]);
                    case WRONG_CLASS -> player.sendMessage(ChatColor.RED + "That ability does not belong to your selected class.");
                    case LOCKED_BY_REQUIREMENTS -> player.sendMessage(ChatColor.RED + "Node is locked. Unlock connected prerequisite nodes first.");
                    case MAX_LEVEL_REACHED -> player.sendMessage(ChatColor.RED + "This ability is already at max level.");
                    case LEVEL_CONFIG_MISSING -> player.sendMessage(ChatColor.RED + "This ability level is not configured.");
                    case NOT_ENOUGH_TOKENS -> player.sendMessage(ChatColor.RED + "Not enough skill tokens.");
                }
                return true;
            }
            case "level" -> {
                if (args.length < 3) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /ability level <abilityId> <level>");
                    return true;
                }
                int level;
                try {
                    level = Integer.parseInt(args[2]);
                } catch (NumberFormatException ignored) {
                    player.sendMessage(ChatColor.RED + "Level must be a number.");
                    return true;
                }
                boolean ok = abilityService.setSelectedLevel(player, args[1], level);
                player.sendMessage(ok
                        ? ChatColor.GREEN + "Selected level " + level + " for " + args[1] + "."
                        : ChatColor.RED + "Could not select that level.");
                return true;
            }
            case "info" -> {
                AbilityPlayerProfile profile = abilityService.profile(player.getUniqueId());
                String classLabel = profile.selectedClass() == null || profile.selectedClass() == AbilityClass.UNSET
                        ? "unset"
                        : profile.selectedClass().name().toLowerCase();
                player.sendMessage(ChatColor.GOLD + "Class: " + classLabel);
                player.sendMessage(ChatColor.GOLD + "Skill tokens: " + profile.skillTokens());
                player.sendMessage(ChatColor.GOLD + "Unlocked abilities: " + profile.unlockedLevels().size());
                return true;
            }
            case "settokens" -> {
                if (!player.hasPermission(ADMIN_PERMISSION)) {
                    player.sendMessage(ChatColor.RED + "You do not have permission.");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(ChatColor.YELLOW + "Usage: /ability settokens <amount>");
                    return true;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {
                    player.sendMessage(ChatColor.RED + "Amount must be numeric.");
                    return true;
                }
                abilityService.setTokens(player, amount);
                player.sendMessage(ChatColor.GREEN + "Your skill tokens were set to " + amount + ".");
                return true;
            }
            default -> {
                player.sendMessage(ChatColor.YELLOW + "Usage: /ability <tree|bindgui|unlock|level|info|settokens>");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("tree", "bindgui", "unlock", "level", "info", "settokens").stream()
                    .filter(v -> v.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && ("unlock".equalsIgnoreCase(args[0]) || "level".equalsIgnoreCase(args[0]))) {
            if (!(sender instanceof Player player)) {
                return List.of();
            }
            AbilityPlayerProfile profile = abilityService.profile(player.getUniqueId());
            return abilityService.abilitiesForClass(profile.selectedClass()).stream()
                    .map(AbilityDefinition::id)
                    .filter(id -> id.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && "level".equalsIgnoreCase(args[0])) {
            return List.of("1", "2", "3");
        }
        return new ArrayList<>();
    }
}
