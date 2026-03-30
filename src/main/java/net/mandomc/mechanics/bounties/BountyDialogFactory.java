package net.mandomc.mechanics.bounties;

import java.util.List;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

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

import net.mandomc.core.LangManager;
import net.mandomc.core.modules.core.EconomyModule;

/**
 * Builds the bounty placement dialog shown when a player selects a target.
 *
 * The dialog allows the player to choose an amount and confirm or cancel.
 * Validation is performed in the confirm callback.
 */
public class BountyDialogFactory {

    /**
     * Creates a bounty dialog for the given placer targeting the given player.
     *
     * @param placer the player placing the bounty
     * @param target the player being bounty-targeted
     * @return the constructed dialog
     */
    public static Dialog create(Player placer, OfflinePlayer target) {
        String targetName = target.getName() == null ? "Unknown" : target.getName();

        return Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(Component.text("Set Bounty on " + targetName, NamedTextColor.RED))
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
                .type(DialogType.confirmation(
                        ActionButton.create(
                                Component.text("Confirm", NamedTextColor.GREEN),
                                Component.text("Place bounty"),
                                100,
                                DialogAction.customClick((view, audience) -> {
                                    if (!(audience instanceof Player player)) return;

                                    double amount = view.getFloat("amount");

                                    if (BountyStorage.hasPlaced(player.getUniqueId())) {
                                        player.sendMessage(LangManager.get("bounties.already-placed"));
                                        return;
                                    }

                                    if (!EconomyModule.has(player, amount)) {
                                        player.sendMessage(LangManager.get("bounties.not-enough-money"));
                                        return;
                                    }

                                    EconomyModule.withdraw(player, amount);

                                    Bounty bounty = BountyStorage.getOrCreate(target.getUniqueId());
                                    bounty.addEntry(player.getUniqueId(), amount);
                                    BountyShowcaseManager.update();

                                    player.getServer().broadcastMessage(
                                            LangManager.get("bounties.placed-broadcast",
                                                    "%target%", targetName,
                                                    "%amount%", EconomyModule.format(bounty.getTotal()))
                                    );

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
