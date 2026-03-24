package net.mandomc.system.planets.ilum;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import net.mandomc.MandoMC;

public class ParkourItemFactory {

    private static final NamespacedKey KEY =
            new NamespacedKey(MandoMC.getInstance(), "parkour_item");

    private static ItemStack createItem(Material material, String name, String type) {

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) return item;

        meta.setDisplayName(name);

        meta.getPersistentDataContainer().set(
                KEY,
                PersistentDataType.STRING,
                type
        );

        item.setItemMeta(meta);

        return item;
    }

    public static ItemStack createLeaveItem() {
        return createItem(
                Material.BARRIER,
                "§cLeave Parkour",
                "leave"
        );
    }

    public static ItemStack createCheckpointItem() {
        return createItem(
                Material.COMPASS,
                "§eLast Checkpoint",
                "checkpoint"
        );
    }

    public static ItemStack createRestartItem() {
        return createItem(
                Material.RED_BED,
                "§aRestart Course",
                "restart"
        );
    }

}