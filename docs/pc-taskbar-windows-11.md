# Windows 11 taskbar behavior for the UBS PC

## Image 1 — app Jump List

The menu above the CurseForge button is the app's **taskbar Jump List**. Windows opens it on right-click. Apps can supply their own tasks and recent or frequent destinations above the system actions. The accent underline in the image marks the active app. [Microsoft Support: Customize the Taskbar](https://support.microsoft.com/en-us/windows/experience/personalization/customize-the-taskbar-in-windows) · [Microsoft Learn: Taskbar Extensions](https://learn.microsoft.com/en-us/windows/win32/shell/taskbar-extensions#jump-lists)

| Action in the image | Windows behavior |
| --- | --- |
| **CurseForge** | The app-name row is a launcher. It requests another launch, which may open a new instance or be handled by the app's existing instance. [Taskbar Extensions](https://learn.microsoft.com/en-us/windows/win32/shell/taskbar-extensions#jump-lists) |
| **Unpin from taskbar** | Removes the pin. A running app still has a taskbar button, so it disappears after the last window closes. An unpinned app that is running offers **Pin to taskbar**. A pinned, closed app remains available as a launcher. [Customize the Taskbar](https://support.microsoft.com/en-us/windows/experience/personalization/customize-the-taskbar-in-windows) |
| **End task** | The optional force-stop action for a running app. On Windows 11 25H2 and later, the toggle is under **Settings → System → Advanced → Taskbar**. Earlier versions placed it under **For Developers**. Microsoft removed the former **Not Responding** confirmation from this command in the July 2024 update. [Advanced Windows Settings](https://learn.microsoft.com/en-us/windows/advanced-settings/) · [Windows Insider: July 2024 update](https://blogs.windows.com/windows-insider/2024/07/11/releasing-windows-11-builds-22621-3951-and-22631-3951-to-the-release-preview-channel/) |
| **Close window** | Requests a regular close. The app can prompt to save changes or cancel. A grouped app can offer **Close all windows** and bring a window forward if it needs attention. [Closing the Window](https://learn.microsoft.com/en-us/windows/win32/learnwin32/closing-the-window) · [Windows Insider: grouped close behavior](https://blogs.windows.com/windows-insider/2022/08/24/announcing-windows-11-insider-preview-build-22621-586-and-22622-586/) |

Shift + right-click opens the window menu. Ctrl + click cycles through windows of a grouped app. [Microsoft Support: Windows keyboard shortcuts](https://support.microsoft.com/en-us/accessibility/windows/keyboard-shortcuts-in-windows)

## Image 2 — taskbar window thumbnail

The card above Edge is a **taskbar thumbnail preview** of the Edge window showing DroidWin. Hover over a running app's taskbar button to show its preview. Select the thumbnail to bring that window forward, including when switching back to a minimized app. Grouped windows can show separate thumbnails, each selecting its own window. Windows 11 also has a close control on taskbar previews. [Windows Insider: preview activation](https://blogs.windows.com/windows-insider/2025/10/21/releasing-windows-11-builds-26100-7015-and-26200-7015-to-the-release-preview-channel/) · [Taskbar Extensions: thumbnails](https://learn.microsoft.com/en-us/windows/win32/shell/taskbar-extensions#thumbnails) · [Windows Insider: close control](https://blogs.windows.com/windows-insider/2021/08/05/announcing-windows-11-insider-preview-build-22000-120/)

**Peek** is the full-size preview that Windows can display when the pointer moves onto a thumbnail itself. The thumbnail can depict actual window content through Desktop Window Manager. The top-right arrows in Image 2 appear unrelated to the thumbnail card. [Microsoft Learn: DWM Thumbnail Overview](https://learn.microsoft.com/en-us/windows/win32/dwm/thumbnail-ovw) · [Microsoft Learn: Peek preview](https://learn.microsoft.com/en-us/windows/win32/api/dwmapi/nf-dwmapi-dwmseticoniclivepreviewbitmap)

## Proposed UBS mapping

- Keep taskbar pin state independent from window state. Right-click an app button for the matching actions. The app name opens or raises the app in the current single-window-per-app model.
- Show a preview only for an open app window, including a minimized one. Anchor it above its taskbar button and render the app's title, icon, and actual in-game window contents. Keep it available as the pointer moves from button to preview. Clicking the card restores and raises the window. Its close control uses the regular close action.
- Expose **End task** only if UBS has a distinct force-stop behavior scoped to that in-game app. The current window model has one normal close operation.
- Measure hover delay, pointer transitions, animation, and dismissal on the target Windows build before claiming exact parity. Microsoft documents changes to preview animations between taskbar app groups. [Windows Insider: November 2025 preview transitions](https://blogs.windows.com/windows-insider/2025/11/17/releasing-windows-11-builds-26100-7296-and-26200-7296-to-the-release-preview-channel/)
