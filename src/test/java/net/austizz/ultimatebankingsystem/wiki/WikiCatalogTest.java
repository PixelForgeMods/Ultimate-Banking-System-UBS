package net.austizz.ultimatebankingsystem.wiki;

import org.junit.jupiter.api.Test;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.Node;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiCatalogTest {
    @Test
    void bundledPagesCoverPlayersAndOperators() {
        var pages = WikiCatalog.pages();
        assertTrue(pages.size() >= 33);
        assertTrue(pages.stream().anyMatch(page -> page.category().equals("Operator")));
        assertTrue(pages.stream().anyMatch(page -> page.category().equals("Jobs")));
        assertTrue(pages.stream().anyMatch(page -> page.category().equals("Shops")));
        assertEquals(pages.size(), pages.stream().map(WikiCatalog.Page::id).collect(Collectors.toSet()).size());
        assertTrue(pages.stream().allMatch(page -> page.markdown().startsWith("# ")));
        assertTrue(pages.stream().anyMatch(page -> page.id().equals("currency")));
        assertTrue(pages.stream().anyMatch(page -> page.id().equals("money-stacks")));
    }

    @Test
    void searchIncludesBodyAndCommandsWithoutCaseSensitivity() {
        Set<String> results = WikiCatalog.pages().stream().filter(page -> page.matches("/DELIVERY CANCEL"))
                .map(WikiCatalog.Page::id).collect(Collectors.toSet());
        assertTrue(results.contains("courier"));
        assertFalse(WikiCatalog.pages().getFirst().matches("/delivery cancel"));
        assertTrue(WikiCatalog.pages().getFirst().matches(""));
        assertTrue(WikiCatalog.pages().stream().anyMatch(page -> page.id().equals("atm")
                && page.matches("PIN withdrawal")));
    }

    @Test
    void gfmTablesAndBoundedItemPreviewsParse() {
        Node document = WikiMarkdown.parse("| Bill | Model |\n| --- | --- |\n"
                + "| $1 | ![bill](item:ultimatebankingsystem:one_dollar_bill?size=500&tooltip=false) |\n");
        assertInstanceOf(TableBlock.class, document.getFirstChild());
        var media = WikiMarkdown.media("item:ultimatebankingsystem:one_dollar_bill?size=500&tooltip=false").orElseThrow();
        assertEquals(96, media.size());
        assertFalse(media.tooltip());
        assertTrue(WikiMarkdown.media("https://example.org/remote.png").isEmpty());
        assertTrue(WikiMarkdown.media("item:bad item?size=32").isEmpty());
        Node currency = WikiMarkdown.parse(WikiCatalog.pages().stream()
                .filter(page -> page.id().equals("currency")).findFirst().orElseThrow().markdown());
        assertTrue(hasTable(currency));
        var texture = WikiMarkdown.media("texture:ultimatebankingsystem:textures/item/bill.png?size=40&tooltip=false")
                .orElseThrow();
        assertEquals("texture", texture.kind());
        assertEquals(40, texture.size());
        assertFalse(texture.tooltip());
    }

    @Test
    void bundledMarkdownHasResolvableGuideLinksAndItemModels() {
        Set<String> ids = WikiCatalog.pages().stream().map(WikiCatalog.Page::id).collect(Collectors.toSet());
        for (var page : WikiCatalog.pages()) {
            checkReferences(WikiMarkdown.parse(page.markdown()), ids);
        }
    }

    @Test
    void screenshotsAreBundledAtTheirDeclaredDimensions() throws IOException {
        var screenshot = WikiMarkdown.media("screenshot:ultimatebankingsystem:07?width=851&height=602").orElseThrow();
        assertEquals("screenshot", screenshot.kind());
        assertTrue(WikiMarkdown.media("screenshot:ultimatebankingsystem:07?width=0&height=602").isEmpty());
        for (var page : WikiCatalog.pages()) {
            checkScreenshots(WikiMarkdown.parse(page.markdown()));
        }
    }

    private static void checkScreenshots(Node node) throws IOException {
        if (node instanceof Image image && image.getDestination().startsWith("screenshot:")) {
            var media = WikiMarkdown.media(image.getDestination()).orElseThrow();
            String number = media.id().substring(media.id().indexOf(':') + 1);
            String path = "/assets/ultimatebankingsystem/textures/gui/wiki/screenshots/" + number + ".png";
            try (var stream = WikiCatalogTest.class.getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var png = new DataInputStream(stream);
                png.skipNBytes(16);
                assertEquals(png.readInt(), media.width(), path);
                assertEquals(png.readInt(), media.height(), path);
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            checkScreenshots(child);
        }
    }

    private static void checkReferences(Node node, Set<String> ids) {
        if (node instanceof Link link && link.getDestination().startsWith("wiki:")) {
            assertTrue(ids.contains(link.getDestination().substring(5)), link.getDestination());
        }
        if (node instanceof Image image && (image.getDestination().startsWith("item:")
                || image.getDestination().startsWith("texture:")
                || image.getDestination().startsWith("screenshot:"))) {
            assertTrue(WikiMarkdown.media(image.getDestination()).isPresent(), image.getDestination());
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            checkReferences(child, ids);
        }
    }

    private static boolean hasTable(Node node) {
        if (node instanceof TableBlock) return true;
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            if (hasTable(child)) return true;
        }
        return false;
    }
}
