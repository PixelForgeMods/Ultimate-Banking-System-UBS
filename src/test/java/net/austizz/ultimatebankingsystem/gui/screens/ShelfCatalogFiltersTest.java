package net.austizz.ultimatebankingsystem.gui.screens;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShelfCatalogFiltersTest {
    @Test
    void includesMatchAnyAndExcludesAlwaysWin() {
        Map<String, Integer> filters = Map.of("c:ingots", 1, "minecraft:planks", 1,
                "c:ingots/gold", 2);
        assertTrue(ShelfCatalogFilters.matches(filters, Set.of("c:ingots/iron", "c:ingots")::contains));
        assertFalse(ShelfCatalogFilters.matches(filters, Set.of("c:ingots", "c:ingots/gold")::contains));
        assertFalse(ShelfCatalogFilters.matches(filters, Set.of("minecraft:logs")::contains));
        assertTrue(ShelfCatalogFilters.matches(Map.of("minecraft:logs", 2), Set.of("minecraft:planks")::contains));
    }
}
