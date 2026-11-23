package net.austizz.ultimatebankingsystem.gui.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import net.austizz.ultimatebankingsystem.network.PcLoginPayload.Action;
import net.austizz.ultimatebankingsystem.network.PcLoginPayload.Request;
import net.austizz.ultimatebankingsystem.network.PcLoginPayload.Response;
import net.austizz.ultimatebankingsystem.network.PcLoginPayload.Result;
import net.austizz.ultimatebankingsystem.pc.PcLoginService;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static net.austizz.ultimatebankingsystem.gui.screens.PcDesktopScreen.label;
import static net.austizz.ultimatebankingsystem.gui.screens.PcDesktopScreen.roundRect;

final class PcLoginScreen extends Screen {
    private static final ResourceLocation PROFILE = ResourceLocation.fromNamespaceAndPath(
            "ultimatebankingsystem", "textures/gui/pc/profile.png");
    private static final int TEXT = 0xFFF6F8FF;
    private static final int MUTED = 0xFFD5E3FF;
    private static final int ACCENT = 0xFF9BDFFF;
    private static final int ERROR = 0xFFFFBFBA;
    private static final int FIELD_HEIGHT = 21;
    private static int nextRequestId;

    private final GlobalPos position;

    boolean isFor(GlobalPos pc) { return position.equals(pc); }
    private final List<SecretField> fields = new ArrayList<>();
    private final List<LoginButton> actions = new ArrayList<>();
    private Action mode = Action.STATUS;
    private Action nextMode;
    private boolean pinSet;
    private boolean pending;
    private int requestId;
    private long deadline;
    private String message = "";
    private int avatarSize;
    private int top;
    private int formY;
    private int messageY;
    private boolean error;
    private PcPhotoTexture photoBackground;
    private boolean backgroundLoaded;
    private PcNotifications lockNotifications;

    PcLoginScreen(GlobalPos position) {
        super(label("PC sign-in"));
        this.position = position;
    }

    @Override
    protected void init() {
        if (!backgroundLoaded) {
            backgroundLoaded = true;
            loadPhotoBackground();
        }
        fields.clear();
        actions.clear();
        avatarSize = Math.clamp(height / 4, 46, 112);
        int formWidth = Math.clamp(width / 4, 124, 210);
        int left = (width - formWidth) / 2;
        top = Math.max(8, (height - avatarSize - 146) / 2 - 12);
        formY = top + avatarSize + 34;
        int y = formY;
        switch (mode) {
            case STATUS -> actions.add(addRenderableWidget(new LoginButton(left, y, formWidth, "Retry", false,
                    () -> send(Action.STATUS, "", ""))));
            case CREATE_PASSWORD -> {
                field(left, y, formWidth, "New password", false);
                field(left, y += 25, formWidth, "Confirm password", false);
            }
            case PASSWORD -> field(left, y, formWidth, "Password", false);
            case PIN -> field(left, y, formWidth, "PIN", true);
            case CREATE_PIN -> {
                field(left, y, formWidth, "Password", false);
                field(left, y += 25, formWidth, "New PIN", true);
                field(left, y += 25, formWidth, "Confirm PIN", true);
            }
        }
        if (mode != Action.STATUS) {
            String primary = switch (mode) {
                case CREATE_PASSWORD -> "Create password";
                case CREATE_PIN -> "Create PIN";
                default -> "Sign in";
            };
            actions.add(addRenderableWidget(new LoginButton(left, y += 27, formWidth, primary, false, this::submit)));
            if (mode != Action.CREATE_PASSWORD) {
                Action alternative = mode == Action.PASSWORD ? (pinSet ? Action.PIN : Action.CREATE_PIN) : Action.PASSWORD;
                String alternateLabel = switch (alternative) {
                    case PIN -> "Sign in with PIN";
                    case CREATE_PIN -> "Set up a PIN";
                    default -> "Sign in with password";
                };
                actions.add(addRenderableWidget(new LoginButton(left, y += 24, formWidth, alternateLabel, true,
                        () -> nextMode = alternative)));
            }
        }
        messageY = y + 25;

        var exit = new LoginButton(width - 34, height - 32, 24, "Exit PC", true, this::onClose);
        exit.power = true;
        exit.setTooltip(Tooltip.create(label("Exit PC")));
        addRenderableWidget(exit);

        if (mode == Action.STATUS && requestId == 0) {
            send(Action.STATUS, "", "");
        }
        updateControls();
        if (!fields.isEmpty() && !pending) {
            setInitialFocus(fields.getFirst());
        }
    }

