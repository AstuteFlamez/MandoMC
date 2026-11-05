package net.mandomc.gameplay.fuel.listener;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import net.mandomc.gameplay.fuel.manager.BarrelFuelTransferManager;
import net.mandomc.gameplay.fuel.manager.BarrelManager;
import net.mandomc.server.items.ItemUtils;

public class BarrelCanisterInteractListener implements Listener {

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {

        Entity entity = event.getRightClicked();
        if (!(entity instanceof ArmorStand stand)) return;

        if (!stand.getScoreboardTags().contains(BarrelManager.BARREL_TAG)) return;

        tryStartTransfer(event.getPlayer(), stand, event);
    }

    @EventHandler
    public void onInteractBarrier(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        ArmorStand stand = BarrelManager.findBarrelAt(event.getClickedBlock());
        if (stand == null) return;

        tryStartTransfer(event.getPlayer(), stand, event);
    }

    private void tryStartTransfer(Player player, ArmorStand stand, org.bukkit.event.Cancellable event) {
        if (!player.isSneaking()) return;
        if (BarrelFuelTransferManager.isTransferring(player)) return;

        ItemStack canister = player.getInventory().getItemInMainHand();

        if (canister == null) return;
        if (!ItemUtils.hasTag(canister, "FUEL")) return;
        if (!ItemUtils.isItem(canister, "rhydonium_canister")) return;

        ItemStack barrelItem = stand.getEquipment().getHelmet();
        if (barrelItem == null) return;

        event.setCancelled(true);
        BarrelFuelTransferManager.startTransfer(player, canister, stand);
    }
}
