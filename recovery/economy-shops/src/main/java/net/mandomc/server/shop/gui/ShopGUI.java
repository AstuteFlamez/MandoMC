package net.mandomc.server.shop.gui;

import net.mandomc.core.LangManager;
import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.guis.InventoryButton;
import net.mandomc.core.guis.InventoryGUI;
import net.mandomc.core.modules.core.EconomyModule;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import net.mandomc.server.shop.model.Shop;
import net.mandomc.server.shop.model.ShopItem;
import net.mandomc.server.shop.ShopLoader;
import net.mandomc.server.shop.ShopPurchaseHandler;

/**
 * Inventory GUI for a single shop.
 *
 * Uses a fixed title and paginated content slots to place each {@link ShopItem}
 * in config order.
 */
public class ShopGUI extends InventoryGUI {

    public static final String SHOP_TITLE = "&fĬ";

    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };
    private static final int SLOT_BACK = 18;
    private static final int SLOT_NEXT = 26;
    private static final int SLOT_BALANCE = 49;
    private static final int NEXT_MODEL_DATA = 1;
    private static final int BACK_MODEL_DATA = 2;
    private static final int BALANCE_MODEL_DATA = 5;

    private final Shop shop;
    private final GUIManager guiManager;
    private final int page;

    public ShopGUI(Shop shop, GUIManager guiManager) {
        this(shop, guiManager, 0);
    }

    public ShopGUI(Shop shop, GUIManager guiManager, int page) {
        this.shop = shop;
        this.guiManager = guiManager;
        this.page = page;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, shop.getSize(), color(SHOP_TITLE));
    }

    @Override
    public void decorate(Player player) {

        int[] usableSlots = usableContentSlots();
        int pageSize = usableSlots.length;
        int totalItems = shop.getItems().size();
        int currentPage = clampPage(page, totalItems, pageSize);
        int start = currentPage * pageSize;
        int end = Math.min(start + pageSize, totalItems);

        int contentIndex = 0;
        for (int i = start; i < end; i++) {
            int slot = usableSlots[contentIndex++];
            ShopItem item = shop.getItems().get(i);
            addButton(slot, createItemButton(item, currentPage));
        }

        if (SLOT_BACK < shop.getSize() && currentPage > 0) {
            addButton(SLOT_BACK, createPageButton(currentPage - 1, BACK_MODEL_DATA));
        }

        if (SLOT_NEXT < shop.getSize() && end < totalItems) {
            addButton(SLOT_NEXT, createPageButton(currentPage + 1, NEXT_MODEL_DATA));
        }

        if (SLOT_BALANCE < shop.getSize()) {
            addButton(SLOT_BALANCE, createBalanceButton());
        }

        super.decorate(player);
    }

    private InventoryButton createItemButton(ShopItem item, int currentPage) {

        // Resolved at GUI-open time — WM is guaranteed loaded by then
        ItemStack icon = buildDisplayItem(item);

        return new InventoryButton()
                .creator(p -> icon)
                .consumer(event -> {
                    if (!(event.getWhoClicked() instanceof Player player)) return;
                    if (event.isShiftClick() && event.isLeftClick()) {
                        ItemStack base = ShopLoader.resolveItem(item.getType(), item.getId(), shop.getId(), item.getId());
                        int stackSize = base != null ? base.getMaxStackSize() : 1;
                        ShopPurchaseHandler.purchase(player, item, stackSize, shop);
                        return;
                    }

                    if (event.isLeftClick()) {
                        guiManager.openGUI(new ShopQuantityGUI(shop, item, guiManager, currentPage), player);
                    }
                });
    }

    private InventoryButton createPageButton(int targetPage, int customModelData) {
        return new InventoryButton()
                .creator(p -> {
                    ItemStack button = new ItemStack(Material.FLINT);
                    ItemMeta meta = button.getItemMeta();
                    if (meta != null) {
                        String name = customModelData == NEXT_MODEL_DATA ? "&9Next Page" : "&9Previous Page";
                        meta.setDisplayName(color(name));
                        meta.setCustomModelData(customModelData);
                        button.setItemMeta(meta);
                    }
                    return button;
                })
                .consumer(event -> {
                    if (!(event.getWhoClicked() instanceof Player player)) {
                        return;
                    }
                    guiManager.openGUI(new ShopGUI(shop, guiManager, targetPage), player);
                });
    }

    private InventoryButton createBalanceButton() {
        return new InventoryButton()
                .creator(p -> {
                    ItemStack button = new ItemStack(Material.FLINT);
                    ItemMeta meta = button.getItemMeta();
                    if (meta != null) {
                        meta.setCustomModelData(BALANCE_MODEL_DATA);
                        meta.setDisplayName(color("&aBalance: &f$" + EconomyModule.format(EconomyModule.getBalance(p))));
                        button.setItemMeta(meta);
                    }
                    return button;
                })
                .consumer(event -> event.setCancelled(true));
    }

    private int[] usableContentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot : CONTENT_SLOTS) {
            if (slot < shop.getSize()) {
                slots.add(slot);
            }
        }
        if (slots.isEmpty()) {
            return new int[0];
        }

        int[] result = new int[slots.size()];
        for (int i = 0; i < slots.size(); i++) {
            result[i] = slots.get(i);
        }
        return result;
    }

    private static int clampPage(int requestedPage, int totalItems, int pageSize) {
        if (pageSize <= 0) {
            return 0;
        }
        int maxPage = totalItems == 0 ? 0 : (totalItems - 1) / pageSize;
        if (requestedPage < 0) {
            return 0;
        }
        return Math.min(requestedPage, maxPage);
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
        lore.add(color("&eClick &7to open quantity selector"));

        meta.setLore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private static String color(String text) {
        return LangManager.colorize(text);
    }
}
