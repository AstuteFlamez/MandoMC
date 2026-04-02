package net.mandomc.gameplay.abilities.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
import net.mandomc.gameplay.abilities.model.AbilityBinding;
import net.mandomc.gameplay.abilities.model.AbilityPlayerProfile;
import net.mandomc.gameplay.abilities.service.AbilityService;

/**
 * GUI for selecting a hotbar slot to configure ability bindings.
 */
public class AbilityBindGUI extends InventoryGUI {
    private static final int SIZE = 27;
    private static final int[] HOTBAR_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};

    private final GUIManager guiManager;
    private final AbilityService abilityService;

    public AbilityBindGUI(GUIManager guiManager, AbilityService abilityService) {
        this.guiManager = guiManager;
        this.abilityService = abilityService;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, SIZE, ChatColor.DARK_PURPLE + "Ability Binds");
    }

    @Override
    public void decorate(Player player) {
        fillBackground();
        AbilityPlayerProfile profile = abilityService.profile(player.getUniqueId());
        for (int slot : HOTBAR_SLOTS) {
            addButton(slot, slotButton(player, slot, profile));
        }

        addButton(22, new InventoryButton()
                .creator(ignored -> labeled(Material.ARROW, ChatColor.YELLOW + "Back to Tree", List.of()))
                .consumer(event -> guiManager.openGUI(new AbilityTreeGUI(guiManager, abilityService), player)));

        super.decorate(player);
    }

    private InventoryButton slotButton(Player player, int hotbarSlot, AbilityPlayerProfile profile) {
        return new InventoryButton()
                .creator(ignored -> slotItem(hotbarSlot, profile))
                .consumer(event -> {
                    guiManager.openGUI(new AbilityBindSlotSelectGUI(guiManager, abilityService, hotbarSlot), player);
                });
    }

    private ItemStack slotItem(int hotbarSlot, AbilityPlayerProfile profile) {
        AbilityBinding current = profile.bindings().get(hotbarSlot);
        String name = ChatColor.AQUA + "Slot " + (hotbarSlot + 1);
        List<String> lore = new ArrayList<>();

        if (current == null) {
            lore.add(ChatColor.GRAY + "Ability: none");
        } else {
            Optional<AbilityDefinition> def = abilityService.definition(current.abilityId());
            lore.add(ChatColor.GRAY + "Ability: " + ChatColor.WHITE + def.map(d -> strip(d.displayName())).orElse(current.abilityId()));
            int selectedLevel = current.selectedLevel() > 0 ? current.selectedLevel() : profile.selectedLevel(current.abilityId());
            lore.add(ChatColor.GRAY + "Bound level: " + ChatColor.AQUA + selectedLevel);
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to choose binding");
        lore.add(ChatColor.DARK_GRAY + "Trigger: left click");

        Material icon = (current == null) ? Material.GRAY_DYE : Material.LIME_DYE;
        return labeled(icon, name, lore);
    }

    private void fillBackground() {
        ItemStack filler = labeled(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
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
}
