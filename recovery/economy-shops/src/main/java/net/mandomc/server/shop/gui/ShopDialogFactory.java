package net.mandomc.server.shop.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import net.mandomc.server.shop.model.Shop;
import net.mandomc.server.shop.model.ShopItem;
import net.mandomc.server.shop.ShopLoader;
import net.mandomc.server.shop.ShopPurchaseHandler;

/**
 * Constructs the Paper API dialog shown when a player left-clicks a shop item.
 *
 * The dialog presents a number range slider letting the player pick between
 * 1 and {@link ShopItem#getDialogMax()} units. One unit equals
 * {@link ShopItem#getAmount()} items. Confirming triggers
 * {@link ShopPurchaseHandler#purchase}.
 */
public final class ShopDialogFactory {

    private ShopDialogFactory() {}

    public static Dialog create(Player player, ShopItem item, Shop shop) {

        int dialogMax = item.getDialogMax();
        int pricePerItem = item.getBuyPrice();
        int pricePerUnit = pricePerItem * item.getAmount();

        ItemStack base = ShopLoader.resolveItem(item.getType(), item.getId(), "dialog", item.getId());

        String itemName = (base != null
                && base.getItemMeta() != null
                && base.getItemMeta().hasDisplayName())
                ? base.getItemMeta().getDisplayName().replaceAll("§[0-9a-fk-orA-FK-OR]", "")
                : item.getId();

        return Dialog.create(builder -> builder.empty()

                .base(DialogBase.builder(
                                Component.text("Purchase Item", NamedTextColor.GOLD)
                        )
                        .body(List.of(
                                DialogBody.plainMessage(
                                        Component.text(itemName, NamedTextColor.WHITE)
                                ),
                                DialogBody.plainMessage(
                                        Component.text("$" + pricePerItem + " per item", NamedTextColor.GREEN)
                                ),
                                item.getAmount() > 1
                                        ? DialogBody.plainMessage(
                                                Component.text("Bundle of " + item.getAmount() + " — $" + pricePerUnit + " per unit", NamedTextColor.GRAY))
                                        : DialogBody.plainMessage(Component.text(" ", NamedTextColor.DARK_GRAY))
                        ))
                        .inputs(List.of(
                                DialogInput.numberRange(
                                        "units",
                                        Component.text("Amount" + (item.getAmount() > 1 ? " (units)" : ""), NamedTextColor.YELLOW),
                                        1f,
                                        (float) dialogMax
                                )
                                        .step(1f)
                                        .initial(1f)
                                        .build()
                        ))
                        .build()
                )

                .type(DialogType.confirmation(

                        // Confirm
                        ActionButton.create(
                                Component.text("Purchase", NamedTextColor.GREEN),
                                Component.text("Confirm purchase"),
                                100,
                                DialogAction.customClick((view, audience) -> {

                                    if (!(audience instanceof Player buyer)) return;

                                    Float unitsF = view.getFloat("units");
                                    if (unitsF == null) return;

                                    int units = Math.max(1, Math.min(dialogMax, unitsF.intValue()));
                                    int totalItems = units * item.getAmount();

                                    ShopPurchaseHandler.purchase(buyer, item, totalItems, shop);

                                }, ClickCallback.Options.builder().uses(1).build())
                        ),

                        // Cancel
                        ActionButton.create(
                                Component.text("Cancel", NamedTextColor.RED),
                                Component.text("Close"),
                                100,
                                null
                        )
                ))
        );
    }
}
