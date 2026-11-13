package net.mandomc.access;

import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class AccessStateTest {
    private final UUID owner = UUID.randomUUID();
    private final UUID guest = UUID.randomUUID();
    private AccessState initial() { return new AccessState(true, owner, Map.of(owner, "Owner")); }

    @Test public void listedAndUnlistedAndOff() {
        assertTrue(initial().allows(owner));
        assertFalse(initial().allows(guest));
        assertTrue(initial().withEnabled(false).allows(guest));
        assertTrue(initial().add(guest, "Guest").allows(guest));
        assertFalse(initial().add(guest, "Guest").remove(guest).allows(guest));
    }
    @Test public void cannotRemoveOwnerEvenIfAnotherAccountExists() {
        assertThrows(IllegalArgumentException.class, () -> initial().add(guest, "Guest").remove(owner));
        assertThrows(IllegalArgumentException.class, () -> new AccessState(true, owner, Map.of()));
    }
    @Test public void restartRoundTripAndFailedWritePreservesPriorState() throws Exception {
        var directory = Files.createTempDirectory("maintenance-test");
        var file = directory.resolve("maintenance.yml");
        try {
            initial().save(file);
            assertEquals(initial(), AccessState.load(file));
            var changed = initial().add(guest, "Guest").withEnabled(false);
            changed.save(file);
            assertEquals(changed, AccessState.load(file));
            var old = Files.readString(file);
            assertThrows(java.io.IOException.class, () -> changed.save(file.resolve("impossible.yml")));
            assertEquals(old, Files.readString(file));
            assertEquals(1, Files.list(directory).count());
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(directory); }
    }
    @Test public void corruptOrIncompleteStateRefused() throws Exception {
        var file = Files.createTempFile("maintenance-bad", ".yml");
        try {
            Files.writeString(file, "schema-version: 1\nenabled: false\nowner: invalid\nplayers: {}\n");
            assertThrows(IllegalArgumentException.class, () -> AccessState.load(file));
            Files.writeString(file, "schema-version: 1\nowner: '" + owner + "'\nplayers: {}\n");
            assertThrows(IllegalArgumentException.class, () -> AccessState.load(file));
        } finally { Files.deleteIfExists(file); }
    }
}
