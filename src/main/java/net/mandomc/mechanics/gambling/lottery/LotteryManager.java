package net.mandomc.mechanics.gambling.lottery;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import net.mandomc.core.LangManager;

import net.mandomc.core.modules.core.EconomyModule;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages the state and logic of the lottery system.
 *
 * Responsible for:
 * - Tracking ticket ownership
 * - Managing the total pot
 * - Selecting a weighted winner
 * - Executing lottery draws
 */
public class LotteryManager {

    private static double pot = 0;
    private static final Map<UUID, Integer> tickets = new HashMap<>();

    /**
     * Adds a ticket for a player and increases the pot.
     *
     * @param uuid  player UUID
     * @param price ticket price
     */
    public static void addTicket(UUID uuid, double price) {
        tickets.put(uuid, tickets.getOrDefault(uuid, 0) + 1);
        pot += price;
    }

    /**
     * Gets the number of tickets owned by a player.
     *
     * @param uuid player UUID
     * @return ticket count
     */
    public static int getTickets(UUID uuid) {
        return tickets.getOrDefault(uuid, 0);
    }

    /**
     * Gets the current lottery pot.
     *
     * @return total pot
     */
    public static double getPot() {
        return pot;
    }

    /**
     * Returns all ticket data.
     *
     * @return map of UUID to ticket count
     */
    public static Map<UUID, Integer> getAllTickets() {
        return tickets;
    }

    /**
     * Loads saved lottery data into memory.
     *
     * @param loadedPot     saved pot value
     * @param loadedTickets saved ticket map
     */
    public static void loadData(double loadedPot, Map<UUID, Integer> loadedTickets) {
        pot = loadedPot;
        tickets.clear();
        tickets.putAll(loadedTickets);
    }

    /**
     * Executes the lottery draw.
     *
     * Selects a weighted winner based on ticket count,
     * pays out the pot, broadcasts the result,
     * and resets the system.
     */
    public static void executeDraw() {

        if (tickets.isEmpty()) {
            Bukkit.broadcastMessage(LangManager.get("lottery.no-players"));
            return;
        }

        UUID winnerId = pickWinner();
        if (winnerId == null) {
            return;
        }

        Player winner = Bukkit.getPlayer(winnerId);
        double winnings = pot;

        if (winner != null && winner.isOnline()) {
            EconomyModule.deposit(winner, winnings);
        }

        Bukkit.broadcastMessage(LangManager.get("lottery.winner",
                "%winner%", (winner != null ? winner.getName() : "Offline Player"),
                "%amount%", String.valueOf(winnings)));

        reset();
    }

    /**
     * Picks a winner using weighted random selection.
     *
     * Each ticket increases the chance of winning.
     *
     * @return winner UUID or null if no valid selection
     */
    private static UUID pickWinner() {

        int totalTickets = tickets.values().stream().mapToInt(Integer::intValue).sum();
        if (totalTickets <= 0) return null;

        int randomValue = ThreadLocalRandom.current().nextInt(totalTickets);

        int cumulative = 0;

        for (Map.Entry<UUID, Integer> entry : tickets.entrySet()) {
            cumulative += entry.getValue();

            if (randomValue < cumulative) {
                return entry.getKey();
            }
        }

        return null;
    }

    /**
     * Resets the lottery after a draw.
     *
     * Clears tickets, resets pot, and persists data.
     */
    private static void reset() {
        tickets.clear();
        pot = 0;
        LotteryStorage.save();
    }
}