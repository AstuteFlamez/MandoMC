package net.mandomc.system.shops;

public class ShopMessages {

    public final String prefix;
    public final String notEnoughMoney;
    public final String purchased;
    public final String inventoryFull;

    public ShopMessages(String prefix, String notEnoughMoney, String purchased, String inventoryFull) {
        this.prefix = prefix;
        this.notEnoughMoney = notEnoughMoney;
        this.purchased = purchased;
        this.inventoryFull = inventoryFull;
    }
}