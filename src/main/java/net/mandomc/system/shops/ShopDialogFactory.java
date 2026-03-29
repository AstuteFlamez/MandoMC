package net.mandomc.system.shops;

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

import org.bukkit.Sound;
import org.bukkit.entity.Player;

/**
 * Builds shop purchase dialog.
 */
public class ShopDialogFactory {

    private static final int MAX_PURCHASE = 640;

    /**
     * Creates a purchase dialog for a shop item.
     *
     * @param player the player
     * @param item the shop item
     * @param shop the shop
     * @return dialog instance
     */
    public static Dialog create(Player player, ShopItem item, Shop shop) {

        int pricePerItem = item.getPrice();

        return Dialog.create(builder -> builder.empty()

                .base(DialogBase.builder(
                                Component.text("Purchase Item", NamedTextColor.GOLD)
                        )

                        .body(java.util.List.of(
                                DialogBody.plainMessage(
                                        Component.text("Select amount to purchase", NamedTextColor.GRAY)
                                ),
                                DialogBody.plainMessage(
                                        Component.text("Price: $" + pricePerItem + " each", NamedTextColor.GREEN)
                                ),
                                DialogBody.plainMessage(
                                        Component.text("Max: " + MAX_PURCHASE, NamedTextColor.DARK_GRAY)
                                )
                        ))

                        .inputs(java.util.List.of(
                                DialogInput.numberRange(
                                        "amount",
                                        Component.text("Amount", NamedTextColor.GREEN),
                                        1f,
                                        (float) MAX_PURCHASE
                                )
                                        .step(1f)
                                        .initial(1f)
                                        .build()
                        ))

                        .build()
                )

                .type(DialogType.confirmation(

                        // ✅ CONFIRM BUTTON
                        ActionButton.create(
                                Component.text("Confirm", NamedTextColor.GREEN),
                                Component.text("Purchase item"),
                                100,
                                DialogAction.customClick((view, audience) -> {

                                    if (!(audience instanceof Player p)) return;

                                    Float amountF = view.getFloat("amount");
                                    if (amountF == null) return;

                                    int amount = Math.max(1, Math.min(MAX_PURCHASE, amountF.intValue()));

                                    // 🔥 MAIN PURCHASE LOGIC
                                    ShopPurchaseHandler.purchase(p, item, amount, shop);

                                    // ✅ feedback sound
                                    p.playSound(
                                            p.getLocation(),
                                            Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                                            1f,
                                            1.2f
                                    );

                                }, ClickCallback.Options.builder().uses(1).build())
                        ),

                        // ❌ CANCEL BUTTON
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