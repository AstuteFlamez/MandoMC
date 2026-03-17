package com.bilal.mandomc.features.events;

public abstract class AbstractGameEvent implements GameEvent {

    private final String id;
    private final String displayName;
    private boolean running;

    protected AbstractGameEvent(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public final void start(EventManager manager) {
        if (running) return;
        running = true;
        onStart(manager);
    }

    @Override
    public final void end(EventManager manager) {
        if (!running) return;
        running = false;
        onEnd(manager);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    protected abstract void onStart(EventManager manager);

    protected abstract void onEnd(EventManager manager);
}