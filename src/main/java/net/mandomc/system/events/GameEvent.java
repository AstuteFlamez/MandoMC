package net.mandomc.system.events;

public interface GameEvent {

    String getId();

    String getDisplayName();

    void start(EventManager manager);

    void end(EventManager manager);

    default void onForceEnd(EventManager manager) {
        end(manager);
    }

    default boolean isRunning() {
        return false;
    }
}