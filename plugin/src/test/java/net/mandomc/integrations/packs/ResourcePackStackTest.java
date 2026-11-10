package net.mandomc.integrations.packs;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.resource.ResourcePackInfo;
import org.junit.Test;
import static org.junit.Assert.*;

public class ResourcePackStackTest {
    private ResourcePackInfo pack() {
        return ResourcePackInfo.resourcePackInfo(UUID.randomUUID(), URI.create("https://example.com/pack.zip"), "a".repeat(40));
    }

    @Test public void clientStackAlwaysPlacesMandoAboveBothDependencies() {
        var main = pack(); var saber = pack(); var faithful = pack();
        var request = SaberPack.stack(main, saber, faithful);
        assertEquals(List.of(faithful, saber, main), request.packs());
        assertTrue(request.replace());
        assertTrue(request.required());
    }

    @Test public void duplicatePackIdentitiesCannotSilentlyReplaceOneAnother() {
        var main = pack();
        assertThrows(IllegalArgumentException.class, () -> SaberPack.stack(main, main, pack()));
    }
}
