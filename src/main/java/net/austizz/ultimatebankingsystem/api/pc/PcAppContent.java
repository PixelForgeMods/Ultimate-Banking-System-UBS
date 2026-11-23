package net.austizz.ultimatebankingsystem.api.pc;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Client-thread content for one open PC window. The desktop owns the frame.
 * Layout and input use Minecraft GUI coordinates. Preview rendering receives
 * its own rectangle and must leave the live layout and focus untouched.
 */
public interface PcAppContent {
    void layout(int x, int y, int width, int height);

    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    default void renderPreview(GuiGraphics graphics, int x, int y, int width, int height) {}

    default void focusChanged(boolean focused) {}

    default void closed() {}

    /** Called after construction and whenever the app returns to a new desktop screen. */
    default void setServices(PcAppServices services) {}

    /** Called for file extensions registered through PcAppRegistrationEvent. */
    default void openFile(java.util.UUID id) throws java.io.IOException {
        throw new java.io.IOException("This app does not accept files.");
    }

    /** The publisher is scoped to this app and is called on the client thread. */
    default void setNotificationPublisher(java.util.function.Consumer<PcAppNotification> publisher) {}

    default boolean mouseClicked(double x, double y, int button) { return false; }

    default boolean mouseReleased(double x, double y, int button) { return false; }

    default boolean mouseDragged(double x, double y, int button, double dx, double dy) { return false; }

    default boolean mouseScrolled(double x, double y, double dx, double dy) { return false; }

    default boolean keyPressed(int key, int scanCode, int modifiers) { return false; }

    default boolean keyReleased(int key, int scanCode, int modifiers) { return false; }

    default boolean charTyped(char character, int modifiers) { return false; }
}
