package net.mandomc.server.discord.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.entity.Player;

import net.mandomc.core.LangManager;

import java.util.List;

/**
 * Builds the Discord information dialog.
 *
 * Sends a clickable fallback message to the player and returns a dialog
 * with the Discord invite link embedded as a clickable body element.
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

        player.sendMessage(
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                        .legacySection()
                        .deserialize(LangManager.get("discord.join-link", "%link%", DISCORD_LINK))
        );
        return Dialog.create(builder -> builder.empty()

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