package net.mandomc.system.discord;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * Builds the Discord dialog.
 */
public class DiscordDialogFactory {

    private static final String DISCORD_LINK = "https://discord.gg/bruuZCG5Pk";

    /**
     * Creates the Discord dialog.
     *
     * @param player the player viewing the dialog
     * @return dialog instance
     */
    public static Dialog create(Player player) {

        // 🔗 Send fallback clickable message immediately
        player.sendMessage(
                Component.text("Click here to join: ", NamedTextColor.GRAY)
                        .append(Component.text(DISCORD_LINK, NamedTextColor.AQUA)
                                .clickEvent(ClickEvent.openUrl(DISCORD_LINK)))
        );

        return Dialog.create(builder -> builder.empty()

                // =========================
                // 🧱 BASE
                // =========================
                .base(DialogBase.builder(
                                Component.text("Join Our Discord", NamedTextColor.AQUA)
                        )

                        .body(List.of(
                                DialogBody.plainMessage(
                                        Component.text("Join the community!", NamedTextColor.GRAY)
                                ),
                                DialogBody.plainMessage(
                                        Component.text("Click the link below:", NamedTextColor.DARK_GRAY)
                                ),
                                DialogBody.plainMessage(
                                        Component.text("discord.gg/bruuZCG5Pk", NamedTextColor.BLUE)
                                                .clickEvent(ClickEvent.openUrl(DISCORD_LINK))
                                )
                        ))

                        .build()
                )

                // =========================
                // 🔘 BUTTONS (visual only)
                // =========================
                .type(DialogType.confirmation(

                        ActionButton.create(
                                Component.text("Join Discord", NamedTextColor.GREEN),
                                Component.text("Open link above"),
                                100,
                                null
                        ),

                        ActionButton.create(
                                Component.text("Close", NamedTextColor.RED),
                                Component.text("Exit"),
                                100,
                                null
                        )
                ))
        );
    }
}