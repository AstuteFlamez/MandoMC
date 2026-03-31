package net.mandomc.gameplay.lottery.gui;

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

import net.mandomc.core.modules.core.EconomyModule;
import net.mandomc.core.LangManager;

import java.util.List;
import net.mandomc.gameplay.lottery.LotteryManager;
import net.mandomc.gameplay.lottery.LotteryStorage;

/**
 * Builds lottery purchase dialog.
 */
public class LotteryDialogFactory {

    /**
     * Creates ticket purchase dialog.
     *
     * @param player player opening dialog
     * @param maxAmount max tickets they can buy
     * @param pricePerTicket price per ticket
     * @return dialog instance
     */
    public static Dialog create(Player player, int maxAmount, double pricePerTicket, int maxPerPlayer) {

        return Dialog.create(builder -> builder.empty()

                .base(DialogBase.builder(
                                Component.text("Buy Lottery Tickets", NamedTextColor.GOLD)
                        )

                        .body(List.of(
                                DialogBody.plainMessage(
                                        Component.text("Select amount of tickets", NamedTextColor.GRAY)
                                ),
                                DialogBody.plainMessage(
                                        Component.text("Max: " + maxAmount, NamedTextColor.DARK_GRAY)
                                )
                        ))

                        .inputs(List.of(
                                DialogInput.numberRange(
                                        "amount",
                                        Component.text("Tickets", NamedTextColor.GREEN),
                                        1f,
                                        (float) maxAmount
                                )
                                        .step(1f)
                                        .initial(1f)
                                        .build()
                        ))

                        .build()
                )

                .type(DialogType.confirmation(

                        ActionButton.create(
                                Component.text("Confirm", NamedTextColor.GREEN),
                                Component.text("Purchase tickets"),
                                100,
                                DialogAction.customClick((view, audience) -> {

                                    if (!(audience instanceof Player buyer)) return;

                                    Float amountF = view.getFloat("amount");
                                    if (amountF == null) return;

                                    int amount = amountF.intValue();
                                    double totalCost = amount * pricePerTicket;

                                    int current = LotteryManager.getTickets(buyer.getUniqueId());

                                    if (current + amount > maxPerPlayer) {
                                        buyer.sendMessage(LangManager.get("lottery.cant-buy-many"));
                                        return;
                                    }

                                    if (!EconomyModule.has(buyer, totalCost)) {
                                        buyer.sendMessage(LangManager.get("lottery.not-enough-money"));
                                        return;
                                    }

                                    EconomyModule.withdraw(buyer, totalCost);

                                    for (int i = 0; i < amount; i++) {
                                        LotteryManager.addTicket(buyer.getUniqueId(), pricePerTicket);
                                    }

                                    LotteryStorage.save();

                                    buyer.sendMessage(LangManager.get("lottery.purchased",
                                            "%amount%", String.valueOf(amount),
                                            "%cost%", String.valueOf(totalCost)));
                                    buyer.playSound(buyer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);

                                }, ClickCallback.Options.builder().uses(1).build())
                        ),

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