package net.mandomc.gameplay.abilities.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.guis.InventoryButton;
import net.mandomc.core.guis.InventoryGUI;
import net.mandomc.gameplay.abilities.config.AbilityDefinition;
import net.mandomc.gameplay.abilities.model.AbilityKind;
import net.mandomc.gameplay.abilities.model.AbilityPlayerProfile;
import net.mandomc.gameplay.abilities.service.AbilityService;

/**
 * Ability picker for a specific hotbar slot.
 */
public class AbilityBindSlotSelectGUI extends InventoryGUI {
    private static final int SIZE = 54;

    private final GUIManager guiManager;
    private final AbilityService abilityService;
    private final int hotbarSlot;

    public AbilityBindSlotSelectGUI(GUIManager guiManager, AbilityService abilityService, int hotbarSlot) {
        this.guiManager = guiManager;
        this.abilityService = abilityService;
        this.hotbarSlot = hotbarSlot;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, SIZE, ChatColor.DARK_GREEN + "Select Ability - Slot " + (hotbarSlot + 1));
    }

    @Override
    public void decorate(Player player) {
        fillBackground();
        AbilityPlayerProfile profile = abilityService.profile(player.getUniqueId());
        List<BindOption> options = availableBindings(profile);

        int idx = 0;
        for (BindOption option : options) {
            if (idx >= 45) {
                break;
            }
            final String abilityId = option.definition().id();
            final int level = option.level();
            addButton(idx, new InventoryButton()
                    .creator(ignored -> abilityItem(option))
                    .consumer(event -> {
                        boolean ok = abilityService.bindAbility(player, hotbarSlot, abilityId, level);
                        if (!ok) {
                            player.sendMessage(ChatColor.RED + "Could not bind ability.");
                        } else {
                            player.sendMessage(ChatColor.GREEN + "Bound " + strip(option.definition().displayName()) + " " + toRoman(level) + " to slot " + (hotbarSlot + 1) + ".");
                        }
                        guiManager.openGUI(new AbilityBindGUI(guiManager, abilityService), player);
                    }));
            idx++;
        }

        addButton(49, new InventoryButton()
                .creator(ignored -> labeled(Material.BARRIER, ChatColor.RED + "Clear Slot", List.of()))
                .consumer(event -> {
                    abilityService.clearBinding(player, hotbarSlot);
                    guiManager.openGUI(new AbilityBindGUI(guiManager, abilityService), player);
                }));

        addButton(53, new InventoryButton()
                .creator(ignored -> labeled(Material.ARROW, ChatColor.YELLOW + "Back", List.of()))
                .consumer(event -> guiManager.openGUI(new AbilityBindGUI(guiManager, abilityService), player)));

        super.decorate(player);
    }

    private List<BindOption> availableBindings(AbilityPlayerProfile profile) {
        List<BindOption> options = new ArrayList<>();
        for (AbilityDefinition def : abilityService.abilitiesForClass(profile.selectedClass())) {
            if (!def.bindable() && def.kind() != AbilityKind.FORCE_JUMP) {
                continue;
            }
            int unlocked = profile.unlockedLevel(def.id());
            if (unlocked <= 0) {
                continue;
            }
            for (int level = 1; level <= unlocked; level++) {
                if (!def.levels().containsKey(level)) {
                    continue;
                }
                options.add(new BindOption(def, level));
            }
        }
        options.sort(Comparator
                .comparing((BindOption o) -> o.definition().id())
                .thenComparingInt(BindOption::level));
        return options;
    }

    private ItemStack abilityItem(BindOption option) {
        AbilityDefinition definition = option.definition();
        int level = option.level();
        return labeled(Material.LIME_DYE, ChatColor.AQUA + strip(definition.displayName()) + " " + toRoman(level), List.of(
                ChatColor.GRAY + "Bound level: " + ChatColor.AQUA + level,
                ChatColor.GRAY + "Trigger: " + ChatColor.WHITE + triggerLabel(definition),
                "",
                ChatColor.YELLOW + "Click to bind"
        ));
    }

    private String triggerLabel(AbilityDefinition definition) {
        if (definition.kind() == AbilityKind.FORCE_JUMP) {
            return "slot-select jump";
        }
        return "left click";
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

    private String strip(String text) {
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', text));
    }

    private String toRoman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(number);
        };
    }

    private record BindOption(AbilityDefinition definition, int level) {}
}
