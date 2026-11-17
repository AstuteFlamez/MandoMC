package net.mandomc.gameplay.ui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.List;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Private, single-use native text input. User input never becomes a command or MiniMessage template. */
public final class TextPrompt {
    private TextPrompt() { }

    public static void open(JavaPlugin plugin, Player owner, Component title, Component label,
            Component submit, String initial, int maximum, BiConsumer<Player, String> action) {
        var id = owner.getUniqueId();
        var callback = DialogAction.customClick((response, audience) -> {
            if (!(audience instanceof Player player) || !player.getUniqueId().equals(id)) return;
            String value = response.getText("query");
            if (value == null || value.length() > maximum) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.isEnabled() && player.isOnline()) action.accept(player, value.strip());
            });
        }, ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(5)).build());
        owner.closeInventory();
        owner.showDialog(Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title).pause(false).canCloseWithEscape(true)
                        .inputs(List.of(DialogInput.text("query", label).initial(initial)
                                .maxLength(maximum).width(300).build())).build())
                .type(DialogType.notice(ActionButton.create(submit, null, 150, callback)))));
    }
}
