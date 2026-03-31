package net.mandomc.core.logging;

import java.util.logging.Logger;

/**
 * Thin injectable wrapper around {@link java.util.logging.Logger}.
 *
 * Provides consistent prefixed logging for each subsystem without depending
 * on static plugin access. Inject an instance into each service/manager
 * instead of calling {@code plugin.getLogger()} or {@code Bukkit.getLogger()} directly.
 */
public final class PluginLogger {

    private final Logger logger;
    private final String prefix;

    /**
     * Creates a logger with an optional subsystem prefix appended to each message.
     *
     * @param logger the underlying JUL logger (from {@code plugin.getLogger()})
     * @param prefix the subsystem name shown in brackets, e.g. {@code "Items"}
     */
    public PluginLogger(Logger logger, String prefix) {
        this.logger = logger;
        this.prefix = "[" + prefix + "] ";
    }

    /** Logs an informational message. */
    public void info(String message) {
        logger.info(prefix + message);
    }

    /** Logs a warning. */
    public void warn(String message) {
        logger.warning(prefix + message);
    }

    /** Logs a severe error. */
    public void error(String message) {
        logger.severe(prefix + message);
    }

    /**
     * Logs a severe error together with the cause's message.
     *
     * @param message high-level description
     * @param cause   the exception that was caught
     */
    public void error(String message, Throwable cause) {
        logger.severe(prefix + message + ": " + cause.getMessage());
    }
}
