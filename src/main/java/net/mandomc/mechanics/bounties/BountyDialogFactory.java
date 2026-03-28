package net.mandomc.mechanics.bounties;

import io.papermc.paper.dialog.*;
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

import net.mandomc.core.modules.core.EconomyModule;

import java.util.List;

/**
 * Builds bounty dialogs.
 */
public class BountyDialogFactory {

    public static Dialog create(Player placer, Player target) {

        return Dialog.create(builder -> builder.empty()

                // =========================
                // 🧱 BASE
                // =========================
                .base(DialogBase.builder(Component.text("Set Bounty on " + target.getName(), NamedTextColor.RED))

                        .body(List.of(
                                DialogBody.plainMessage(Component.text("Choose bounty amount", NamedTextColor.GRAY))
                        ))

                        .inputs(List.of(
                                DialogInput.numberRange(
                                        "amount",
                                        Component.text("Bounty Amount", NamedTextColor.GREEN),
                                        100f,
                                        100000f
                                )
                                        .step(100f)
                                        .initial(1000f)
                                        .build()
                        ))

                        .build()
                )

                // =========================
                // ✅ BUTTONS
                // =========================
                .type(DialogType.confirmation(

                        // CONFIRM
                        ActionButton.create(
                                Component.text("Confirm", NamedTextColor.GREEN),
                                Component.text("Place bounty"),
                                100,
                                DialogAction.customClick((view, audience) -> {

                                    if (!(audience instanceof Player p)) return;

                                    float amountF = view.getFloat("amount");
                                    double amount = amountF;

                                    // 🔒 VALIDATION
                                    if (BountyStorage.hasPlaced(p.getUniqueId())) {
                                        p.sendMessage("§cYou already placed a bounty.");
                                        return;
                                    }

                                    if (!EconomyModule.has(p, amount)) {
                                        p.sendMessage("§cNot enough money.");
                                        return;
                                    }

                                    // 💸 withdraw
                                    EconomyModule.withdraw(p, amount);

                                    // 📦 store
                                    Bounty bounty = BountyStorage.getOrCreate(target.getUniqueId());
                                    bounty.addEntry(p.getUniqueId(), amount);

                                    // 📢 broadcast
                                    p.getServer().broadcastMessage(
                                            "§c⚠ " + target.getName()
                                                    + " has a bounty of $" + EconomyModule.format(bounty.getTotal())
                                    );

                                }, ClickCallback.Options.builder().uses(1).build())
                        ),

                        // CANCEL
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