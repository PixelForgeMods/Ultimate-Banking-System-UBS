package net.austizz.ultimatebankingsystem.gui.screens;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.austizz.ultimatebankingsystem.Config;
import net.austizz.ultimatebankingsystem.api.ApiNotificationPlacement;
import net.austizz.ultimatebankingsystem.api.ApiNotificationRequest;
import net.austizz.ultimatebankingsystem.api.ApiNotificationType;
import net.austizz.ultimatebankingsystem.client.NpcSkinClientCache;
import net.austizz.ultimatebankingsystem.client.NpcSkinFilePicker;
import net.austizz.ultimatebankingsystem.client.NotificationClientState;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopButton;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopEditBox;
import net.austizz.ultimatebankingsystem.network.NpcSkinActionPayload;
import net.austizz.ultimatebankingsystem.network.NpcSkinEditorPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;

/** Operator-only server NPC appearance editor. Skin files always originate from the server library. */
public final class NpcSkinEditorScreen extends Screen {
    private static final int BACKDROP = 0xFF07111D;
    private static final int PANEL = 0xFF0C2034;
    private static final int CARD = 0xFF102A42;
    private static final int CARD_ALT = 0xFF0A1B2D;
    private static final int FRAME = 0xFF28547A;
    private static final int CYAN = 0xFF42C8FF;
    private static final int GREEN = 0xFF57E39B;
    private static final int GOLD = 0xFFFFC84A;
    private static final int RED = 0xFFFF7272;
    private static final int TEXT = 0xFFF2F8FF;
    private static final int MUTED = 0xFFAAC3DB;
    private static final int DIM = 0xFF718BA4;

    private NpcSkinEditorPayload payload;
    private String selectedTargetId = "";
    private int targetScroll;
    private int skinScroll;
    private DesktopEditBox lookupInput;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int navLeft;
    private int navTop;
    private int navWidth;
    private int targetViewportTop;
    private int targetViewportBottom;
    private int targetVisibleRows;
    private int targetMaxScroll;
    private int contentLeft;
    private int contentTop;
    private int contentWidth;
    private int libraryLeft;
    private int libraryWidth;
    private int skinsTop;
    private int skinsBottom;
    private int skinColumns;
    private int skinCardWidth;
    private int skinCardHeight;
    private int skinVisibleRows;
    private int skinMaxScroll;
    private int previewLeft;
    private int previewTop;
    private int previewWidth;
    private int previewHeight;
    private PlayerModel<net.minecraft.world.entity.LivingEntity> widePreviewModel;
    private PlayerModel<net.minecraft.world.entity.LivingEntity> slimPreviewModel;
    private float previewYaw = 180.0F;
    private float previewZoom = 1.0F;
    private boolean draggingPreview;
    private double lastPreviewDragX;

    public NpcSkinEditorScreen(NpcSkinEditorPayload payload) {
        super(Component.literal("UBS NPC Appearance"));
        this.payload = payload == null ? new NpcSkinEditorPayload(List.of(), List.of(), "") : payload;
        NpcSkinClientCache.applyEditor(this.payload);
        selectFirstTarget();
        showNotice(this.payload.notice());
    }

