# In-game PC apps

Register a client-side NeoForge app with `PcAppRegistrationEvent`. The ID is the app's permanent identity. Use your mod's namespace and keep the ID stable so installations and shortcuts survive upgrades.

```java
@EventBusSubscriber(modid = "example", value = Dist.CLIENT)
public final class ExamplePcApps {
    @SubscribeEvent
    static void register(PcAppRegistrationEvent event) {
        event.register(ResourceLocation.fromNamespaceAndPath("example", "counter"),
                Component.literal("Counter"), PcWindowOptions.DEFAULT, Counter::new);
    }

    private static final class Counter implements PcAppContent {
        private int x;
        private int y;
        private int count;

        @Override
        public void layout(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.drawString(Minecraft.getInstance().font, "Clicks " + count,
                    x + 10, y + 10, 0xFFF5F7FC, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                return false;
            }
            count++;
            return true;
        }
    }
}
```

The factory creates a content instance when the window opens. Keep your app state in that instance. Minimizing or resizing leaves it alive. Implement `closed()` to release resources or save data. The desktop calls `layout(...)` with the content area's position and dimensions in Minecraft GUI pixels. These coordinates also apply to mouse input. Implement `renderPreview(...)` to draw the thumbnail using the rectangle it receives. The same live instance can be visible behind or beside its thumbnail, so preview drawing should leave window state and focus alone.

The registration overload with an icon accepts a PNG resource such as `example:textures/gui/pc/counter.png`. Players can install or remove registered apps through Marketplace. Installations are local to the Minecraft profile and the app code comes from installed client mods.

The API follows the Windows App SDK distinction between a window frame, its hosted content, and its lifecycle. The current UI uses Minecraft widgets and rendering. [Windows windowing overview](https://learn.microsoft.com/en-us/windows/apps/develop/ui/windowing-overview) · [Windows desktop app lifecycle](https://learn.microsoft.com/en-us/windows/apps/develop/launch/app-lifecycle)

## Notifications

Override `setNotificationPublisher` and retain the consumer to post alerts while the app instance is open.

```java
private Consumer<PcAppNotification> notifications = ignored -> {};

@Override
public void setNotificationPublisher(Consumer<PcAppNotification> publisher) {
    notifications = publisher;
}

private void onExported() {
    notifications.accept(new PcAppNotification("Export complete", "Your report is in Documents."));
}
```

Use a nonempty key scoped to the app to update an existing alert in place. Progress accepts percentages from 0 to 100 and the constants `NO_PROGRESS` and `INDETERMINATE`. An optional image refers to a file UUID on the PC drive. Buttons and inline replies invoke app callbacks on the client thread. History preserves the message, image reference, and progress across sessions. App callbacks are runtime-only. The desktop applies the user's banner, sound, history, and Do Not Disturb preferences to each delivery.

## System services API v1

Override `setServices(PcAppServices services)` and use the latest service instance on the Minecraft client thread. The app gets the same stable app ID between launches. `drive()` is the signed-in PC's shared drive. `appData()` is a separate persistent virtual drive for your app on that PC. Both return opaque UUIDs that survive renames and moves within their drive. File operations report failures as `IOException`. Store a UUID when you need to reopen a file, and ask the drive for its current name or path for display.

- **File dialogs.** Send a `PcFileDialog.Request` with a mode, optional starting folder, filename suggestion, file-type filters, and whether Open permits multiple selections. An empty filter extension list matches all files. The owner window must be active. Escape or Cancel does not call the accept callback. Use the optional cancellation callback if the app needs to update its state. Save As returns a parent and filename, plus an existing file ID only after the user confirms overwrite. The caller writes or creates the file inside the accept callback. Throw `IOException` there to show the failure in the dialog.
- **File handlers.** Call `event.registerFileHandler(appId, "txt", ".md")` alongside app registration and implement `openFile(UUID)` in the content instance. Read the file through `services.drive()`. Users can set a default in Settings or choose an app for one open from Files. Call `services.open(fileId)` to use the same routing as Files and Desktop Search.
- **Clipboard.** Read or set `PcClipboard.Text`, `PcClipboard.Image`, or `PcClipboard.Files` through `services.clipboard()`. Image bytes are copied on input and output. File clipboard items refer to files on the shared drive. A successful cut-and-paste in Files clears the item. Clipboard contents live in the current PC session.

The virtual drive API uses PC paths only. The app data store is separated by registered app identity. NeoForge governs the capabilities of the underlying mod. For design references and implementation dependencies, see [PC system services research](pc-system-services-research.md).
