package net.mandomc.mechanics.gambling.lottery;

import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import net.mandomc.core.guis.GUIManager;
import net.mandomc.core.guis.InventoryButton;
import net.mandomc.core.guis.InventoryGUI;
import net.mandomc.core.modules.core.EconomyModule;
import net.milkbowl.vault.economy.Economy;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI for interacting with the lottery system.
 *
 * Displays pot, tickets, next draw time, and allows ticket purchase.
 */
public class LotteryGUI extends InventoryGUI {

    private final GUIManager guiManager;
    private final Economy economy = EconomyModule.ECONOMY;

    /**
     * Creates a new Lottery GUI instance.
     *
     * @param guiManager manager responsible for handling GUI interactions
     */
    public LotteryGUI(GUIManager guiManager) {
        this.guiManager = guiManager;
    }

    /**
     * Creates the inventory backing this GUI.
     *
     * @return constructed inventory instance
     */
    @Override
    protected Inventory createInventory() {

        ConfigurationSection cfg = LotteryConfig.get().getConfigurationSection("lottery.gui");
        if (cfg == null) {
            return Bukkit.createInventory(null, 27, "Lottery");
        }

        return Bukkit.createInventory(
                null,
                cfg.getInt("size", 27),
                color(cfg.getString("title", "&6Lottery"))
        );
    }

    /**
     * Populates the GUI with items and buttons.
     *
     * @param player player viewing the GUI
     */
    @Override
    public void decorate(Player player) {

        fillBackground();

        ConfigurationSection items = LotteryConfig.get().getConfigurationSection("lottery.items");
        if (items == null) return;

        addButton(items.getConfigurationSection("pot").getInt("slot"),
                createStaticItem(items.getConfigurationSection("pot"),
                        "%pot%", String.valueOf(LotteryManager.getPot())));

        addButton(items.getConfigurationSection("info").getInt("slot"),
                createStaticItem(items.getConfigurationSection("info"),
                        "%tickets%", String.valueOf(LotteryManager.getTickets(player.getUniqueId()))));

        addButton(items.getConfigurationSection("time").getInt("slot"),
                createStaticItem(items.getConfigurationSection("time"),
                        "%time%", LotteryScheduler.getFormattedNextDraw()));

        addButton(items.getConfigurationSection("buy").getInt("slot"),
                createBuyButton(items.getConfigurationSection("buy"), player));

        super.decorate(player);
    }

    /**
     * Fills empty GUI slots with a background item.
     */
    private void fillBackground() {

        ConfigurationSection section = LotteryConfig.get().getConfigurationSection("lottery.filler");

        Material mat = Material.matchMaterial(section.getString("material", "BLACK_STAINED_GLASS_PANE"));
        ItemStack filler = new ItemStack(mat == null ? Material.BLACK_STAINED_GLASS_PANE : mat);

        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(section.getString("name", " ")));
            meta.setLore(section.getStringList("lore"));
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < getInventory().getSize(); i++) {
            addButton(i, new InventoryButton()
                    .creator(p -> filler)
                    .consumer(e -> {}));
        }
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

                    Player p = (Player) event.getWhoClicked();

                    double price = getPrice();
                    int max = LotteryConfig.get().getInt("lottery.max-tickets-per-player");

                    if (LotteryManager.getTickets(p.getUniqueId()) >= max) {
                        p.sendMessage(prefix("&cYou reached max tickets."));
                        return;
                    }

                    if (!economy.has(p, price)) {
                        p.sendMessage(prefix("&cNot enough money."));
                        return;
                    }

                    economy.withdrawPlayer(p, price);
                    LotteryManager.addTicket(p.getUniqueId(), price);

                    LotteryStorage.save();

                    p.sendMessage(prefix("&aTicket purchased!"));
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);

                    guiManager.openGUI(new LotteryGUI(guiManager), p);
                });
    }

    /**
     * Gets ticket price from config.
     *
     * @return ticket price
     */
    private double getPrice() {
        return LotteryConfig.get().getDouble("lottery.ticket-price");
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

        Material mat = Material.matchMaterial(section.getString("material"));
        ItemStack item = new ItemStack(mat == null ? Material.STONE : mat);

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

    /**
     * Applies color formatting.
     *
     * @param text input text
     * @return formatted string
     */
    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Formats a prefixed message.
     *
     * @param msg message content
     * @return formatted message
     */
    private String prefix(String msg) {
        return color("&6&lLottery &8» " + msg);
    }
}