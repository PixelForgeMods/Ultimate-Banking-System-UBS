package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.gui.widgets.DesktopButton;
import net.austizz.ultimatebankingsystem.menu.VendingMachineStockMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Styled container screen for a vending-machine compartment. Item movement is delegated to
 * {@link AbstractContainerScreen}, which keeps the normal Minecraft cursor and drag semantics.
 */
public final class VendingMachineStockScreen extends AbstractContainerScreen<VendingMachineStockMenu> {
    private static final int COLOR_BACKGROUND = 0xFF081827;
    private static final int COLOR_FRAME = 0xFF245278;
    private static final int COLOR_PANEL = 0xFF0D2238;
    private static final int COLOR_CARD = 0xFF122D47;
    private static final int COLOR_TEXT = 0xFFF2F7FF;
    private static final int COLOR_MUTED = 0xFF9EB7D0;
    private static final int COLOR_CYAN = 0xFF42C8FF;
    private static final int COLOR_GREEN = 0xFF59E29A;
    private static final int COLOR_GOLD = 0xFFF2C14E;
    private static final int COLOR_SLOT_PLAYER = 0xFF102A42;
    private static final int COLOR_SLOT_STOCK = 0xFF0A1929;
    private static final int COLOR_SLOT_CREATIVE = 0xFF1F2630;
    private static final int COLOR_SLOT_HOVER = 0xFF8BE7FF;
    private static final float ITEM_SCALE = 1.25F;

    private static final int SLOT_GRID_LEFT = VendingMachineStockMenu.SLOT_GRID_LEFT;

    public VendingMachineStockScreen(VendingMachineStockMenu menu,
                                     Inventory playerInventory,
                                     Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 600;
        this.imageHeight = 330;
        this.titleLabelX = 18;
        this.titleLabelY = 11;
    }

    @Override
    protected void init() {
        this.imageWidth = 600;
        this.imageHeight = 330;
        super.init();
        addRenderableWidget(new DesktopButton(
                leftPos + 18,
                topPos + imageHeight - 34,
                118,
                24,
                Component.literal("Close"),
                COLOR_CYAN,
                ignored -> onClose()
        ));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(-1, -1, imageWidth + 1, imageHeight + 1, COLOR_FRAME);
        graphics.fill(0, 0, imageWidth, imageHeight, COLOR_PANEL);
        graphics.fill(1, 1, imageWidth - 1, 58, 0xFF0A1D30);
        graphics.fill(1, 57, imageWidth - 1, 58, COLOR_CYAN);

        drawSection(graphics, 24, 62, imageWidth - 24, 154);
        drawSection(graphics, 24, 158, imageWidth - 24, 288);

        drawSlotFrames(graphics, 0, menu.getStockSlotCount(), menu.isCreative() ? COLOR_GOLD : COLOR_GREEN,
                mouseX, mouseY);
        drawSlotFrames(graphics, menu.getStockSlotCount(), menu.slots.size(), COLOR_FRAME, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, "VENDING MACHINE STOCK", titleLabelX, titleLabelY, COLOR_TEXT, false);
        String itemName = menu.getDisplayItem().isEmpty()
                ? "Unconfigured item"
                : fit(menu.getDisplayItem().getHoverName().getString(), 190);
        graphics.drawString(font,
                "SLOT " + (menu.getDisplaySlot() + 1) + "  |  " + itemName,
                18,
                30,
                COLOR_MUTED,
                false);

        String stockLabel = menu.isCreative() ? "CREATIVE STOCK" : "OWNER STOCK";
        graphics.drawString(font, stockLabel,
                imageWidth - font.width(stockLabel) - 18,
                10,
                menu.isCreative() ? COLOR_GOLD : COLOR_GREEN,
                false);

        String quantity = menu.isCreative()
                ? "TOTAL  INFINITE"
                : "TOTAL  " + menu.getStockItemCount();
        graphics.drawString(font, quantity,
                imageWidth - font.width(quantity) - 18,
                30,
                menu.isCreative() ? COLOR_GOLD : COLOR_MUTED,
                false);

        graphics.drawString(font, "MACHINE STOCK", SLOT_GRID_LEFT, 64, COLOR_CYAN, false);
        graphics.drawString(font, "YOUR INVENTORY", SLOT_GRID_LEFT, 164, COLOR_CYAN, false);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, COLOR_BACKGROUND);
        graphics.pose().pushPose();
        graphics.pose().translate(leftPos, topPos, 0.0F);
        renderBg(graphics, partialTick, mouseX, mouseY);
        graphics.pose().popPose();
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        graphics.pose().pushPose();
        graphics.pose().translate(slot.x + 9.0F, slot.y + 9.0F, 0.0F);
        graphics.pose().scale(ITEM_SCALE, ITEM_SCALE, 1.0F);
        graphics.pose().translate(-slot.x - 8.0F, -slot.y - 8.0F, 0.0F);
        super.renderSlot(graphics, slot);
        graphics.pose().popPose();
    }

    @Override
    protected boolean isHovering(int x, int y, int slotWidth, int slotHeight,
                                double mouseX, double mouseY) {
        return super.isHovering(x - 2, y - 2, slotWidth + 6, slotHeight + 6, mouseX, mouseY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void drawSlotFrames(GuiGraphics graphics, int from, int to, int accent, int mouseX, int mouseY) {
        int localMouseX = mouseX - leftPos;
        int localMouseY = mouseY - topPos;
        for (int index = Math.max(0, from); index < to && index < menu.slots.size(); index++) {
            var slot = menu.slots.get(index);
            int x = slot.x - 2;
            int y = slot.y - 2;
            int right = x + 22;
            int bottom = y + 22;
            boolean hovered = localMouseX >= x && localMouseX < right
                    && localMouseY >= y && localMouseY < bottom;
            int border = hovered ? COLOR_SLOT_HOVER : accent;
            int interior = index < menu.getStockSlotCount()
                    ? menu.isCreative() ? COLOR_SLOT_CREATIVE : COLOR_SLOT_STOCK
                    : COLOR_SLOT_PLAYER;

            graphics.fill(x, y, right, bottom, border);
            graphics.fill(x + 1, y + 1, right - 1, bottom - 1, interior);
        }
    }

    private void drawSection(GuiGraphics graphics, int left, int top, int right, int bottom) {
        graphics.fill(left, top, right, bottom, COLOR_CARD);
    }

    private String fit(String value, int maxWidth) {
        if (value == null || value.isEmpty() || maxWidth <= 0 || font.width(value) <= maxWidth) {
            return value == null ? "" : value;
        }
        String suffix = "...";
        int end = value.length();
        while (end > 0 && font.width(value.substring(0, end) + suffix) > maxWidth) {
            end--;
        }
        return value.substring(0, end) + suffix;
    }
}
