package net.mandomc.world.ilum;

import net.mandomc.world.ilum.manager.ParkourManager;
import net.mandomc.world.ilum.model.ParkourSession;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ParkourLifecycleTest {

    @SuppressWarnings("unchecked")
    @Test
    void shutdownSessionsClearsSessionMap() throws Exception {
        ParkourManager manager = new ParkourManager(null, null);

        Field sessionsField = ParkourManager.class.getDeclaredField("sessions");
        sessionsField.setAccessible(true);
        Map<UUID, ParkourSession> sessions = (Map<UUID, ParkourSession>) sessionsField.get(manager);

        UUID playerId = UUID.randomUUID();
        sessions.put(playerId, new ParkourSession(playerId));

        manager.shutdownSessions();

        assertTrue(sessions.isEmpty(), "shutdown should clear all active parkour sessions");
    }
}
