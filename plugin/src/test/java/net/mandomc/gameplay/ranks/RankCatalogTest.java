package net.mandomc.gameplay.ranks;

import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.Test;
import static org.junit.Assert.*;

public class RankCatalogTest {
    private YamlConfiguration config() { return YamlConfiguration.loadConfiguration(new InputStreamReader(getClass().getResourceAsStream("/ranks/ranks.yml"))); }
    @Test public void restoredRanksAndInheritance() {
        var ranks = new RankCatalog(config());
        assertEquals(List.of("mandostudios", "mod", "builder", "creator", "mandalore", "marshal", "warrior", "default"), ranks.ids());
        assertEquals("mod", ranks.primary(Set.of("builder", "mod")).id());
        assertEquals("default", ranks.primary(Set.of()).id());
        assertEquals(Boolean.TRUE, RankCatalog.resolve(ranks.permissions(Set.of("warrior")), "mandomc.credits.pay"));
        assertNull(RankCatalog.resolve(ranks.permissions(Set.of("mandalore")), "mandomc.ranks.manage"));
        assertEquals(Boolean.TRUE, RankCatalog.resolve(ranks.permissions(Set.of("builder")), "mandomc.admin.fly"));
        assertEquals(Boolean.TRUE, RankCatalog.resolve(ranks.permissions(Set.of("builder")), "mandomc.travel.homes.unlimited"));
        assertEquals(Boolean.FALSE, RankCatalog.resolve(ranks.permissions(Set.of("default")), "mandomc.travel.homes.unlimited"));
        assertEquals(Boolean.TRUE, RankCatalog.resolve(ranks.permissions(Set.of("mandostudios")), "mandomc.ranks.manage"));
    }
    @Test public void mostSpecificPermissionWins() {
        var nodes = Map.of("*", true, "worldedit.*", false, "worldedit.selection.pos", true);
        assertEquals(Boolean.FALSE, RankCatalog.resolve(nodes, "worldedit.region.set"));
        assertEquals(Boolean.TRUE, RankCatalog.resolve(nodes, "worldedit.selection.pos"));
        assertEquals(Boolean.TRUE, RankCatalog.resolve(nodes, "mandomc.credits.admin"));
    }
    @Test public void cyclesUnknownParentsAndDuplicateOrdersFail() {
        var c = config(); c.set("ranks.default.inherits", List.of("warrior"));
        var cycle = c; assertThrows(IllegalArgumentException.class, () -> new RankCatalog(cycle));
        c = config(); c.set("ranks.default.inherits", List.of("missing")); var unknown = c;
        assertThrows(IllegalArgumentException.class, () -> new RankCatalog(unknown));
        c = config(); c.set("ranks.default.order", 0); var duplicate = c;
        assertThrows(IllegalArgumentException.class, () -> new RankCatalog(duplicate));
    }
    @Test public void malformedMembershipStoreDoesNotResetRanks() throws Exception {
        var path = Files.createTempFile("rank-users", ".yml");
        Files.writeString(path, "schema-version: 1\nusers:\n  bad-id:\n    ranks: [mandostudios]\n");
        assertThrows(IllegalArgumentException.class, () -> RankService.read(path, new RankCatalog(config())));
        Files.writeString(path, "schema-version: 1\nusers:\n  '" + UUID.randomUUID() + "':\n    ranks: [missing]\n");
        assertThrows(IllegalArgumentException.class, () -> RankService.read(path, new RankCatalog(config())));
        Files.delete(path);
    }
}
