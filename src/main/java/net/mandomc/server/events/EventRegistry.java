package net.mandomc.server.events;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import net.mandomc.server.events.types.jabba.JabbaDungeonEvent;
import net.mandomc.server.events.types.koth.KothEvent;
import net.mandomc.server.events.types.beskar.BeskarRushEvent;
import net.mandomc.server.events.model.GameEvent;
import net.mandomc.server.events.model.EventDefinition;

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