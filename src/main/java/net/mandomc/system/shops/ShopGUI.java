package net.mandomc.system.shops;

import net.mandomc.core.guis.InventoryButton;
import net.mandomc.core.guis.InventoryGUI;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventory GUI for a single shop.
 *
 * Fills all slots with the configured filler, then places each {@link ShopItem}
 * button into its configured slot.
 *
 * Left-click → opens a Paper dialog for choosing purchase amount.
 * Shift-left-click → instantly buys one stack (respects item max stack size).
 */
public class ShopGUI extends InventoryGUI {

    private final Shop shop;

    public ShopGUI(Shop shop) {
        this.shop = shop;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, shop.getSize(), color(shop.getTitle()));
    }

    @Override
    public void decorate(Player player) {

        // Fill all slots with the filler button (non-interactive)
        if (shop.getFiller() != null) {
            InventoryButton fillerButton = new InventoryButton()
                    .creator(p -> shop.getFiller().clone())
                    .consumer(event -> event.setCancelled(true));

            for (int i = 0; i < shop.getSize(); i++) {
                addButton(i, fillerButton);
            }
        }

        // Place each shop item button
        shop.getItems().forEach((slot, item) -> addButton(slot, createItemButton(item)));

        super.decorate(player);
    }

    private InventoryButton createItemButton(ShopItem item) {

        // Resolved at GUI-open time — WM is guaranteed loaded by then
        ItemStack icon = buildDisplayItem(item);

        return new InventoryButton()
                .creator(p -> icon)
                .consumer(event -> {

                    if (!(event.getWhoClicked() instanceof Player player)) return;

                    if (event.isShiftClick() && event.isLeftClick()) {
                        // Resolve to get accurate max stack size for shift-click
                        ItemStack base = ShopLoader.resolveItem(item.getType(), item.getId(), shop.getId(), item.getId());
                        int stackSize = base != null ? base.getMaxStackSize() : 1;
                        ShopPurchaseHandler.purchase(player, item, stackSize, shop);
                    } else if (event.isLeftClick()) {
                        player.showDialog(ShopDialogFactory.create(player, item, shop));
                    }
                });
    }

    /**
     * Resolves the base item from its system (WM, registry, vanilla) and
     * applies shop-standard lore:
     *   [first lore line from the source item — rarity/category indicator]
     *   [blank]
     *   Buy: $X each
     *   [Click / Shift-Click hints]
     *
     * For VANILLA items there is no source lore, so the header is skipped.
     */
    private static ItemStack buildDisplayItem(ShopItem item) {

        ItemStack base = ShopLoader.resolveItem(item.getType(), item.getId(), "gui", item.getId());

        if (base == null) {
            // Fallback barrier so the slot isn't empty
            ItemStack barrier = new ItemStack(Material.BARRIER);
            ItemMeta bm = barrier.getItemMeta();
            if (bm != null) {
                bm.setDisplayName("§c" + item.getId());
                bm.setLore(List.of(color("&cItem unavailable")));
                barrier.setItemMeta(bm);
            }
            return barrier;
        }

        ItemStack display = base.clone();
        display.setAmount(1);
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        List<String> lore = new ArrayList<>();

        // Preserve first lore line (rarity/category indicator) for non-vanilla items
        if (item.getType() != ShopItem.Type.VANILLA) {
            List<String> sourceLore = meta.getLore();
            if (sourceLore != null && !sourceLore.isEmpty()) {
                lore.add(sourceLore.get(0));
                lore.add("");
            }
        }

        // Purchase info
        lore.add(color("&7Buy: &a$" + item.getBuyPrice() + " &7each"));
        if (item.getSellPrice() != -1) {
            lore.add(color("&7Sell: &a$" + item.getSellPrice() + " &7each"));
        }
        lore.add("");
        lore.add(color("&eClick &7to choose amount"));
        lore.add(color("&eShift-Click &7to buy a stack"));

        meta.setLore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
