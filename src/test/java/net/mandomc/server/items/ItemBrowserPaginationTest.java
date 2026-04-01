package net.mandomc.server.items.gui;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ItemBrowserPaginationTest {

    @Test
    void rarityRankPrefersHigherTiers() {
        assertEquals(6, ItemBrowserGUI.rarityRank("mythic"));
        assertEquals(5, ItemBrowserGUI.rarityRank("legendary"));
        assertEquals(1, ItemBrowserGUI.rarityRank("common"));
        assertEquals(0, ItemBrowserGUI.rarityRank("unknown"));
    }

    @Test
    void rarityRankIsCaseInsensitive() {
        assertEquals(ItemBrowserGUI.rarityRank("legendary"), ItemBrowserGUI.rarityRank("LeGeNdArY"));
    }
}
