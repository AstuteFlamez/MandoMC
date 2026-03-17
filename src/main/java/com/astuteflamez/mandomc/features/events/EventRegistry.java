package com.astuteflamez.mandomc.features.events;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.astuteflamez.mandomc.features.events.types.jabba_dungeon.JabbaDungeonEvent;
import com.astuteflamez.mandomc.features.events.types.koth.KothEvent;
import com.astuteflamez.mandomc.features.events.types.mining.BeskarRushEvent;

public class EventRegistry {

    private final Map<String, Function<EventDefinition, GameEvent>> registry = new HashMap<>();

    public EventRegistry() {
        register("koth", KothEvent::new);
        register("beskar", BeskarRushEvent::new);
        register("jabba", JabbaDungeonEvent::new);
    }

    public void register(String id, Function<EventDefinition, GameEvent> factory) {
        registry.put(id.toLowerCase(), factory);
    }

    public GameEvent create(EventDefinition definition) {
        Function<EventDefinition, GameEvent> factory = registry.get(definition.getId().toLowerCase());
        if (factory == null) {
            return null;
        }
        return factory.apply(definition);
    }

    public boolean isRegistered(String id) {
        return registry.containsKey(id.toLowerCase());
    }
}