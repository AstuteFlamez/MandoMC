package net.mandomc.gameplay.events;

/** EventManager owns scheduling; an event owns only its gameplay and transient visuals. */
public interface ManagedEvent extends AutoCloseable {
    String id();
    String displayName();
    void start();
    void tick();
    boolean running();
    boolean relevant(org.bukkit.entity.Player player);
    default long endingInTicks() { return Long.MAX_VALUE; }
    void stop(boolean announce);
    @Override default void close() { stop(false); }
}
