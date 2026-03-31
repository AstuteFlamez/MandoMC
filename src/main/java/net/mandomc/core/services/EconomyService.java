package net.mandomc.core.services;

import org.bukkit.OfflinePlayer;

/**
 * Small abstraction over economy operations to keep command logic testable.
 */
public interface EconomyService {

    /**
     * Deposits money into a player's account.
     *
     * @param player the player receiving funds
     * @param amount amount to deposit
     * @return true when transaction succeeds
     */
    boolean deposit(OfflinePlayer player, double amount);
}
