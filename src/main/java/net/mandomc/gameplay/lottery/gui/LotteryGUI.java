package net.mandomc.gameplay.lottery.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.mandomc.gameplay.lottery.config.LotteryConfig;
import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.guis.InventoryButton;
import net.mandomc.core.guis.InventoryGUI;
import net.mandomc.core.modules.core.EconomyModule;
import net.mandomc.core.LangManager;
import net.mandomc.server.shop.gui.ShopGUI;
import net.milkbowl.vault.economy.Economy;

import java.util.ArrayList;
import java.util.List;
import net.mandomc.gameplay.lottery.LotteryManager;
import net.mandomc.gameplay.lottery.task.LotteryScheduler;

/**
 * GUI for interacting with the lottery system.
 *
 * Displays pot, tickets, next draw time, and allows ticket purchase.
 */
public class LotteryGUI extends InventoryGUI {

    private static final String LOTTERY_TITLE = ShopGUI.SHOP_TITLE.substring(0, ShopGUI.SHOP_TITLE.length() - 1) + "Ĺ";
    private static final int BLANK_MODEL_DATA = 5;

    private final GUIManager guiManager;
    private final LotteryConfig lotteryConfig;
    private final Economy economy = EconomyModule.get();

    /**
     * Creates a new Lottery GUI instance.
     *
     * @param guiManager manager responsible for handling GUI interactions
     * @param lotteryConfig typed lottery configuration
     */
    public LotteryGUI(GUIManager guiManager, LotteryConfig lotteryConfig) {
        this.guiManager = guiManager;
        this.lotteryConfig = lotteryConfig;
    }

    /**
     * Creates the inventory backing this GUI.
     *
     * @return constructed inventory instance
     */
    @Override
    protected Inventory createInventory() {
        ConfigurationSection cfg = lotteryConfig.getSection("lottery.gui");
        int size = cfg == null ? 27 : cfg.getInt("size", 27);
        String whiteTitle = ChatColor.WHITE + ChatColor.stripColor(color(LOTTERY_TITLE));
        return Bukkit.createInventory(
                null,
                size,
                whiteTitle
        );
    }

    /**
     * Populates the GUI with items and buttons.
     *
     * @param player player viewing the GUI
     */
    @Override
    public void decorate(Player player) {
        ConfigurationSection items = lotteryConfig.getSection("lottery.items");
        if (items == null) return;

        ConfigurationSection potSection = items.getConfigurationSection("pot");
        if (potSection != null) {
            addButton(potSection.getInt("slot"),
                    createStaticItem(potSection, "%pot%", String.valueOf(LotteryManager.getPot())));
        }

        ConfigurationSection infoSection = items.getConfigurationSection("info");
        if (infoSection != null) {
            addButton(infoSection.getInt("slot"),
                    createStaticItem(infoSection, "%tickets%", String.valueOf(LotteryManager.getTickets(player.getUniqueId()))));
        }

        ConfigurationSection timeSection = items.getConfigurationSection("time");
        if (timeSection != null) {
            addButton(timeSection.getInt("slot"),
                    createStaticItem(timeSection, "%time%", LotteryScheduler.getFormattedNextDraw()));
        }

        ConfigurationSection buySection = items.getConfigurationSection("buy");
        if (buySection != null) {
            addButton(buySection.getInt("slot"),
                    createBuyButton(buySection, player));
        }

        super.decorate(player);
    }

    /**
     * Creates a non-clickable display item.
     *
     * @param section config section for item
     * @param placeholder placeholder to replace
     * @param value value to insert
     * @return configured inventory button
     */
    private InventoryButton createStaticItem(ConfigurationSection section, String placeholder, String value) {
        return new InventoryButton()
                .creator(p -> buildItem(section, placeholder, value))
                .consumer(e -> {});
    }

    /**
     * Creates the buy ticket button.
     *
     * @param section config section
     * @param player player viewing GUI
     * @return configured inventory button
     */
    private InventoryButton createBuyButton(ConfigurationSection section, Player player) {

        return new InventoryButton()
                .creator(p -> buildItem(section, "%price%", String.valueOf(getPrice())))
                .consumer(event -> {

                    Player clicker = (Player) event.getWhoClicked();

                    int max = lotteryConfig.getMaxTicketsPerPlayer();
                    int current = LotteryManager.getTickets(clicker.getUniqueId());

                    if (current >= max) {
                        clicker.sendMessage(LangManager.get("lottery.max-tickets"));
                        return;
                    }

                    int remaining = max - current;

                    clicker.showDialog(LotteryDialogFactory.create(clicker, remaining, getPrice(), max));
                });
    }

    /**
     * Gets ticket price from config.
     *
     * @return ticket price
     */
    private double getPrice() {
        return lotteryConfig.getTicketPrice();
    }

    /**
     * Builds an item from config with placeholder replacement.
     *
     * @param section config section
     * @param placeholder placeholder to replace
     * @param value replacement value
     * @return constructed item stack
     */
    private ItemStack buildItem(ConfigurationSection section, String placeholder, String value) {
        if (section == null) {
            return createBlankItem();
        }

        ItemStack item = createBlankItem();

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(color(section.getString("name")));

        List<String> lore = new ArrayList<>();
        for (String line : section.getStringList("lore")) {
            lore.add(color(line.replace(placeholder, value)));
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    private static ItemStack createBlankItem() {
        ItemStack item = new ItemStack(Material.FLINT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(BLANK_MODEL_DATA);
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Applies color formatting.
     *
     * @param text input text
     * @return formatted string
     */
    private String color(String text) {
        if (text == null) {
            return "";
        }
        return LangManager.colorize(text);
    }
}