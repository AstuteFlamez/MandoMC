package net.mandomc.core.modules.mechanics;

import net.mandomc.core.module.Module;
import net.mandomc.mechanics.gambling.lottery.*;

/**
 * Module responsible for initializing and managing gambling systems.
 *
 * Currently handles:
 * - Lottery storage lifecycle
 * - Lottery scheduler and broadcasts
 * - Lottery holograms
 */
public class GamblingModule implements Module {

    /**
     * Enables the gambling module.
     *
     * Initializes storage, loads persisted data,
     * starts scheduled tasks, and updates holograms.
     */
    @Override
    public void enable() {

        // Initialize and load stored lottery data
        LotteryStorage.setup();
        LotteryStorage.load();

        // Start scheduler and broadcast systems
        LotteryScheduler.start();
        LotteryBroadcastTask.start();

        // Initialize holograms
        LotteryHologramManager.update();
        LotteryTopHologramManager.update();
    }

    /**
     * Disables the gambling module.
     *
     * Saves current lottery state and removes active holograms.
     */
    @Override
    public void disable() {

        // Persist data
        LotteryStorage.save();

        // Clean up holograms
        LotteryHologramManager.remove();
    }
}