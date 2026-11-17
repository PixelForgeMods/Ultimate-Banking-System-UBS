package net.austizz.ultimatebankingsystem.gui.screens;

import java.util.Map;
import java.util.function.Predicate;

final class ShelfCatalogFilters {
    private ShelfCatalogFilters() {}

    static <T> boolean matches(Map<T, Integer> filters, Predicate<T> member) {
        boolean hasInclude = false;
        boolean included = false;
        for (var filter : filters.entrySet()) {
            if (filter.getValue() == 1) {
                hasInclude = true;
                included |= member.test(filter.getKey());
            } else if (filter.getValue() == 2 && member.test(filter.getKey())) {
                return false;
            }
        }
        return !hasInclude || included;
    }
}
