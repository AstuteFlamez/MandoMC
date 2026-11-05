package net.mandomc.server.shop.gui;

import net.mandomc.core.guis.InventoryHandler;
import net.mandomc.core.modules.core.EconomyModule;
import net.mandomc.server.shop.ShopSellService;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.mandomc.core.LangManager;
import net.mandomc.server.shop.gui.ShopGUI;

import java.util.ArrayList;
import java.util.List;

/**
 * A free-form 6-row sell inventory.
 *
 * Players drag in any items they want to sell. On close:
 *  - Items matching a shop entry with sell_price > 0 are sold for money.
 *  - All other items are returned to the player (dropped at feet if inventory full).
 *
 * Does NOT extend InventoryGUI — player must be able to freely place items.
 * Registered via {@link net.mandomc.core.guis.GUIManager#registerHandledInventory}.
 */
public class ShopSellGUI implements InventoryHandler {

    private static final int SIZE = 54;
    private static final int SLOT_INFO = 49;
    private static final int INFO_MODEL_DATA = 5;
    private static final int[] SELL_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final Inventory inventory;

    public ShopSellGUI(Player target) {
        this.inventory = Bukkit.createInventory(null, SIZE, ("§f" + pawnTitle()));
        this.inventory.setItem(SLOT_INFO, createInfoItem());
    }

    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        if (event.getClickedInventory() == null) {
            return;
        }

        int rawSlot = event.getRawSlot();
        boolean topInventory = rawSlot < inventory.getSize();

        if (topInventory) {
            if (!isSellSlot(rawSlot)) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            event.setCancelled(true);
            moveShiftClickedItemToSellSlots(event);
        }
    }

    @Override
    public void onOpen(InventoryOpenEvent event) {}

    @Override
    public void onDrag(InventoryDragEvent event) {
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= inventory.getSize()) {
                continue;
            }
            if (!isSellSlot(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {

        if (!(event.getPlayer() instanceof Player player)) return;

        List<ItemStack> sellItems = new ArrayList<>();
        for (int slot : SELL_SLOTS) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null && stack.getType() != Material.AIR) {
                sellItems.add(stack.clone());
            }
        }

        ShopSellService.SellResult result = ShopSellService.processInventory(sellItems);

        // Return unsellable items; drop at feet if inventory is full
        for (ItemStack item : result.unsellable()) {
            var leftover = player.getInventory().addItem(item);
            leftover.values().forEach(dropped ->
                    player.getWorld().dropItemNaturally(player.getLocation(), dropped)
            );
        }

        if (result.totalSold() == 0) {
            player.sendMessage(LangManager.get("shops.no-sellable"));
            return;
        }

        EconomyModule.deposit(player, result.totalProfit());

        player.sendMessage(LangManager.get("shops.sold", "%amount%", String.valueOf(result.totalSold()), "%profit%", EconomyModule.format(result.totalProfit())));
    }

    private void moveShiftClickedItemToSellSlots(InventoryClickEvent event) {
        ItemStack source = event.getCurrentItem();
        if (source == null || source.getType() == Material.AIR) {
            return;
        }

        ItemStack moving = source.clone();
        int originalAmount = moving.getAmount();

        for (int slot : SELL_SLOTS) {
            ItemStack existing = inventory.getItem(slot);
            if (existing == null || existing.getType() == Material.AIR) {
                continue;
            }
            if (!existing.isSimilar(moving)) {
                continue;
            }

            int max = existing.getMaxStackSize();
            int space = max - existing.getAmount();
            if (space <= 0) {
                continue;
            }

            int transfer = Math.min(space, moving.getAmount());
            existing.setAmount(existing.getAmount() + transfer);
            moving.setAmount(moving.getAmount() - transfer);
            if (moving.getAmount() <= 0) {
                break;
            }
        }

        if (moving.getAmount() > 0) {
            for (int slot : SELL_SLOTS) {
                ItemStack existing = inventory.getItem(slot);
                if (existing != null && existing.getType() != Material.AIR) {
                    continue;
                }

                ItemStack placed = moving.clone();
                int transfer = Math.min(placed.getMaxStackSize(), moving.getAmount());
                placed.setAmount(transfer);
                inventory.setItem(slot, placed);
                moving.setAmount(moving.getAmount() - transfer);
                if (moving.getAmount() <= 0) {
                    break;
                }
            }
        }

        int moved = originalAmount - moving.getAmount();
        if (moved <= 0) {
            return;
        }

        if (moving.getAmount() <= 0) {
            event.getClickedInventory().setItem(event.getSlot(), null);
        } else {
            source.setAmount(moving.getAmount());
            event.getClickedInventory().setItem(event.getSlot(), source);
        }
    }

    private static boolean isSellSlot(int slot) {
        for (int candidate : SELL_SLOTS) {
            if (candidate == slot) {
                return true;
            }
        }
        return false;
    }

    private static ItemStack createInfoItem() {
        ItemStack info = new ItemStack(Material.FLINT);
        ItemMeta meta = info.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(INFO_MODEL_DATA);
            meta.setDisplayName(LangManager.colorize("&6Pawn Shop Info"));
            meta.setLore(List.of(
                    LangManager.colorize("&7Drop items into the sell slots"),
                    LangManager.colorize("&7to automatically sell them on close.")
            ));
            info.setItemMeta(meta);
        }
        return info;
    }

    private static String pawnTitle() {
        String base = ShopGUI.SHOP_TITLE;
        String withoutLast = base.substring(0, base.length() - 1);
        String shortened = removeGlyphOccurrences(withoutLast, '', 11);
        if (!shortened.startsWith("&f")) {
            shortened = "&f" + shortened.replaceFirst("^&[0-9a-fk-or]", "");
        }
        return shortened + "Į";
    }

    private static String removeGlyphOccurrences(String text, char glyph, int removeCount) {
        StringBuilder sb = new StringBuilder(text.length());
        int removed = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == glyph && removed < removeCount) {
                removed++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