    private void field(int x, int y, int width, String hint, boolean numeric) {
        fields.add(addRenderableWidget(new SecretField(x, y, width, hint, numeric)));
    }

    private void submit() {
        if (pending || mode == Action.STATUS) {
            return;
        }
        String secret = fields.getFirst().getValue();
        String pin = mode == Action.CREATE_PIN ? fields.get(1).getValue() : "";
        if (mode == Action.CREATE_PASSWORD && !secret.equals(fields.get(1).getValue())) {
            showError("Passwords do not match.");
        } else if (mode == Action.CREATE_PIN && !pin.equals(fields.get(2).getValue())) {
            showError("PINs do not match.");
        } else if (mode != Action.PIN && !PcLoginService.validPassword(secret)) {
            showError("Enter a password with 8–64 characters.");
        } else if ((mode == Action.PIN || mode == Action.CREATE_PIN)
                && !PcLoginService.validPin(mode == Action.PIN ? secret : pin)) {
            showError("Enter a PIN with 4–8 digits.");
        } else {
            send(mode, secret, pin);
        }
    }

    private void send(Action action, String secret, String pin) {
        pending = true;
        error = false;
        message = action == Action.STATUS ? "Checking PC sign-in…" : "Signing in…";
        deadline = Util.getMillis() + 10_000;
        requestId = ++nextRequestId;
        clearFields();
        updateControls();
        PacketDistributor.sendToServer(new Request(requestId, position, action, secret, pin));
    }

    void accept(Response response) {
        if (!pending || requestId != response.id()) {
            return;
        }
        pending = false;
        if (response.result() == Result.SIGNED_IN) {
            OwnerPcScreenHelper.openSignedInDesktop(position);
            return;
        }
        if (response.result() == Result.UNAVAILABLE) {
            mode = Action.STATUS;
            showError("Move closer to this PC and try again.");
        } else {
            pinSet = response.pinSet();
            if (response.result() == Result.READY) {
                mode = response.passwordSet() ? (pinSet ? Action.PIN : Action.PASSWORD) : Action.CREATE_PASSWORD;
                error = false;
                message = "";
            } else {
                showError(switch (response.result()) {
                    case INCORRECT -> mode == Action.PIN ? "Incorrect PIN." : "Incorrect password.";
                    case WAIT -> "Wait a moment, then try again.";
                    default -> "Check your sign-in details and try again.";
                });
            }
        }
        rebuildWidgets();
    }

    private void updateControls() {
        fields.forEach(field -> field.setEditable(!pending));
        actions.forEach(button -> button.active = !pending);
    }

    private void showError(String text) {
        error = true;
        message = text;
    }

    @Override
    public void tick() {
        if (nextMode != null) {
            clearFields();
            mode = nextMode;
            nextMode = null;
            message = "";
            error = false;
            rebuildWidgets();
        }
        if (pending && Util.getMillis() >= deadline) {
            pending = false;
            mode = Action.STATUS;
            showError("The PC did not respond. Try again.");
            rebuildWidgets();
        }
    }

    private void clearFields() {
        fields.forEach(field -> field.setValue(""));
    }

