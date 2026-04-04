package net.mandomc.server.items.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import net.mandomc.core.LangManager;
import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.guis.InventoryButton;
import net.mandomc.core.guis.InventoryGUI;
import net.mandomc.server.items.ItemRegistry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * GUI for browsing registered items via shared core GUI system.
 */
public class ItemBrowserGUI extends InventoryGUI {

    private static final int SIZE = 54;
    private static final int SLOT_BACK = 0;
    private static final int SLOT_HEADER = 4;
    private static final int SLOT_PREVIOUS = 45;
    private static final int SLOT_NEXT = 53;
    private static final int PAGE_SIZE = 36;
    private static final Map<String, Integer> RARITY_RANKS = createRarityRanks();
    private static final int[] CONTENT_SLOTS = {
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final Comparator<String> ITEM_ORDER = Comparator
            .comparingInt((String id) -> rarityRank(ItemRegistry.getRarity(id))).reversed()
            .thenComparing(id -> id.toLowerCase(Locale.ROOT));
    private static final Comparator<String> CATEGORY_ORDER =
            Comparator.comparing(id -> id.toLowerCase(Locale.ROOT));

    private final GUIManager guiManager;
    private final Player target;
    private final String category;
    private final int page;
    private final Mode mode;

    private enum Mode {
        CATEGORIES,
        ITEMS
    }

    private ItemBrowserGUI(GUIManager guiManager, Player target, String category, int page, Mode mode) {
        this.guiManager = guiManager;
        this.target = target;
        this.category = category;
        this.page = page;
        this.mode = mode;
    }

    public static ItemBrowserGUI categories(GUIManager guiManager, Player target) {
        return new ItemBrowserGUI(guiManager, target, null, 0, Mode.CATEGORIES);
    }

    public static ItemBrowserGUI categories(GUIManager guiManager, Player target, int page) {
        return new ItemBrowserGUI(guiManager, target, null, page, Mode.CATEGORIES);
    }

    public static ItemBrowserGUI items(GUIManager guiManager, Player target, String category, int page) {
        return new ItemBrowserGUI(guiManager, target, category, page, Mode.ITEMS);
    }

    @Override
    protected Inventory createInventory() {
        String rawTitle = mode == Mode.CATEGORIES
                ? LangManager.get("items.browser.title-categories")
                : LangManager.get("items.browser.title-items");
        String title = ChatColor.WHITE + ChatColor.stripColor(rawTitle);
        return Bukkit.createInventory(null, SIZE, title);
    }

    @Override
    public void decorate(Player viewer) {
        fillBackground();

        if (mode == Mode.CATEGORIES) {
            decorateCategories();
        } else {
            decorateItems();
        }

        super.decorate(viewer);
    }

    private void decorateCategories() {
        addButton(SLOT_HEADER, new InventoryButton()
                .creator(p -> createItem(Material.BOOK, LangManager.get("items.browser.select-category")))
                .consumer(event -> {}));

        List<String> categories = getCategoriesWithAll();
        int clamped = clampPage(page, categories.size());
        int start = clamped * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, categories.size());

        int contentIndex = 0;
        for (int i = start; i < end; i++) {
            String categoryValue = categories.get(i);
            String display = categoryValue == null ? LangManager.get("items.browser.all-name") : categoryValue;
            int slot = CONTENT_SLOTS[contentIndex++];

            addButton(slot, new InventoryButton()
                    .creator(p -> createItem(Material.PAPER, "§e" + display))
                    .consumer(event -> {
                        Player clicker = (Player) event.getWhoClicked();
                        guiManager.openGUI(items(guiManager, target, categoryValue, 0), clicker);
                    }));
        }

        addPaginationButtons(clamped, categories.size(), true);
    }

    private void decorateItems() {
        String filter = category == null
                ? LangManager.get("items.browser.all-name")
                : category;

        addButton(SLOT_BACK, new InventoryButton()
                .creator(p -> createItem(Material.BARRIER, LangManager.get("items.browser.back-categories")))
                .consumer(event -> {
                    Player clicker = (Player) event.getWhoClicked();
                    guiManager.openGUI(categories(guiManager, target), clicker);
                }));

        addButton(SLOT_HEADER, new InventoryButton()
                .creator(p -> createItem(
                        Material.BOOK,
                        LangManager.get("items.browser.current-filter", "%category%", filter)
                ))
                .consumer(event -> {}));

        List<String> itemIds = getItems(category);
        int clamped = clampPage(page, itemIds.size());
        int start = clamped * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, itemIds.size());

