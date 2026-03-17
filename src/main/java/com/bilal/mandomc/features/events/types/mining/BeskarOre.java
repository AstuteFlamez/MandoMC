package com.bilal.mandomc.features.events.types.mining;

import com.bilal.mandomc.features.items.ItemRegistry;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

public class BeskarOre {

    private final BeskarRushEvent event;
    private final Location location;
    private final Material originalMaterial;

    private boolean mined = false;

    public BeskarOre(BeskarRushEvent event, Location location) {
        this.event = event;
        this.location = location;

        Block block = location.getBlock();
        this.originalMaterial = block.getType();
    }

    public void spawn() {
        location.getBlock().setType(Material.ANCIENT_DEBRIS);
    }

    public void mine(Player player) {

        if (mined) return;

        mined = true;

        location.getBlock().setType(Material.BEDROCK);

        int min = event.getDropMin();
        int max = event.getDropMax();

        int amount = min;

        if (max > min) {
            amount = ThreadLocalRandom.current().nextInt(min, max + 1);
        }

        ItemStack item = ItemRegistry.get("beskar");
        item.setAmount(amount);

        location.getWorld().dropItemNaturally(
                location.clone().add(0.5, 0.5, 0.5),
                item
        );

        event.getActiveTask().oreMined(this);
    }

    public void restore() {
        location.getBlock().setType(originalMaterial);
    }

    public Location getLocation() {
        return location;
    }

    public boolean isMined() {
        return mined;
    }
}