    @Override
    public void removed() {
        clearFields();
        if (photoBackground != null) {
            photoBackground.close();
            photoBackground = null;
        }
        super.removed();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) && getFocused() instanceof SecretField) {
            submit();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (photoBackground == null) PcDesktopScreen.drawWallpaper(graphics, width, height);
        else photoBackground.drawBackground(graphics, width, height);
        renderBlurredBackground(partialTick);
        graphics.fill(0, 0, width, height, 0x8F071A4C);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(PROFILE, (width - avatarSize) / 2, top, avatarSize, avatarSize,
                0f, 0f, 192, 192, 192, 192);
        RenderSystem.disableBlend();

        if (minecraft.player != null) {
            Component player = Component.literal(minecraft.player.getGameProfile().getName())
                    .withStyle(label("PC").getStyle());
            graphics.pose().pushPose();
            graphics.pose().translate(width / 2f, top + avatarSize + 11f, 1);
            graphics.pose().scale(1.5f, 1.5f, 1);
            graphics.drawString(font, player, -font.width(player) / 2, 0, TEXT, false);
            graphics.pose().popPose();
        }
        renderLockNotifications(graphics);
        String status = !message.isEmpty() ? message : switch (mode) {
            case CREATE_PASSWORD -> "Use 8–64 characters for your PC password.";
            case CREATE_PIN -> "Use 4–8 digits for your PC PIN.";
            default -> "";
        };
        if (!status.isEmpty()) {
            Component text = label(status);
            graphics.drawString(font, text, (width - font.width(text)) / 2, messageY, error ? ERROR : MUTED, false);
        }
    }

    private void renderLockNotifications(GuiGraphics g) {
        if (lockNotifications == null) return;
        var entries = lockNotifications.lockEntries();
        if (entries.isEmpty()) return;
        int cardWidth = Math.min(170, (width - 180) / 2 - 10);
        if (cardWidth < 85 || top < 14) {
            g.drawCenteredString(font, label("New notifications"), width / 2, 4, MUTED);
            return;
        }
        int left = width - cardWidth - 8, y = 8;
        for (var entry : entries) {
            boolean hidden = lockNotifications.hideLockContent || lockNotifications.app(entry.app).hideLockContent;
            roundRect(g, left, y, cardWidth, 28, 0xE92B3B54);
            var sender = label(hidden ? "New notification" : entry.appName);
            g.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(sender, cardWidth - 16)),
                    left + 8, y + (hidden ? 10 : 4), TEXT, false);
            if (!hidden) {
                g.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(label(entry.payload.title()), cardWidth - 16)),
                        left + 8, y + 16, MUTED, false);
            }
            y += 32;
        }
    }

    private void loadPhotoBackground() {
        var path = PcDesktopPreferences.path(minecraft, position);
        if (path == null) return;
        var preferences = new PcDesktopWindows<PcDesktopScreen.App>();
        PcDesktopPreferences.load(path, preferences, List.of());
        lockNotifications = OwnerPcScreenHelper.notifications(position, preferences.notifications);
        if (preferences.photoLockScreen == null) return;
        try {
            var drive = new PcFileSystem(path.resolveSibling(path.getFileName() + ".drive"));
            photoBackground = PcPhotoTexture.load(drive, preferences.photoLockScreen);
        } catch (java.io.IOException ignored) {
            // Use the default background if the saved image is missing.
        }
    }

    private final class SecretField extends EditBox {
        SecretField(int x, int y, int w, String hint, boolean numeric) {
            super(PcLoginScreen.this.font, x + 6, y + 5, w - 12, FIELD_HEIGHT - 5, label(hint));
            setBordered(false);
            setTextShadow(false);
            setTextColor(TEXT);
            setHint(label(hint));
            setMaxLength(numeric ? 8 : 64);
            if (numeric) {
                setFilter(value -> value.chars().allMatch(c -> c >= '0' && c <= '9'));
            }
            setFormatter((value, offset) -> Component.literal("•".repeat(value.length())).getVisualOrderText());
        }

        @Override
        public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX() - 6;
            int y = getY() - 5;
            int w = getWidth() + 12;
            roundRect(graphics, x, y, w, FIELD_HEIGHT, isFocused() ? ACCENT : 0xFF496082);
            roundRect(graphics, x + 1, y + 1, w - 2, FIELD_HEIGHT - 2, 0xFF162547);
            if (isFocused() && getValue().isEmpty()) {
                graphics.drawString(font, getMessage(), getX() + 8, getY(), MUTED, false);
            }
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean keyPressed(int key, int scanCode, int modifiers) {
            return isCut(key) || isCopy(key) || super.keyPressed(key, scanCode, modifiers);
        }

        @Override
        protected MutableComponent createNarrationMessage() {
            return getMessage().copy();
        }
    }

    private final class LoginButton extends AbstractButton {
        private final Runnable action;
        private final boolean link;
        private boolean power;

        LoginButton(int x, int y, int w, String name, boolean link, Runnable action) {
            super(x, y, w, FIELD_HEIGHT, label(name));
            this.link = link;
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (!link || isHoveredOrFocused()) {
                roundRect(graphics, getX(), getY(), width, height,
                        isFocused() ? ACCENT : isHovered() ? 0xFF3E5F93 : 0xFF324B7A);
            }
            if (power) {
                PcDesktopScreen.drawPowerIcon(graphics, getX() + 5, getY() + 4, 14);
            } else {
                Component title = getMessage();
                graphics.drawString(font, title, getX() + (width - font.width(title)) / 2,
                        getY() + 6, isFocused() ? 0xFF102442 : active ? TEXT : MUTED, false);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
