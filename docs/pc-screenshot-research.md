# Windows 11 screenshot capture

## Supplied reference images

| Image | Capture state |
| --- | --- |
| 1 and 4 | A rectangle is being dragged. The area outside it is dimmed, the dotted outline follows the drag, and a badge beside the pointer reports the current pixel dimensions. The toolbar is out of the way. |
| 2 | The capture bar is visible with video selected, and the Text extractor tooltip identifies the right-hand utility. |
| 3 | The same bar shows the Color picker tooltip. |
| 5 | The still capture selector is open. The order is Rectangle, Window, Full screen, then Freeform. A marker identifies Rectangle as selected. |
| 6, 7, and 8 | The camera tab is selected and the picture-and-pencil button is highlighted. The capture UI floats over the source content. The documentation identifies this button as Quick markup. |

## Behavior documented by Microsoft

- The system shortcut opens an overlay over the current desktop with a capture toolbar. The toolbar has still and video capture tabs. Windows also has a separate shortcut that opens directly in video capture. [Combined capture bar announcement](https://blogs.windows.com/windows-insider/2023/08/31/new-updates-for-snipping-tool-and-notepad-for-windows-insiders/)
- Rectangle captures the dragged bounds. Window captures a chosen window. Full screen captures the whole screen immediately. Freeform lets the user draw a closed selection. The desktop is dimmed during selection. The toolbar's icon grouping, selection outline, and pixel badge come from the supplied references. [Microsoft Support capture guide](https://support.microsoft.com/en-gb/windows/apps/use-snipping-tool-to-capture-screenshots)
- The mode selector supports keyboard navigation. Microsoft describes using Tab to focus it, Enter to open it, then an arrow key and Enter to choose a mode. [Microsoft Support screen clipping walkthrough](https://support.microsoft.com/en-gb/accessibility/windows/use-a-screen-reader-to-explore-and-navigate-the-windows-desktop)
- A completed still capture goes to the image clipboard and is automatically saved to Pictures > Screenshots by default. The result can be opened from a notification for editing, if the editor does not open automatically. [Windows keyboard shortcuts](https://support.microsoft.com/en-us/accessibility/windows/keyboard-shortcuts-in-windows) · [Surface screenshot guide](https://support.microsoft.com/en-us/surface/accessories/cover-keyboard/take-a-screenshot-or-record-your-screen-on-surface) · [Notification and markup walkthrough](https://blogs.windows.com/windows-insider/2025/03/13/snipping-tool-and-notepad-app-updates-begin-rolling-out-to-windows-insiders/)
- Quick markup can be toggled before taking a snip, including with Ctrl+E. It lets the user draw on the selected image and revise the selection before finalizing. Microsoft's capture bar also exposes Color picker to inspect and copy a color value, and Text extractor to scan a selected region and copy recognized text. These are separate workflows from still screenshot capture. [Quick markup announcement](https://blogs.windows.com/windowsexperience/2025/10/16/new-experiences-currently-rolling-out-for-windows-11/) · [Color picker announcement](https://blogs.windows.com/windowsexperience/2025/07/22/windows-11-is-the-home-for-ai-on-the-pc-with-even-more-experiences-available-today/) · [Text extractor announcement](https://blogs.windows.com/windows-insider/2025/04/15/text-extractor-in-snipping-tool-begins-rolling-out-to-windows-insiders/)

## In-game mapping

Ctrl+Shift+S should invoke still capture inside the virtual PC. The captured pixels should come from its rendered desktop before capture UI is drawn, so the toolbar, selection lines, and dimming never appear in a saved image. Selection should stay in desktop coordinates, with capture bounds and the dimension badge converted to framebuffer pixels. Capture should save a PNG to the virtual Pictures > Screenshots folder and offer a direct route to the saved image in Photos. The still capture modes and cancellation form the main workflow. Recording and screen text recognition are adjacent tools in the references.
