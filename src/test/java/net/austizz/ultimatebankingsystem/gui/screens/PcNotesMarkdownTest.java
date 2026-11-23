package net.austizz.ultimatebankingsystem.gui.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PcNotesMarkdownTest {
    @Test
    void renderedTextKeepsOffsetsBackToMarkdown() {
        String source = "# Title\n\n- **Bold** and _italic_ and ~~removed~~ and [link](https://example.com)";
        var rendered = PcNotesMarkdown.formatted(source);
        assertEquals("Title\n\n• Bold and italic and removed and link", rendered.text());
        assertEquals(1, rendered.heading(0));
        int bold = rendered.text().indexOf("Bold");
        assertEquals(PcNotesMarkdown.BOLD, rendered.style(bold));
        assertEquals(source.indexOf("Bold"), rendered.start(bold));
        assertEquals(source.indexOf("Bold") + 4, rendered.end(bold + 4));
        assertEquals(PcNotesMarkdown.LINK, rendered.style(rendered.text().lastIndexOf("link")));
    }

    @Test
    void tableEditsPreserveTheHeaderAndSurroundingText() {
        String draft = "Before\n\n" + PcNotesMarkdown.table(3, 3) + "\n\nAfter";
        String inserted = PcNotesMarkdown.editTable(draft, draft.indexOf("Column 2"), 0, 1);
        assertTrue(inserted.contains("| Column 1 | Column 2 |  | Column 3 |"));
        assertTrue(inserted.startsWith("Before\n\n"));
        assertTrue(inserted.endsWith("\n\nAfter"));
        assertEquals(draft, PcNotesMarkdown.editTable(draft, 0, 0, -1));
    }
}
