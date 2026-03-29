package net.mandomc.system.shops;

import java.util.Map;

public class Shop {

    private final String id;
    private final String title;
    private final int size;
    private final ShopItem filler;
    private final Map<Integer, ShopItem> items;
    private final ShopMessages messages;

    public Shop(String id, String title, int size,
                ShopItem filler,
                Map<Integer, ShopItem> items,
                ShopMessages messages) {
        this.id = id;
        this.title = title;
        this.size = size;
        this.filler = filler;
        this.items = items;
        this.messages = messages;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public int getSize() { return size; }
    public ShopItem getFiller() { return filler; }
    public Map<Integer, ShopItem> getItems() { return items; }
    public ShopMessages getMessages() { return messages; }
}