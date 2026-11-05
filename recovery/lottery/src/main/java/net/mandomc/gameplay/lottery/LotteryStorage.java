package net.mandomc.gameplay.lottery;

import java.util.HashMap;
import java.util.UUID;
import net.mandomc.gameplay.lottery.storage.LotteryRepository;
import net.mandomc.gameplay.lottery.storage.LotteryState;

/**
 * Handles persistence of lottery data.
 *
 * Stores and loads the current pot and player tickets
 * to a JSON file located in the gambling folder.
 */
public class LotteryStorage {

    private static LotteryRepository repository;

    /**
     * Initializes the storage system.
     *
     * Creates the gambling folder and lottery.json file if they do not exist.
     */
    public static void setup(LotteryRepository repo) {
        repository = repo;
    }

    /**
     * Saves the current lottery state to disk.
     *
     * Includes total pot and all player tickets.
     */
    public static void save() {
        if (repository == null) {
            return;
        }
        LotteryState state = new LotteryState(
                LotteryState.CURRENT_ID,
                LotteryManager.getPot(),
                new HashMap<>(LotteryManager.getAllTickets())
        );
        repository.saveState(state);
        repository.flush();
    }

    /**
     * Loads lottery data from disk.
     *
     * Restores pot and ticket distribution.
     */
    public static void load() {
        if (repository == null) {
            return;
        }
        LotteryState state = repository.getState();
        LotteryManager.loadData(state.getPot(), new HashMap<UUID, Integer>(state.getTickets()));
    }
}