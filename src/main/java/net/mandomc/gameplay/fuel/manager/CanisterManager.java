package net.mandomc.gameplay.fuel.manager;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.mandomc.MandoMC;
import net.mandomc.server.items.ItemUtils;
import net.md_5.bungee.api.ChatColor;

public class CanisterManager {

    private static final NamespacedKey CURRENT_FUEL =
            new NamespacedKey(MandoMC.getInstance(), "current_fuel");

    private static final NamespacedKey MAX_FUEL =
            new NamespacedKey(MandoMC.getInstance(), "max_fuel");

    private static final NamespacedKey MODE =
            new NamespacedKey(MandoMC.getInstance(), "mode");
    
    public static String getMode(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return "depositing";

        PersistentDataContainer container = meta.getPersistentDataContainer();

        return container.getOrDefault(
                MODE,
                PersistentDataType.STRING,
                "depositing"
        );
    }

    public static void switchMode(ItemStack item) {

        if (!ItemUtils.isItem(item, "rhydonium_canister")) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer container = meta.getPersistentDataContainer();

        String current = container.getOrDefault(MODE, PersistentDataType.STRING, "depositing");

        String newMode = current.equals("depositing") ? "refueling" : "depositing";

        container.set(MODE, PersistentDataType.STRING, newMode);

        boolean depositing = newMode.equals("depositing");

        String name = meta.getDisplayName();

        if (name != null) {

            name = name.substring(0, name.length() - 9);

            name = name + color("&6[" + (depositing ? "Inject" : "Extract") + "]");

            meta.setDisplayName(name);
        }

        item.setItemMeta(meta);
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
