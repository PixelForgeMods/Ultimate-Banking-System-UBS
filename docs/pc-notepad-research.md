# Modern Notepad reference

## What the supplied images show

| Image | State |
| --- | --- |
| 1 | Blank document in formatted mode. A tab strip, File/Edit/View menus, formatting toolbar, and status bar surround the editor. |
| 2 | File menu with new tab, new Markdown tab, open and recent files, save commands, print commands, and close commands. |
| 3 | Edit menu with undo, clipboard actions, clear formatting, find and replace, go to line, select all, date/time, and font. |
| 4 | View menu with zoom, status bar, word wrap, and a Markdown submenu. |
| 5 | Heading picker with six levels and a Body option. |
| 6 | List picker with bullets, numbering, and controls to increase or decrease list indentation. |
| 7 | Bold toggled on at the caret. |
| 8 | Italic toggled on at the caret. |
| 9 | Strikethrough toggled on at the caret. |
| 10 | Link dialog with optional display text, an address field, and Insert and Cancel actions. Insert starts disabled. |
| 11 | Table dimension picker with a 5 × 5 grid. Hovering highlights the chosen dimensions, and table editing is separate from insertion. |
| 12 | Markdown syntax mode. The toolbar is hidden, the status bar names the mode, and an unsaved tab is marked with a dot. |

## Documented behavior

- The toolbar applies styles to selected text or to text typed next. The View menu and the status bar switch between formatted editing and Markdown syntax. Clear Formatting is a separate editing command. [Microsoft's original formatting announcement](https://blogs.windows.com/windows-insider/2025/05/30/text-formatting-in-notepad-begin-rolling-out-to-windows-insiders/)
- Tables can be inserted through the toolbar or by typing Markdown. The Table menu and the right-click menu support adding and removing rows and columns. [Microsoft's table announcement](https://blogs.windows.com/windows-insider/2025/11/21/notepad-update-begins-rolling-out-to-windows-insiders/)
- Strikethrough and nested lists can be applied through the toolbar, keyboard shortcuts, or direct source edits. [Microsoft's January 2026 formatting update](https://blogs.windows.com/windows-insider/2026/01/21/notepad-and-paint-updates-begin-rolling-out-to-windows-insiders/)
- Tabs represent independent documents in one window. Notepad's session restoration can retain open tabs and unsaved drafts separately from saved file contents. [Tabs announcement](https://blogs.windows.com/windows-insider/2023/01/19/tabs-in-notepad-begins-rolling-out-to-windows-insiders/) · [Session restoration announcement](https://blogs.windows.com/windows-insider/2023/08/31/new-updates-for-snipping-tool-and-notepad-for-windows-insiders/)
- The status bar can show the size of the selection alongside the document size. Recent files are available from File. [Character count announcement](https://blogs.windows.com/windows-insider/2023/12/07/announcing-windows-11-insider-preview-build-23601-dev-channel/) · [Recent files announcement](https://blogs.windows.com/windows-insider/2025/03/13/snipping-tool-and-notepad-app-updates-begin-rolling-out-to-windows-insiders/)

The in-game adaptation uses the virtual PC's own design and files. Formatted and source views should share the same Markdown data, so toggling views preserves the document. Formatting controls belong in formatted mode, with a persistent route back to source through View. The Windows reference assigns Ctrl+Shift+S to Save As. The virtual PC already assigns that shortcut to screenshot capture, so Save As needs a separate route.
