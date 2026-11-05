package net.mandomc.gameplay.abilities.gui;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.guis.InventoryButton;
import net.mandomc.core.guis.InventoryGUI;
import net.mandomc.gameplay.abilities.config.AbilityDefinition;
import net.mandomc.gameplay.abilities.config.AbilityLevelDefinition;
import net.mandomc.gameplay.abilities.model.AbilityClass;
import net.mandomc.gameplay.abilities.model.AbilityNodeState;
import net.mandomc.gameplay.abilities.model.AbilityPlayerProfile;
import net.mandomc.gameplay.abilities.service.AbilityService;

/**
 * Ability tree GUI with locked/unlockable/unlocked node states.
 */
public class AbilityTreeGUI extends InventoryGUI {
    private static final int SIZE = 54;

    private final GUIManager guiManager;
    private final AbilityService abilityService;

    public AbilityTreeGUI(GUIManager guiManager, AbilityService abilityService) {
        this.guiManager = guiManager;
        this.abilityService = abilityService;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, SIZE, ChatColor.WHITE + "Abilities Tree");
    }

    @Override
    public void decorate(Player player) {
        fillBackground();
        AbilityPlayerProfile profile = abilityService.profile(player.getUniqueId());
        if (profile.selectedClass() == null || profile.selectedClass() == AbilityClass.UNSET) {
            addButton(22, new InventoryButton()
                    .creator(ignored -> labeled(Material.BARRIER, ChatColor.RED + "No Class Selected", List.of(
                            ChatColor.GRAY + "Use /class <jedi|sith|mandalorian>",
                            ChatColor.GRAY + "before opening the tree."
                    )))
                    .consumer(event -> {}));
            super.decorate(player);
            return;
        }

        addButton(48, new InventoryButton()
                .creator(ignored -> labeled(Material.SUNFLOWER, ChatColor.GOLD + "Skill Tokens", List.of(
                        ChatColor.YELLOW + String.valueOf(profile.skillTokens())
                )))
                .consumer(event -> {}));

        for (AbilityDefinition definition : abilityService.abilitiesForClass(profile.selectedClass())) {
            int slot = Math.max(0, Math.min(53, definition.guiSlot()));
            addButton(slot, buildNodeButton(player, profile, definition));
        }

        addButton(49, new InventoryButton()
                .creator(ignored -> labeled(Material.BOOK, ChatColor.GOLD + "Bind Menu", List.of(
                        ChatColor.GRAY + "Open hotbar binding GUI"
                )))
                .consumer(event -> guiManager.openGUI(new AbilityBindGUI(guiManager, abilityService), player)));

        super.decorate(player);
    }

    private InventoryButton buildNodeButton(Player player, AbilityPlayerProfile profile, AbilityDefinition definition) {
        return new InventoryButton()
                .creator(ignored -> buildNodeItem(profile, definition))
                .consumer(event -> {
                    AbilityNodeState state = abilityService.nodeState(profile, definition.id());
                    if (state == AbilityNodeState.UNLOCKABLE) {
                        AbilityService.UnlockResult unlock = abilityService.unlockNextLevel(player, definition.id());
                        if (unlock == AbilityService.UnlockResult.SUCCESS) {
                            player.sendMessage(ChatColor.GREEN + "Unlocked " + strip(definition.displayName()) + ".");
                        } else if (unlock == AbilityService.UnlockResult.NOT_ENOUGH_TOKENS) {
                            player.sendMessage(ChatColor.RED + "Not enough skill tokens.");
                        } else if (unlock == AbilityService.UnlockResult.LOCKED_BY_REQUIREMENTS) {
                            player.sendMessage(ChatColor.RED + "Locked: unlock prerequisite connected nodes first.");
                        } else if (unlock == AbilityService.UnlockResult.MAX_LEVEL_REACHED) {
                            player.sendMessage(ChatColor.YELLOW + "Already at max level.");
                        } else {
                            player.sendMessage(ChatColor.RED + "Could not unlock " + strip(definition.displayName()) + " (" + unlock.name().toLowerCase() + ").");
                        }
                        guiManager.openGUI(new AbilityTreeGUI(guiManager, abilityService), player);
                        return;
                    }

                    if (state == AbilityNodeState.UNLOCKED) {
                        if ((event.getClick() == ClickType.LEFT || event.getClick() == ClickType.SHIFT_LEFT)
                                && abilityService.canUpgradeLevel(player, definition.id())) {
                            AbilityService.UnlockResult unlock = abilityService.unlockNextLevel(player, definition.id());
                            if (unlock == AbilityService.UnlockResult.SUCCESS) {
                                int newLevel = abilityService.profile(player.getUniqueId()).selectedLevel(definition.id());
                                player.sendMessage(ChatColor.GREEN + "Upgraded " + strip(definition.displayName()) + " to level " + newLevel + ".");
                            } else {
                                player.sendMessage(ChatColor.RED + "Could not upgrade " + strip(definition.displayName()) + ".");
                            }
                            guiManager.openGUI(new AbilityTreeGUI(guiManager, abilityService), player);
                            return;
                        }
                        int unlocked = profile.unlockedLevel(definition.id());
                        int selected = profile.selectedLevel(definition.id());
                        int next;
                        if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
                            next = selected - 1;
                            if (next < 1) {
                                next = unlocked;
                            }
                        } else {
                            next = selected + 1;
                            if (next > unlocked) {
                                next = 1;
                            }
                        }
                        abilityService.setSelectedLevel(player, definition.id(), next);
                        player.sendMessage(ChatColor.AQUA + "Selected " + strip(definition.displayName()) + " level " + next + ".");
                        guiManager.openGUI(new AbilityTreeGUI(guiManager, abilityService), player);
                    }
                });
    }

    private ItemStack buildNodeItem(AbilityPlayerProfile profile, AbilityDefinition definition) {
        AbilityNodeState state = abilityService.nodeState(profile, definition.id());
        int unlocked = profile.unlockedLevel(definition.id());
        int selected = profile.selectedLevel(definition.id());

        Material material = switch (state) {
            case LOCKED -> Material.GRAY_DYE;
            case UNLOCKABLE -> Material.LIME_DYE;
            case UNLOCKED -> Material.CYAN_DYE;
        };

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "State: " + colorForState(state) + state.name().toLowerCase());
        lore.add(ChatColor.GRAY + "Unlocked level: " + ChatColor.WHITE + unlocked + ChatColor.GRAY + "/" + definition.maxLevel());
        if (unlocked > 0) {
            lore.add(ChatColor.GRAY + "Selected level: " + ChatColor.AQUA + selected);
        }
        int nextLevel = unlocked + 1;
        AbilityLevelDefinition next = definition.levels().get(nextLevel);
        if (next != null) {
            lore.add(ChatColor.GRAY + "Next cost: " + ChatColor.GOLD + next.tokenCost() + " token(s)");
        }
        if (!definition.requires().isEmpty()) {
            lore.add(ChatColor.DARK_GRAY + "Requires: " + String.join(", ", definition.requires()));
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Left click: unlock / upgrade / cycle");
        lore.add(ChatColor.YELLOW + "Right click: cycle level backwards");

        return labeled(material, colored(definition.displayName()), lore);
    }

    private ChatColor colorForState(AbilityNodeState state) {
        return switch (state) {
            case LOCKED -> ChatColor.DARK_GRAY;
            case UNLOCKABLE -> ChatColor.GREEN;
            case UNLOCKED -> ChatColor.AQUA;
        };
    }

    private void fillBackground() {
        ItemStack filler = labeled(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < SIZE; i++) {
            addButton(i, new InventoryButton().creator(player -> filler).consumer(event -> {}));
        }
    }

    private ItemStack labeled(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String colored(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private String strip(String text) {
        return ChatColor.stripColor(colored(text));
    }
}