        int contentIndex = 0;
        for (int i = start; i < end; i++) {
            String itemId = itemIds.get(i);
            int slot = CONTENT_SLOTS[contentIndex++];

            addButton(slot, new InventoryButton()
                    .creator(p -> {
                        ItemStack icon = ItemRegistry.get(itemId);
                        return icon != null ? icon : createItem(Material.BARRIER, "§c" + itemId);
                    })
                    .consumer(event -> {
                        Player clicker = (Player) event.getWhoClicked();
                        ItemStack toGive = ItemRegistry.get(itemId);
                        if (toGive == null) {
                            clicker.sendMessage(LangManager.get("items.unknown-item", "%id%", itemId));
                            return;
                        }

                        target.getInventory().addItem(toGive);
                        clicker.sendMessage(LangManager.get("items.gave-via-browser", "%player%", target.getName()));
                    }));
        }

        addPaginationButtons(clamped, itemIds.size(), false);
    }

    private void addPaginationButtons(int currentPage, int totalSize, boolean categoryMode) {
        if (currentPage > 0) {
            addButton(SLOT_PREVIOUS, new InventoryButton()
                    .creator(p -> createItem(Material.ARROW, LangManager.get("items.browser.prev-page")))
                    .consumer(event -> {
                        Player clicker = (Player) event.getWhoClicked();
                        if (categoryMode) {
                            guiManager.openGUI(categories(guiManager, target, currentPage - 1), clicker);
                        } else {
                            guiManager.openGUI(items(guiManager, target, category, currentPage - 1), clicker);
                        }
                    }));
        }

        if ((currentPage + 1) * PAGE_SIZE < totalSize) {
            addButton(SLOT_NEXT, new InventoryButton()
                    .creator(p -> createItem(Material.ARROW, LangManager.get("items.browser.next-page")))
                    .consumer(event -> {
                        Player clicker = (Player) event.getWhoClicked();
                        if (categoryMode) {
                            guiManager.openGUI(categories(guiManager, target, currentPage + 1), clicker);
                        } else {
                            guiManager.openGUI(items(guiManager, target, category, currentPage + 1), clicker);
                        }
                    }));
        }
    }

    private void fillBackground() {
        ItemStack pane = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < getInventory().getSize(); i++) {
            addButton(i, new InventoryButton()
                    .creator(player -> pane)
                    .consumer(event -> {}));
        }
    }

    private static int clampPage(int requestedPage, int totalItems) {
        int maxPage = totalItems == 0 ? 0 : (totalItems - 1) / PAGE_SIZE;
        if (requestedPage < 0) return 0;
        return Math.min(requestedPage, maxPage);
    }

    static int rarityRank(String rarity) {
        if (rarity == null) {
            return 0;
        }
        return RARITY_RANKS.getOrDefault(rarity.toLowerCase(Locale.ROOT), 0);
    }

    static List<String> sortItemIds(List<String> ids) {
        ids.sort(ITEM_ORDER);
        return ids;
    }

    private static Map<String, Integer> createRarityRanks() {
        Map<String, Integer> ranks = new HashMap<>();
        ranks.put("common", 1);
        ranks.put("uncommon", 2);
        ranks.put("rare", 3);
        ranks.put("epic", 4);
        ranks.put("legendary", 5);
        ranks.put("mythic", 6);
        return ranks;
    }

    private static List<String> getCategoriesWithAll() {
        List<String> categories = new ArrayList<>(ItemRegistry.getCategories());
        categories.sort(CATEGORY_ORDER);

        List<String> withAll = new ArrayList<>();
        withAll.add(null);
        withAll.addAll(categories);
        return withAll;
    }

    private static List<String> getItems(String category) {
        List<String> ids = new ArrayList<>();
        for (String id : ItemRegistry.getItemIds()) {
            if (category == null || category.equals(ItemRegistry.getCategory(id))) {
                ids.add(id);
            }
        }
        return sortItemIds(ids);
    }

    private static ItemStack createItem(Material material, String name) {

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }

        return item;
    }
}