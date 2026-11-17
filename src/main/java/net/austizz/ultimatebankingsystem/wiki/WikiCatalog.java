package net.austizz.ultimatebankingsystem.wiki;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Bundled, offline help. Keep claims here aligned with the current commands and screens. */
public final class WikiCatalog {
    private static final String ROOT = "/assets/ultimatebankingsystem/wiki/";
    private static final Gson GSON = new Gson();
    private static final List<Page> PAGES = load();

    private WikiCatalog() {}

    public record Page(String id, String category, String title, String summary, String markdown) {
        public boolean matches(String query) {
            String needle = query.strip().toLowerCase(Locale.ROOT);
            if (needle.isEmpty()) return true;
            String haystack = (id + ' ' + category + ' ' + title + ' ' + summary + ' ' + markdown)
                    .toLowerCase(Locale.ROOT);
            for (String word : needle.split("\\s+")) if (!haystack.contains(word)) return false;
            return true;
        }
    }

    private record Entry(String id, String category, String title, String summary) {}
    private record Document(List<Entry> pages) {}

    public static List<Page> pages() {
        return PAGES;
    }

    private static List<Page> load() {
        try (var stream = WikiCatalog.class.getResourceAsStream(ROOT + "index.json")) {
            if (stream == null) throw new IllegalStateException("Missing bundled wiki index");
            Document document = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), Document.class);
            if (document == null || document.pages() == null || document.pages().isEmpty()) {
                throw new IllegalStateException("Bundled wiki has no pages");
            }
            Set<String> ids = new HashSet<>();
            java.util.ArrayList<Page> pages = new java.util.ArrayList<>();
            for (Entry entry : document.pages()) {
                if (entry.id() == null || !entry.id().matches("[a-z0-9-]+")
                        || entry.category() == null || entry.title() == null || entry.summary() == null
                        || !ids.add(entry.id())) throw new IllegalStateException("Invalid or duplicate wiki page");
                String path = ROOT + "pages/" + entry.id() + ".md";
                try (var pageStream = WikiCatalog.class.getResourceAsStream(path)) {
                    if (pageStream == null) throw new IllegalStateException("Missing wiki page: " + path);
                    String markdown = new String(pageStream.readAllBytes(), StandardCharsets.UTF_8);
                    if (markdown.isBlank()) throw new IllegalStateException("Empty wiki page: " + path);
                    pages.add(new Page(entry.id(), entry.category(), entry.title(), entry.summary(), markdown));
                }
            }
            return List.copyOf(pages);
        } catch (java.io.IOException | JsonParseException ex) {
            throw new IllegalStateException("Cannot load bundled wiki", ex);
        }
    }
}
