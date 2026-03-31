package net.mandomc.server.shop.model;

/**
 * Represents a single purchasable item in a shop.
 *
 * The {@code amount} field is the bundle unit — e.g. amount=5 means
 * each unit purchased delivers 5 items. The dialog presents units,
 * and {@code totalItems = unitsChosen * amount}.
 *
 * {@code sellPrice} of -1 means the item is not sellable (reserved for future use).
 */
public class ShopItem {

    public enum Type {
        WEAPON_MECHANICS_AMMO,
        WEAPON_MECHANICS_WEAPON,
        VANILLA,
        CUSTOM
    }

    private final Type type;
    private final String id;
    private final int amount;
    private final int buyPrice;
    private final int sellPrice;

    public ShopItem(Type type, String id, int amount, int buyPrice, int sellPrice) {
        this.type = type;
        this.id = id;
        this.amount = amount;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
    }

    public Type getType() { return type; }
    public String getId() { return id; }

    /** Bundle unit size — how many items are delivered per unit purchased. */
    public int getAmount() { return amount; }

    /** Cost per individual item. */
    public int getBuyPrice() { return buyPrice; }

    /** Sell price per item, or -1 if not sellable. */
    public int getSellPrice() { return sellPrice; }

    /**
     * Maximum number of units a player can select in the dialog.
     * Capped so total items never exceeds 640.
     */
    public int getDialogMax() {
        return Math.max(1, 640 / Math.max(1, amount));
    }
}
