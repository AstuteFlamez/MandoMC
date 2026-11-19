package net.mandomc.integrations.paper;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.mandomc.gameplay.items.ItemCatalog;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Runs Paper's ordinary wire placement transaction while the player holds Electrum Dust. */
public final class ElectrumWirePlacement implements Listener {
    private final ItemCatalog items;
    private final Method playerHandle, worldHandle, asNativeCopy, useOn, consumesAction;
    private final Constructor<?> blockPos, vector, hitResult, useContext;
    private final Class<?> direction;
    private final Object mainHand, offHand;

    public ElectrumWirePlacement(ItemCatalog items) {
        this.items = items;
        try {
            Class<?> player = Class.forName("net.minecraft.world.entity.player.Player");
            Class<?> level = Class.forName("net.minecraft.world.level.Level");
            Class<?> hand = Class.forName("net.minecraft.world.InteractionHand");
            Class<?> stack = Class.forName("net.minecraft.world.item.ItemStack");
            Class<?> use = Class.forName("net.minecraft.world.item.context.UseOnContext");
            Class<?> hit = Class.forName("net.minecraft.world.phys.BlockHitResult");
            Class<?> pos = Class.forName("net.minecraft.core.BlockPos");
            Class<?> vec = Class.forName("net.minecraft.world.phys.Vec3");
            direction = Class.forName("net.minecraft.core.Direction");
            mainHand = hand.getField("MAIN_HAND").get(null);
            offHand = hand.getField("OFF_HAND").get(null);
            playerHandle = Class.forName("org.bukkit.craftbukkit.entity.CraftPlayer").getMethod("getHandle");
            worldHandle = Class.forName("org.bukkit.craftbukkit.CraftWorld").getMethod("getHandle");
            asNativeCopy = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack")
                    .getMethod("asNMSCopy", ItemStack.class);
            useOn = stack.getMethod("useOn", use);
            consumesAction = Class.forName("net.minecraft.world.InteractionResult").getMethod("consumesAction");
            blockPos = pos.getConstructor(int.class, int.class, int.class);
            vector = vec.getConstructor(double.class, double.class, double.class);
            hitResult = hit.getConstructor(vec, direction, pos, boolean.class);
            useContext = use.getConstructor(level, player, hand, stack, hit);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Electrum wire requires the pinned Paper placement API", error);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void place(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.useItemInHand() == Event.Result.DENY
                || !electrum(event.getItem())) return;
        var player = event.getPlayer();
        var block = event.getClickedBlock();
        var slot = event.getHand();
        if (player.getGameMode() == GameMode.SPECTATOR || block == null || slot == null) return;
        // Preserve native container and mechanism interaction unless the player sneaks to place.
        if (block.getType().isInteractable() && !player.isSneaking()
                && event.useInteractedBlock() != Event.Result.DENY) return;
        var held = player.getInventory().getItem(slot);
        if (!electrum(held)) return;
        try {
            var point = event.getInteractionPoint();
            var offset = point == null ? block.getLocation().add(.5, .5, .5) : point;
            var impact = vector.newInstance(offset.getX(), offset.getY(), offset.getZ());
            var pos = blockPos.newInstance(block.getX(), block.getY(), block.getZ());
            var hit = hitResult.newInstance(impact, direction.getField(event.getBlockFace().name()).get(null), pos, false);
            var nativeWire = asNativeCopy.invoke(null, new ItemStack(Material.REDSTONE));
            Object activeHand = slot == EquipmentSlot.OFF_HAND ? offHand : mainHand;
            var context = useContext.newInstance(worldHandle.invoke(block.getWorld()),
                    playerHandle.invoke(player), activeHand, nativeWire, hit);
            if (!(boolean) consumesAction.invoke(useOn.invoke(nativeWire, context))) return;
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);
            if (player.getGameMode() != GameMode.CREATIVE && held.equals(player.getInventory().getItem(slot))) {
                player.getInventory().setItem(slot, held.subtract(1));
            }
            player.swingHand(slot);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Paper Electrum wire placement failed", error);
        }
    }

    private boolean electrum(ItemStack stack) {
        return stack != null && stack.getType() == Material.LAPIS_LAZULI && "electrum_dust".equals(items.identify(stack));
    }
}
