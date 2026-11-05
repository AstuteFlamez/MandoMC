package net.mandomc.server.events.config;

import net.mandomc.core.config.BaseConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

/**
 * Typed configuration for the event scheduling system.
 *
 * Wraps {@code events.yml} which controls the event scheduler,
 * announcement timings, and the event selection GUI.
 */
public class EventConfig extends BaseConfig {

    public EventConfig(Plugin plugin) {
        super(plugin, "events.yml");
    }

    /** Whether the automatic event scheduler is active. */
    public boolean isSchedulerEnabled() {
        return getBoolean("scheduler.enabled", true);
    }

    /** Minute within an hour at which the end-warning announcement fires. */
    public int getEndWarningMinute() {
        return getInt("scheduler.announcements.end-warning-minute", 50);
    }

    /** Minute within an hour at which the event-end announcement fires. */
    public int getEndMinute() {
        return getInt("scheduler.announcements.end-minute", 55);
    }

    /** Minute within an hour at which the start-warning announcement fires. */
    public int getStartWarningMinute() {
        return getInt("scheduler.announcements.start-warning-minute", 55);
    }

    /** Minute within an hour at which the event-start announcement fires. */
    public int getStartMinute() {
        return getInt("scheduler.announcements.start-minute", 0);
    }

    /** Raw scheduler messages section (current-event-ending, next-event-warning, etc.). */
    public ConfigurationSection getMessagesSection() {
        return getSection("scheduler.messages");
    }

    /** Raw event selection GUI configuration section. */
    public ConfigurationSection getGuiSection() {
        return getSection("gui");
    }
}