    public void refresh(NpcSkinEditorPayload next) {
        if (next == null) return;
        String previous = selectedTargetId;
        payload = next;
        NpcSkinClientCache.applyEditor(next);
        if (target(previous) != null) selectedTargetId = previous;
        else selectFirstTarget();
        skinScroll = 0;
        showNotice(next.notice());
        rebuild();
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        String lookup = lookupInput == null ? "" : lookupInput.getValue();
        clearWidgets();
        panelWidth = Math.min(1100, Math.max(360, width - 18));
        panelHeight = Math.min(650, Math.max(300, height - 18));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        navLeft = panelLeft + 14;
        navTop = panelTop + 70;
        navWidth = Math.min(210, Math.max(148, panelWidth / 5));
        contentLeft = navLeft + navWidth + 14;
        contentTop = panelTop + 70;
        contentWidth = panelLeft + panelWidth - 14 - contentLeft;
        previewWidth = Math.min(250, Math.max(170, contentWidth / 3));
        previewLeft = contentLeft;
        previewTop = contentTop + 58;
        previewHeight = Math.max(120, panelTop + panelHeight - 50 - previewTop);
        libraryLeft = contentLeft + previewWidth + 12;
        libraryWidth = Math.max(120, contentWidth - previewWidth - 12);
        int libraryContentWidth = Math.max(104, libraryWidth - 18);

        addButton(panelLeft + 14, panelTop + panelHeight - 32, 78, 22, "Close", MUTED,
                ignored -> onClose(), "Close the NPC appearance editor.");
        addButton(panelLeft + panelWidth - 112, panelTop + panelHeight - 32, 98, 22, "Refresh Folder", CYAN,
                ignored -> send("REFRESH", "", "", new byte[0]),
                "Rescan config/ultimatebankingsystem/npc_skins for PNG skins pasted into the server folder.");

        NpcSkinEditorPayload.TargetSelection selectedTarget = selectedTarget();
        if (selectedTarget != null) {
            int modelButtonWidth = Math.max(72, Math.min(104, (contentWidth - 24) / 5));
            int modelButtonX = contentLeft + contentWidth - modelButtonWidth * 2 - 8;
            addButton(modelButtonX, contentTop + 10, modelButtonWidth, 24, "Classic",
                    selectedTarget.slimModel() ? CYAN : GREEN,
                    ignored -> send("SET_MODEL", selectedTarget.id(), "CLASSIC", new byte[0]),
                    "Use the standard Steve-style model with wide arms for this NPC role.");
            addButton(modelButtonX + modelButtonWidth + 8, contentTop + 10, modelButtonWidth, 24, "Slim",
                    selectedTarget.slimModel() ? GREEN : CYAN,
                    ignored -> send("SET_MODEL", selectedTarget.id(), "SLIM", new byte[0]),
                    "Use the Alex-style model with narrow arms for this NPC role.");
        }

        targetViewportTop = navTop + 28;
        targetViewportBottom = panelTop + panelHeight - 50;
        targetVisibleRows = Math.max(1, (targetViewportBottom - targetViewportTop) / 40);
        targetMaxScroll = Math.max(0, payload.targets().size() - targetVisibleRows);
        targetScroll = Mth.clamp(targetScroll, 0, targetMaxScroll);
        int selectedIndex = selectedTargetIndex();
        if (selectedIndex >= 0 && selectedIndex < targetScroll) {
            targetScroll = selectedIndex;
        } else if (selectedIndex >= targetScroll + targetVisibleRows) {
            targetScroll = selectedIndex - targetVisibleRows + 1;
        }
        targetScroll = Mth.clamp(targetScroll, 0, targetMaxScroll);
        if (targetMaxScroll > 0) {
            addButton(navLeft + navWidth - 44, navTop + 1, 18, 18, "^", CYAN,
                    ignored -> scrollTargets(-1), "Scroll the NPC role list up.");
            addButton(navLeft + navWidth - 22, navTop + 1, 18, 18, "v", CYAN,
                    ignored -> scrollTargets(1), "Scroll the NPC role list down.");
        }
        int firstTarget = targetScroll;
        int lastTarget = Math.min(payload.targets().size(), firstTarget + targetVisibleRows);
        for (int index = firstTarget; index < lastTarget; index++) {
            NpcSkinEditorPayload.TargetSelection target = payload.targets().get(index);
            boolean selected = target.id().equals(selectedTargetId);
            int targetY = targetViewportTop + (index - firstTarget) * 40;
            DesktopButton button = addButton(navLeft, targetY, navWidth, 34, target.displayName(),
                    selected ? GREEN : CYAN, ignored -> {
                        selectedTargetId = target.id();
                        skinScroll = 0;
                        rebuild();
                    }, "Select " + target.displayName() + " to choose its shared skin.");
            button.setLabelOffset(0, 0);
        }

        int toolsY = contentTop + 54;
        int buttonWidth = Math.max(76, Math.min(132, (libraryWidth - 12) / 3));
        lookupInput = new DesktopEditBox(font, libraryLeft, toolsY, Math.max(92, libraryWidth - buttonWidth * 2 - 16),
                26, Component.literal("Minecraft username or UUID"));
        lookupInput.setHint(Component.literal("Username or UUID"));
        lookupInput.setValue(lookup);
        lookupInput.setMaxLength(96);
        addRenderableWidget(lookupInput);
        int lookupButtonX = lookupInput.getX() + lookupInput.getWidth() + 6;
        addButton(lookupButtonX, toolsY, buttonWidth, 26, "Lookup Skin", GOLD,
                ignored -> send("LOOKUP", "", lookupInput.getValue(), new byte[0]),
                "Downloads the current official Minecraft skin for this username or UUID, then saves it on the server.");
        addButton(lookupButtonX + buttonWidth + 6, toolsY, buttonWidth, 26, "Upload PNG", GREEN,
                ignored -> NpcSkinFilePicker.choose(message -> {}),
                "Choose a 64x64 or 64x32 PNG from this computer and upload it into the server NPC skin library.");

        skinsTop = toolsY + 46;
        skinsBottom = panelTop + panelHeight - 50;
        skinColumns = Math.max(1, Math.min(4, libraryContentWidth / 138));
        skinCardWidth = Math.max(104, (libraryContentWidth - (skinColumns - 1) * 8) / skinColumns);
        skinCardHeight = 118;
        skinVisibleRows = Math.max(1, (skinsBottom - skinsTop) / (skinCardHeight + 8));
        List<SkinOption> options = skinOptions();
        skinMaxScroll = Math.max(0, (options.size() + skinColumns - 1) / skinColumns - skinVisibleRows);
        skinScroll = Mth.clamp(skinScroll, 0, skinMaxScroll);
        if (skinMaxScroll > 0) {
            addButton(libraryLeft + libraryWidth - 46, skinsTop - 22, 20, 18, "^", CYAN,
                    ignored -> scrollLibrary(-1), "Scroll the skin library up.");
            addButton(libraryLeft + libraryWidth - 22, skinsTop - 22, 20, 18, "v", CYAN,
                    ignored -> scrollLibrary(1), "Scroll the skin library down.");
        }
        int first = skinScroll * skinColumns;
        int last = Math.min(options.size(), first + skinVisibleRows * skinColumns);
        for (int index = first; index < last; index++) {
            SkinOption option = options.get(index);
            int relative = index - first;
            int column = relative % skinColumns;
            int row = relative / skinColumns;
            int x = libraryLeft + column * (skinCardWidth + 8);
            int y = skinsTop + row * (skinCardHeight + 8);
            DesktopButton select = addButton(x, y, skinCardWidth, skinCardHeight, "", option.selected() ? GREEN : CYAN,
                    ignored -> send("ASSIGN", selectedTargetId, option.skinId(), new byte[0]), option.tooltip());
            select.setChromeVisible(false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BACKDROP);
        drawPanel(graphics, panelLeft, panelTop, panelWidth, panelHeight, CYAN);
        graphics.fill(panelLeft + 1, panelTop + 1, panelLeft + panelWidth - 1, panelTop + 52, 0xFF0A1929);
        graphics.fill(panelLeft + 1, panelTop + 51, panelLeft + panelWidth - 1, panelTop + 53, CYAN);
        graphics.drawString(font, "NPC APPEARANCE LIBRARY", panelLeft + 18, panelTop + 14, TEXT, false);
        graphics.drawString(font, "Server managed skins", panelLeft + 18, panelTop + 30, MUTED, false);
        graphics.drawString(font, "Shared by every NPC in the selected role", panelLeft + panelWidth - 270,
                panelTop + 19, DIM, false);

        drawPanel(graphics, navLeft - 6, navTop - 6, navWidth + 12,
                Math.max(58, panelTop + panelHeight - 52 - navTop + 6), FRAME);
        graphics.drawString(font, "NPC TYPES", navLeft + 10, navTop + 4, CYAN, false);
        drawTargetScrollIndicator(graphics);
        renderContent(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The editor paints an opaque shell itself; inheriting Screen's blur makes the rest of the UI unreadable.
    }

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY) {
        NpcSkinEditorPayload.TargetSelection selected = selectedTarget();
        if (selected == null) {
            drawPanel(graphics, contentLeft, contentTop, contentWidth, 80, GOLD);
            graphics.drawString(font, "No NPC roles are available.", contentLeft + 14, contentTop + 18, GOLD, false);
            return;
        }
        drawPanel(graphics, contentLeft, contentTop, contentWidth, 46, selected.selectedSkinId().isBlank() ? CYAN : GREEN);
        graphics.drawString(font, selected.displayName(), contentLeft + 14, contentTop + 11, TEXT, false);
        String source = selected.selectedSkinId().isBlank() ? "Using built-in default" : "Using " + selected.selectedSkinId();
        graphics.drawString(font, source, contentLeft + 14, contentTop + 27,
                selected.selectedSkinId().isBlank() ? MUTED : GREEN, false);
        graphics.drawString(font, "ADD A SKIN", contentLeft, contentTop + 48, CYAN, false);
        graphics.drawString(font, "SKIN LIBRARY", libraryLeft, skinsTop - 18, CYAN, false);

        drawPreview(graphics, selected, mouseX, mouseY);

        List<SkinOption> options = skinOptions();
        int first = skinScroll * skinColumns;
        int last = Math.min(options.size(), first + skinVisibleRows * skinColumns);
        for (int index = first; index < last; index++) {
            SkinOption option = options.get(index);
            int relative = index - first;
            int x = libraryLeft + (relative % skinColumns) * (skinCardWidth + 8);
            int y = skinsTop + (relative / skinColumns) * (skinCardHeight + 8);
            drawSkinCard(graphics, option, x, y, mouseX, mouseY, selected.slimModel());
        }
        if (options.isEmpty()) {
            graphics.drawString(font, "No skins are available yet.", libraryLeft, skinsTop + 16, TEXT, false);
        }
        drawLibraryScrollIndicator(graphics, options.size());
    }

    private void drawPreview(GuiGraphics graphics, NpcSkinEditorPayload.TargetSelection target,
                             int mouseX, int mouseY) {
        drawPanel(graphics, previewLeft, previewTop, previewWidth, previewHeight, GREEN);
        graphics.fill(previewLeft + 3, previewTop + 3, previewLeft + previewWidth - 3,
                previewTop + previewHeight - 3, CARD_ALT);
        for (int y = previewTop + 12; y < previewTop + previewHeight; y += 18) {
            graphics.fill(previewLeft + 4, y, previewLeft + previewWidth - 4, y + 1, 0x2432A7D1);
        }
        for (int x = previewLeft + 12; x < previewLeft + previewWidth; x += 18) {
            graphics.fill(x, previewTop + 4, x + 1, previewTop + previewHeight - 4, 0x2432A7D1);
        }
        graphics.drawString(font, "FULL-BODY PREVIEW", previewLeft + 12, previewTop + 10, GREEN, false);
        graphics.drawString(font, target.selectedSkinId().isBlank() ? "BUILT-IN SKIN" : "SELECTED LIBRARY SKIN",
                previewLeft + 12, previewTop + 25, MUTED, false);
        drawDragIcon(graphics, previewLeft + 12, previewTop + 40);
        graphics.drawString(font, "DRAG TO ROTATE", previewLeft + 30, previewTop + 39, TEXT, false);
        graphics.drawString(font, "SCROLL TO ZOOM", previewLeft + 30, previewTop + 52, DIM, false);
        int modelTop = previewTop + 74;
        int modelBottom = previewTop + previewHeight - 8;
        graphics.enableScissor(previewLeft + 4, modelTop, previewLeft + previewWidth - 4, modelBottom);
        float previewScale = Math.max(120.0F, (modelBottom - modelTop) * 0.5F) * previewZoom;
        renderPlayerModel(graphics, target, previewLeft + previewWidth / 2,
                modelBottom, previewScale, previewYaw);
        graphics.disableScissor();
    }

    private void renderPlayerModel(GuiGraphics graphics, NpcSkinEditorPayload.TargetSelection target,
                                   int centerX, int bottomY, float scale, float yaw) {
        renderPlayerModel(graphics, NpcSkinClientCache.preview(target.selectedSkinId(),
                ResourceLocation.tryParse(target.defaultTextureId())), centerX, bottomY, scale, yaw, target.slimModel());
    }

    private void renderPlayerModel(GuiGraphics graphics, ResourceLocation texture,
                                   int centerX, int bottomY, float scale, float yaw, boolean slim) {
        PlayerModel<net.minecraft.world.entity.LivingEntity> previewModel = slim ? slimPreviewModel : widePreviewModel;
        if (previewModel == null) {
            previewModel = new PlayerModel<>(Minecraft.getInstance().getEntityModels()
                    .bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
            if (slim) slimPreviewModel = previewModel;
            else widePreviewModel = previewModel;
        }
        if (texture == null) return;
        var player = Minecraft.getInstance().player;
        if (player != null) {
            previewModel.setupAnim(player, 0.0F, 0.0F, player.tickCount, 0.0F, 0.0F);
        }
        previewModel.crouching = false;
        previewModel.young = false;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        // PlayerModel is authored from neck to feet: head occupies -8..0 Y and feet end at +24 Y.
        // ModelPart converts those skin pixels into 1/16th blocks before drawing.
        // GUI Y already grows downward, so a positive scale and a feet-pivot offset keeps it upright.
        pose.translate(centerX, bottomY - 1.5F * scale, 120.0F);
        pose.scale(scale, scale, scale);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderType renderType = RenderType.entityTranslucent(texture);
        VertexConsumer consumer = buffers.getBuffer(renderType);
        previewModel.renderToBuffer(pose, consumer, 0xF000F0, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
        buffers.endBatch(renderType);
        pose.popPose();
    }

    private void drawSkinCard(GuiGraphics graphics, SkinOption option, int x, int y, int mouseX, int mouseY,
                              boolean slimModel) {
        boolean hovered = mouseX >= x && mouseX < x + skinCardWidth && mouseY >= y && mouseY < y + skinCardHeight;
        int accent = option.selected() ? GREEN : hovered ? CYAN : FRAME;
        drawPanel(graphics, x, y, skinCardWidth, skinCardHeight, accent);
        ResourceLocation texture = NpcSkinClientCache.preview(option.skinId(), option.fallback());
        graphics.fill(x + 10, y + 10, x + skinCardWidth - 10, y + 66, CARD_ALT);
        graphics.enableScissor(x + 10, y + 10, x + skinCardWidth - 10, y + 66);
        renderPlayerModel(graphics, texture, x + skinCardWidth / 2, y + 64, 24.0F, 180.0F, slimModel);
        graphics.disableScissor();
        drawFit(graphics, option.label(), x + 10, y + 76, skinCardWidth - 20, TEXT);
        drawFit(graphics, option.selected() ? "SELECTED" : option.defaultSkin() ? "BUILT-IN" : "CLICK TO APPLY",
                x + 10, y + 94, skinCardWidth - 20, option.selected() ? GREEN : option.defaultSkin() ? MUTED : CYAN);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (insidePreview(mouseX, mouseY) && verticalAmount != 0.0D) {
            previewZoom = Mth.clamp(previewZoom + (verticalAmount > 0.0D ? 0.08F : -0.08F), 0.72F, 1.28F);
            return true;
        }
        if (mouseX >= libraryLeft && mouseX <= libraryLeft + libraryWidth
                && mouseY >= skinsTop && mouseY <= skinsBottom && verticalAmount != 0.0D) {
            scrollLibrary(verticalAmount < 0.0D ? 1 : -1);
            return true;
        }
        if (mouseX >= navLeft && mouseX <= navLeft + navWidth
                && mouseY >= targetViewportTop && mouseY <= targetViewportBottom
                && verticalAmount != 0.0D) {
            scrollTargets(verticalAmount < 0.0D ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && insidePreview(mouseX, mouseY)) {
            draggingPreview = true;
            lastPreviewDragX = mouseX;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (draggingPreview && button == 0) {
            previewYaw = Mth.wrapDegrees(previewYaw + (float) ((mouseX - lastPreviewDragX) * 0.8D));
            lastPreviewDragX = mouseX;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingPreview) {
            draggingPreview = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean insidePreview(double mouseX, double mouseY) {
        return mouseX >= previewLeft && mouseX < previewLeft + previewWidth
                && mouseY >= previewTop && mouseY < previewTop + previewHeight;
    }

    private void scrollLibrary(int delta) {
        int next = Mth.clamp(skinScroll + delta, 0, skinMaxScroll);
        if (next != skinScroll) {
            skinScroll = next;
            rebuild();
        }
    }

    private void scrollTargets(int delta) {
        int next = Mth.clamp(targetScroll + delta, 0, targetMaxScroll);
        if (next != targetScroll) {
            targetScroll = next;
            rebuild();
        }
    }

    private void drawTargetScrollIndicator(GuiGraphics graphics) {
        if (targetMaxScroll <= 0 || payload.targets().isEmpty()) return;
        int trackX = navLeft + navWidth - 6;
        int trackTop = targetViewportTop;
        int trackBottom = targetViewportBottom;
        int trackHeight = Math.max(1, trackBottom - trackTop);
        int thumbHeight = Math.max(18, trackHeight * targetVisibleRows / Math.max(1, payload.targets().size()));
        int thumbY = trackTop + (trackHeight - thumbHeight) * targetScroll / targetMaxScroll;
        graphics.fill(trackX, trackTop, trackX + 3, trackBottom, 0xFF0A1929);
        graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbHeight, CYAN);
    }

    private void drawLibraryScrollIndicator(GuiGraphics graphics, int optionCount) {
        if (skinMaxScroll <= 0 || optionCount <= 0) return;
        int trackX = libraryLeft + libraryWidth - 10;
        int trackTop = skinsTop;
        int trackBottom = skinsBottom;
        int totalRows = (optionCount + skinColumns - 1) / skinColumns;
        int trackHeight = Math.max(1, trackBottom - trackTop);
        int thumbHeight = Math.max(18, trackHeight * skinVisibleRows / Math.max(1, totalRows));
        int thumbY = trackTop + (trackHeight - thumbHeight) * skinScroll / skinMaxScroll;
        graphics.fill(trackX, trackTop, trackX + 4, trackBottom, 0xFF0A1929);
        graphics.fill(trackX, thumbY, trackX + 4, thumbY + thumbHeight, CYAN);
    }

    private void showNotice(String notice) {
        if (notice == null || notice.isBlank()) return;
        String message = notice.trim();
        String lower = message.toLowerCase(Locale.ROOT);
        boolean failure = lower.contains("rejected") || lower.contains("could not")
                || lower.contains("failed") || lower.contains("unknown") || lower.contains("valid");
        NotificationClientState.showLocal(ApiNotificationRequest.builder(
                        failure ? ApiNotificationType.ERROR : ApiNotificationType.SUCCESS, message)
                .id("npc-skin-editor-status")
                .channel("npc-skins")
                .source("NPC Appearance")
                .title(failure ? "NPC Skin Update Failed" : "NPC Skin Updated")
                .placement(ApiNotificationPlacement.TOP_RIGHT)
                .durationMs(failure ? 6_500 : 4_200)
                .replaceExisting(true)
                .build());
    }

    private void drawDragIcon(GuiGraphics graphics, int x, int y) {
        int color = 0xFFBBD3E8;
        graphics.fill(x + 7, y, x + 9, y + 16, color);
        graphics.fill(x, y + 7, x + 16, y + 9, color);
        graphics.fill(x + 5, y, x + 11, y + 2, color);
        graphics.fill(x + 5, y + 14, x + 11, y + 16, color);
        graphics.fill(x, y + 5, x + 2, y + 11, color);
        graphics.fill(x + 14, y + 5, x + 16, y + 11, color);
    }

    private List<SkinOption> skinOptions() {
        NpcSkinEditorPayload.TargetSelection target = selectedTarget();
        if (target == null) return List.of();
        List<SkinOption> options = new java.util.ArrayList<>();
        options.add(new SkinOption("", "Default skin", target.defaultTextureId(), true,
                target.selectedSkinId().isBlank(), "Restore the built-in UBS appearance for " + target.displayName() + "."));
        for (NpcSkinEditorPayload.SkinAsset skin : payload.skins()) {
            options.add(new SkinOption(skin.id(), skin.displayName(), target.defaultTextureId(), false,
                    skin.id().equals(target.selectedSkinId()), "Apply " + skin.displayName() + " to every "
                    + target.displayName() + " NPC."));
        }
        return options;
    }

    private NpcSkinEditorPayload.TargetSelection selectedTarget() {
        return target(selectedTargetId);
    }

    private NpcSkinEditorPayload.TargetSelection target(String id) {
        return payload.targets().stream().filter(target -> target.id().equals(id)).findFirst().orElse(null);
    }

    private int selectedTargetIndex() {
        for (int index = 0; index < payload.targets().size(); index++) {
            if (payload.targets().get(index).id().equals(selectedTargetId)) return index;
        }
        return -1;
    }

    private void selectFirstTarget() {
        if (target(selectedTargetId) != null) return;
        selectedTargetId = payload.targets().isEmpty() ? "" : payload.targets().getFirst().id();
    }

    private void send(String action, String target, String value, byte[] png) {
        PacketDistributor.sendToServer(new NpcSkinActionPayload(action, target, value, png));
    }

    private DesktopButton addButton(int x, int y, int width, int height, String label, int accent,
                                    java.util.function.Consumer<DesktopButton> action, String tooltip) {
        DesktopButton button = new DesktopButton(x, y, Math.max(1, width), Math.max(1, height),
                Component.literal(label), accent, action);
        if (tooltip != null && !tooltip.isBlank()) button.setTooltip(Tooltip.create(Component.literal(tooltip)));
        return addRenderableWidget(button);
    }

    private void drawPanel(GuiGraphics graphics, int x, int y, int width, int height, int accent) {
        if (width <= 2 || height <= 2) return;
        graphics.fill(x, y, x + width, y + height, accent);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, PANEL);
        graphics.fill(x + 3, y + 3, x + width - 3, y + 4, 0x5542C8FF);
    }

    private void drawFit(GuiGraphics graphics, String text, int x, int y, int maximum, int color) {
        String value = text == null ? "" : text;
        if (font.width(value) > maximum) value = font.plainSubstrByWidth(value, Math.max(0, maximum - font.width("..."))) + "...";
        graphics.drawString(font, value, x, y, color, false);
    }

    private record SkinOption(String skinId, String label, String fallbackTextureId, boolean defaultSkin,
                              boolean selected, String tooltip) {
        private ResourceLocation fallback() {
            return ResourceLocation.tryParse(fallbackTextureId) == null
                    ? ResourceLocation.withDefaultNamespace("textures/entity/steve.png")
                    : ResourceLocation.tryParse(fallbackTextureId);
        }
    }
}
