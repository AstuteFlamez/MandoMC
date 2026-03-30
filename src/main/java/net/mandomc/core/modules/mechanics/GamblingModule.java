package net.mandomc.core.modules.mechanics;

import net.mandomc.core.module.Module;
import net.mandomc.mechanics.gambling.lottery.LotteryBroadcastTask;
import net.mandomc.mechanics.gambling.lottery.LotteryHologramManager;
import net.mandomc.mechanics.gambling.lottery.LotteryScheduler;
import net.mandomc.mechanics.gambling.lottery.LotteryStorage;
import net.mandomc.mechanics.gambling.lottery.LotteryTopHologramManager;

/**
 * Manages the lifecycle of gambling systems.
 *
 * Currently handles the lottery subsystem: storage, scheduling,
 * broadcast tasks, and holograms.
 */
public class GamblingModule implements Module {

    /**
     * Enables the gambling module.
     *
     * Initializes storage, loads persisted data, starts scheduled tasks,
     * and updates all lottery holograms.
     */
    @Override
    public void enable() {
        LotteryStorage.setup();
        LotteryStorage.load();

        LotteryScheduler.start();
        LotteryBroadcastTask.start();

        LotteryHologramManager.update();
        LotteryTopHologramManager.update();
    }

    /**
     * Disables the gambling module.
     *
     * Saves the current lottery state and removes active holograms.
     */
    @Override
    public void disable() {
        LotteryStorage.save();
        LotteryHologramManager.remove();
    }
}
