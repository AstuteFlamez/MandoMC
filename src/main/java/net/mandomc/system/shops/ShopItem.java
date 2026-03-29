package net.mandomc.system.shops;

import org.bukkit.inventory.ItemStack;

/**
 * Represents a single shop item.
 */
public class ShopItem {

    public enum Type {
        WEAPON_MECHANICS_AMMO,
        WEAPON_MECHANICS_WEAPON,
        VANILLA,
        CUSTOM,
        GENERIC
    }

    private final Type type;
    private final String id;
    private final int amount;
    private final int price;
    private final ItemStack display;

    public ShopItem(Type type, String id, int amount, int price, ItemStack display) {
        this.type = type;
        this.id = id;
        this.amount = amount;
        this.price = price;
        this.display = display;
    }

    public Type getType() { return type; }
    public String getId() { return id; }
    public int getAmount() { return amount; }
    public int getPrice() { return price; }
    public ItemStack getDisplay() { return display; }
}