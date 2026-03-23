package com.astuteflamez.mandomc.core.guis;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Represents a clickable button within a GUI.
 *
 * Defines how the button is displayed and how it behaves
 * when interacted with.
 */
public class InventoryButton {

    /**
     * Function used to generate the button's icon for a player.
     */
    private Function<Player, ItemStack> iconCreator;

    /**
     * Consumer that handles click events for this button.
     */
    private Consumer<InventoryClickEvent> eventConsumer;

    /**
     * Sets the icon creator for this button.
     *
     * @param iconCreator function that creates the button item
     * @return this button instance
     */
    public InventoryButton creator(Function<Player, ItemStack> iconCreator) {
        this.iconCreator = iconCreator;
        return this;
    }

    /**
     * Sets the click handler for this button.
     *
     * @param eventConsumer consumer that handles click events
     * @return this button instance
     */
    public InventoryButton consumer(Consumer<InventoryClickEvent> eventConsumer) {
        this.eventConsumer = eventConsumer;
        return this;
    }

    /**
     * Gets the click event handler.
     *
     * @return the event consumer
     */
    public Consumer<InventoryClickEvent> getEventConsumer() {
        return this.eventConsumer;
    }

    /**
     * Gets the icon creator function.
     *
     * @return the icon creator
     */
    public Function<Player, ItemStack> getIconCreator() {
        return this.iconCreator;
    }
}