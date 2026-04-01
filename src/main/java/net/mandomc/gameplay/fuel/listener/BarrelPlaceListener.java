package net.mandomc.gameplay.fuel.listener;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import net.mandomc.core.LangManager;
import net.mandomc.gameplay.fuel.FuelManager;
import net.mandomc.gameplay.fuel.manager.BarrelManager;
import net.mandomc.server.items.ItemRegistry;
import net.mandomc.server.items.ItemUtils;

/**
 * Listens for players placing a rhydonium barrel in the world.
 *
 * When a player right-clicks a block while holding a rhydonium_barrel,
 * an armor stand is spawned at the target block with the barrel model and fuel level.
 * A hologram showing remaining fuel is attached as a passenger.
 */
public class BarrelPlaceListener implements Listener {

    /**
     * Handles placement of a rhydonium barrel on a block.
     *
     * Transfers fuel from the portable item to the placed model, spawns an
     * invisible marker armor stand, creates a hologram, and consumes one item.
     *
     * @param event the player interact event
     */
    @EventHandler
    public void onPlaceBarrel(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        ItemStack model = ItemRegistry.get("rhydonium_barrel_block");

        if (item == null || model == null) return;

        if (!ItemUtils.hasTag(item, "FUEL")) return;
        if (!ItemUtils.isItem(item, "rhydonium_barrel")) return;

        event.setCancelled(true);

        int currentFuel = FuelManager.getCurrentFuel(item);
        FuelManager.updateFuel(model, currentFuel);

        Block anchorBlock = event.getClickedBlock().getRelative(BlockFace.UP);
        Location placeLoc = anchorBlock.getLocation().add(0.5, 0, 0.5);

        ArmorStand stand = placeLoc.getWorld().spawn(placeLoc, ArmorStand.class);

        stand.setInvisible(true);
        stand.setGravity(false);
        stand.setMarker(false);
        stand.setSmall(false);
        stand.setInvulnerable(true);
        stand.addScoreboardTag(BarrelManager.BARREL_TAG);

        if (!BarrelManager.placeBarrier(stand, anchorBlock)) {
            stand.remove();
            return;
        }

        ItemStack placed = model.clone();
        placed = BarrelManager.applyPlacementOffset(placed);
        placed = BarrelManager.updateModel(placed);

        stand.getEquipment().setHelmet(placed);
        BarrelManager.createHologram(stand);

        int amount = item.getAmount();

        if (amount <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(amount - 1);
        }

        player.sendMessage(LangManager.get(
                "fuel.barrel.placed",
                "%current%", String.valueOf(currentFuel),
                "%max%", String.valueOf(FuelManager.getMaxFuel(placed))
        ));
    }
}
