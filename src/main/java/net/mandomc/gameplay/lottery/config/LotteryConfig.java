package net.mandomc.gameplay.lottery.config;

import net.mandomc.core.config.BaseConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

/**
 * Typed configuration for the lottery system.
 *
 * Wraps {@code gambling/lottery.yml}.
 */
public class LotteryConfig extends BaseConfig {

    public LotteryConfig(Plugin plugin) {
        super(plugin, "gambling/lottery.yml");
    }

    /** Cost of one lottery ticket. */
    public double getTicketPrice() {
        return getDouble("lottery.ticket-price", 1000.0);
    }

    /** Maximum tickets a single player may hold at once. */
    public int getMaxTicketsPerPlayer() {
        return getInt("lottery.max-tickets-per-player", 10);
    }

    /** Day of the week the draw occurs (e.g. {@code "SUNDAY"}). */
    public String getDrawDay() {
        return getString("lottery.draw.day", "SUNDAY");
    }

    /** Hour of the draw (24-h, server time). */
    public int getDrawHour() {
        return getInt("lottery.draw.hour", 12);
    }

    /** Minute of the draw. */
    public int getDrawMinute() {
        return getInt("lottery.draw.minute", 0);
    }

    /** Raw main hologram configuration section. */
    public ConfigurationSection getHologramSection() {
        return getSection("lottery.hologram");
    }

    /** Raw top-players hologram configuration section. */
    public ConfigurationSection getTopHologramSection() {
        return getSection("lottery.top-hologram");
    }

    /** Raw broadcasts configuration section. */
    public ConfigurationSection getBroadcastsSection() {
        return getSection("lottery.broadcasts");
    }
}
