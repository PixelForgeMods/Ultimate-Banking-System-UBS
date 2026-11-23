package net.austizz.ultimatebankingsystem.gui.screens;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class PcSearch {
    private PcSearch() {}

    record Match(PcFileSystem.Entry file, int score) {}

    static int rank(String text, String query) {
        String name = text.toLowerCase(Locale.ROOT);
        if (name.equals(query)) return 0;
        if (name.startsWith(query)) return 1;
        if (name.contains(query)) return 2;
        for (String part : query.split("\\s+")) if (!name.contains(part)) return -1;
        return 3;
    }

    static List<Match> files(PcFileSystem drive, String query) throws IOException {
        return drive.searchableEntries().stream().map(entry -> {
            int score = rank(entry.name(), query);
            if (score < 0 && !entry.folder() && !entry.binary()
                    && entry.text().toLowerCase(Locale.ROOT).contains(query)) score = 4;
            return new Match(entry, score);
        }).filter(match -> match.score >= 0)
                .sorted(Comparator.comparingInt(Match::score).thenComparing(match -> match.file.name(), String.CASE_INSENSITIVE_ORDER))
                .limit(100).toList();
    }
}
