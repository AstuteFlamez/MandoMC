package net.mandomc.core.modules.server;

import net.mandomc.server.events.EventManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertTrue;

class EventModuleLifecycleTest {

    @Test
    void disableForceEndsActiveEventWhenManagerPresent() throws Exception {
        EventModule module = new EventModule(null);
        TrackingEventManager trackingManager = new TrackingEventManager();

        Field field = EventModule.class.getDeclaredField("eventManager");
        field.setAccessible(true);
        field.set(module, trackingManager);

        module.disable();

        assertTrue(trackingManager.forceEndCalled);
    }

    private static final class TrackingEventManager extends EventManager {
        private boolean forceEndCalled;

        private TrackingEventManager() {
            super(null);
        }

        @Override
        public void forceEndActiveEvent(boolean broadcast) {
            forceEndCalled = true;
        }
    }
}
