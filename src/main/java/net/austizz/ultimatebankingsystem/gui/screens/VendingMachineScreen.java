package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.gui.widgets.DesktopButton;
import net.austizz.ultimatebankingsystem.gui.widgets.DesktopEditBox;
import net.austizz.ultimatebankingsystem.network.VendingMachineActionPayload;
import net.austizz.ultimatebankingsystem.network.VendingMachineActionResponsePayload;
import net.austizz.ultimatebankingsystem.network.VendingMachineOpenPayload;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Buyer and owner-facing control surface for the independent vending machine. */
public final class VendingMachineScreen extends Screen {
    private static final int COLOR_BACKGROUND = 0xFF081827;
    private static final int COLOR_FRAME = 0xFF245278;
    private static final int COLOR_PANEL = 0xFF0D2238;
    private static final int COLOR_CARD = 0xFF122D47;
    private static final int COLOR_TEXT = 0xFFF2F7FF;
    private static final int COLOR_MUTED = 0xFF9EB7D0;
    private static final int COLOR_CYAN = 0xFF42C8FF;
    private static final int COLOR_GREEN = 0xFF59E29A;
    private static final int COLOR_GOLD = 0xFFF2C14E;
    private static final int COLOR_RED = 0xFFE17A7A;

    private VendingMachineOpenPayload payload;
    private final List<DesktopButton> slotButtons = new ArrayList<>();
    private DesktopButton paymentButton;
    private DesktopButton accountButton;
    private DesktopButton setInventoryItemButton;
    private DesktopButton showStockButton;
    private DesktopButton clearButton;
    private DesktopButton savePriceButton;
    private DesktopButton collectCashButton;
    private DesktopButton closeButton;
    private DesktopEditBox priceInput;

    private int selectedSlot;
    private int selectedAccountIndex;
    private PaymentMethod paymentMethod = PaymentMethod.AUTO;
    private String feedbackMessage = "";
    private boolean feedbackSuccess;
    private long feedbackUntilMillis;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int cardsLeft;
    private int cardsWidth;
    private int cardWidth;
    private int cardsTop;
    private int cardHeight;
    private int infoLeft;
    private int infoWidth;
    private int accountLabelY;
    private int priceLabelY;
    private int ownerControlsBottom;

    private boolean inventoryPickerOpen;
    private int inventoryPickerSelectedSlot = -1;
    private int pickerLeft;
    private int pickerTop;
    private int pickerWidth;
    private int pickerHeight;
    private int pickerGridLeft;
    private int pickerGridTop;
    private int pickerCell;
    private int pickerGap;
    private int pickerCancelX;
    private int pickerCancelY;
    private int pickerContinueX;
    private int pickerContinueY;
    private int pickerButtonWidth;
    private final List<InventoryChoice> inventoryChoices = new ArrayList<>();

    public VendingMachineScreen(VendingMachineOpenPayload payload) {
        super(Component.literal("Vending Machine"));
        this.payload = payload;
        this.selectedSlot = 0;
        this.selectedAccountIndex = resolveSelectedAccountIndex();
    }

    public boolean matches(VendingMachineOpenPayload other) {
        return other != null && payload != null
                && payload.dimensionId().equals(other.dimensionId())
                && payload.x() == other.x()
                && payload.y() == other.y()
                && payload.z() == other.z();
    }

    public void refresh(VendingMachineOpenPayload next) {
        if (next == null) {
            return;
        }
        this.payload = next;
        selectedSlot = Math.max(0, Math.min(selectedSlot, VendingMachineOpenPayloadSlots.COUNT - 1));
        selectedAccountIndex = resolveSelectedAccountIndex();
        syncControls();
    }

    @Override
    protected void init() {
        clearWidgets();
        slotButtons.clear();
        paymentButton = null;
        accountButton = null;
        setInventoryItemButton = null;
        showStockButton = null;
        clearButton = null;
        savePriceButton = null;
        collectCashButton = null;
        closeButton = null;
        priceInput = null;

        panelWidth = Math.min(980, Math.max(680, width - 26));
        panelHeight = Math.min(570, Math.max(420, height - 24));
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;

        int innerLeft = panelLeft + 16;
        int innerRight = panelLeft + panelWidth - 16;
        int gap = 12;
        cardsLeft = innerLeft;
        cardsWidth = Math.max(380, (int) (panelWidth * 0.60F));
        cardWidth = (cardsWidth - gap) / 2;
        cardsTop = panelTop + 72;
        cardHeight = 72;
        infoLeft = cardsLeft + cardsWidth + gap;
        infoWidth = Math.max(180, innerRight - infoLeft);

        for (int slot = 0; slot < VendingMachineOpenPayloadSlots.COUNT; slot++) {
            final int displaySlot = slot;
            int row = slot / 2;
            int column = slot % 2;
            int x = cardsLeft + column * (cardWidth + gap);
            int y = cardsTop + row * (cardHeight + gap);
            if (payload.configureAllowed()) {
                DesktopButton selectButton = addRenderableWidget(new DesktopButton(
                        x + 8,
                        y + cardHeight - 24,
                        Math.max(80, cardWidth - 16),
                        19,
                        buttonLabel(slot),
                        COLOR_CYAN,
                        ignored -> {
                            selectedSlot = displaySlot;
                            syncControls();
                        }
                ));
                slotButtons.add(selectButton);
            } else {
                DesktopButton buyButton = addRenderableWidget(new DesktopButton(
                        x + 8,
                        y + cardHeight - 24,
                        Math.max(80, cardWidth - 16),
                        19,
                        buttonLabel(slot),
                        COLOR_GREEN,
                        ignored -> sendAction("buy", displaySlot, paymentMethod.name())
                ));
                slotButtons.add(buyButton);
            }
        }

        if (payload.configureAllowed()) {
            createOwnerControls();
        } else {
            paymentButton = addRenderableWidget(new DesktopButton(
                    infoLeft,
                    panelTop + 76,
                    infoWidth,
                    24,
                    Component.literal("Payment: " + paymentMethod.label),
                    COLOR_GOLD,
                    ignored -> cyclePaymentMethod()
            ));
        }

        closeButton = addRenderableWidget(new DesktopButton(
                panelLeft + 16,
                panelTop + panelHeight - 34,
                120,
                24,
                Component.literal("Close"),
                COLOR_CYAN,
                ignored -> onClose()
        ));
        syncControls();
    }

    private void createOwnerControls() {
        int y = panelTop + 76;
        int buttonHeight = 23;

        accountLabelY = y - 11;
        accountButton = addRenderableWidget(new DesktopButton(
                infoLeft, y, infoWidth, buttonHeight, Component.literal(accountLabel()), COLOR_GREEN,
                ignored -> cycleAccount()
        ));
        y += 36;

        priceLabelY = y;
        int saveWidth = Math.min(82, Math.max(72, infoWidth / 4));
        priceInput = new DesktopEditBox(font, infoLeft, y + 11, infoWidth - saveWidth - 6, buttonHeight,
                Component.literal("Price in whole dollars"));
        priceInput.setValue(String.valueOf(payload.priceDollars(selectedSlot)));
        priceInput.setMaxLength(12);
        addRenderableWidget(priceInput);
        savePriceButton = addRenderableWidget(new DesktopButton(
                infoLeft + infoWidth - saveWidth, y + 11, saveWidth, buttonHeight, Component.literal("Save"), COLOR_GOLD,
                ignored -> sendAction("set_price", selectedSlot, priceInput.getValue())
        ));
        y += 48;

        setInventoryItemButton = addRenderableWidget(new DesktopButton(
                infoLeft, y, infoWidth, buttonHeight, Component.literal("Set Item From Inventory"), COLOR_CYAN,
                ignored -> openInventoryPicker()
        ));
        y += 32;

        showStockButton = addRenderableWidget(new DesktopButton(
                infoLeft, y, infoWidth, buttonHeight, Component.literal("Show Stock"), COLOR_GREEN,
                ignored -> sendAction("show_stock", selectedSlot, "")
        ));
        y += 32;

        clearButton = addRenderableWidget(new DesktopButton(
                infoLeft, y, infoWidth, buttonHeight, Component.literal("Clear Display Slot"), COLOR_RED,
                ignored -> sendAction("clear_slot", selectedSlot, "")
        ));
        y += 36;

        collectCashButton = addRenderableWidget(new DesktopButton(
                infoLeft, y, infoWidth, buttonHeight, Component.literal("Collect Cash Vault"), COLOR_GOLD,
                ignored -> sendAction("collect_cash", -1, "")
        ));
        ownerControlsBottom = y + buttonHeight;
    }

    private void syncControls() {
        if (slotButtons.size() == VendingMachineOpenPayloadSlots.COUNT) {
            for (int slot = 0; slot < slotButtons.size(); slot++) {
                DesktopButton button = slotButtons.get(slot);
                button.setMessage(buttonLabel(slot));
                button.setAccentColor(payload.configureAllowed() && slot == selectedSlot ? COLOR_GREEN :
                        payload.configureAllowed() ? COLOR_CYAN : COLOR_GREEN);
            }
        }
        if (priceInput != null) {
            priceInput.setValue(String.valueOf(payload.priceDollars(selectedSlot)));
        }
        if (accountButton != null) {
            accountButton.setMessage(Component.literal(accountLabel()));
        }
        if (paymentButton != null) {
            paymentButton.setMessage(Component.literal("Payment: " + paymentMethod.label));
        }
    }

    private void cyclePaymentMethod() {
        PaymentMethod[] values = PaymentMethod.values();
        paymentMethod = values[(paymentMethod.ordinal() + 1) % values.length];
        syncControls();
    }

    private void cycleAccount() {
        if (payload.accounts().isEmpty()) {
            return;
        }
        selectedAccountIndex = (selectedAccountIndex + 1) % payload.accounts().size();
        sendAction("set_account", -1, payload.accounts().get(selectedAccountIndex).accountId().toString());
    }

    private int resolveSelectedAccountIndex() {
        if (payload == null || payload.accounts().isEmpty()) {
            return 0;
        }
        String selected = payload.settlementAccountId();
        for (int index = 0; index < payload.accounts().size(); index++) {
            if (payload.accounts().get(index).accountId().toString().equals(selected)) {
                return index;
            }
        }
        for (int index = 0; index < payload.accounts().size(); index++) {
            if (payload.accounts().get(index).primary()) {
                return index;
            }
        }
        return 0;
    }

    private Component buttonLabel(int slot) {
        ItemStack item = payload.displayItem(slot);
        if (payload.configureAllowed()) {
            return Component.literal((slot == selectedSlot ? "Selected " : "Select ") + "Slot " + (slot + 1));
        }
        return Component.literal(item.isEmpty() ? "Unavailable" : "Buy " + (slot + 1));
    }

    private String accountLabel() {
        if (payload.accounts().isEmpty()) {
            return "Settlement account: None";
        }
        var account = payload.accounts().get(Math.max(0, Math.min(selectedAccountIndex, payload.accounts().size() - 1)));
        return "Settlement: " + account.accountType() + " | $" + MoneyText.abbreviate(parseDecimal(account.balance()));
    }

    private void sendAction(String action, int slot, String value) {
        PacketDistributor.sendToServer(new VendingMachineActionPayload(
                payload.dimensionId(), payload.x(), payload.y(), payload.z(), action, slot, value == null ? "" : value));
    }

    public void handleActionResponse(VendingMachineActionResponsePayload response) {
        if (response == null) {
            return;
        }
        feedbackSuccess = response.success();
        feedbackMessage = response.message() == null ? "" : response.message();
        feedbackUntilMillis = System.currentTimeMillis() + 4500L;
    }

    private void openInventoryPicker() {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        inventoryPickerSelectedSlot = -1;
        inventoryPickerOpen = true;
        layoutInventoryPicker();
        rebuildInventoryChoices();
    }

    private void layoutInventoryPicker() {
        pickerWidth = Math.min(600, Math.max(420, width - 36));
        pickerHeight = Math.min(410, Math.max(300, height - 36));
        pickerLeft = (width - pickerWidth) / 2;
        pickerTop = (height - pickerHeight) / 2;
        pickerGap = 4;
        pickerCell = Math.min(32, Math.max(22, (pickerWidth - 48 - 8 * pickerGap) / 9));
        int gridWidth = 9 * pickerCell + 8 * pickerGap;
        pickerGridLeft = pickerLeft + (pickerWidth - gridWidth) / 2;
        pickerGridTop = pickerTop + 96;
        pickerButtonWidth = 112;
        pickerCancelX = pickerLeft + 14;
        pickerContinueX = pickerLeft + pickerWidth - pickerButtonWidth - 14;
        pickerCancelY = pickerTop + pickerHeight - 32;
        pickerContinueY = pickerCancelY;
    }

    private void rebuildInventoryChoices() {
        inventoryChoices.clear();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int inventorySlot = 9 + row * 9 + column;
                int x = pickerGridLeft + column * (pickerCell + pickerGap);
                int y = pickerGridTop + row * (pickerCell + pickerGap);
                inventoryChoices.add(new InventoryChoice(
                        inventorySlot, mc.player.getInventory().getItem(inventorySlot).copy(), x, y, pickerCell));
            }
        }

        int hotbarY = pickerGridTop + 3 * (pickerCell + pickerGap) + 30;
        for (int column = 0; column < 9; column++) {
            int inventorySlot = column;
            int x = pickerGridLeft + column * (pickerCell + pickerGap);
            inventoryChoices.add(new InventoryChoice(
                    inventorySlot, mc.player.getInventory().getItem(inventorySlot).copy(), x, hotbarY, pickerCell));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (feedbackUntilMillis > 0L && System.currentTimeMillis() > feedbackUntilMillis) {
            feedbackMessage = "";
            feedbackUntilMillis = 0L;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, COLOR_BACKGROUND);

        int right = panelLeft + panelWidth;
        int bottom = panelTop + panelHeight;
        graphics.fill(panelLeft - 2, panelTop - 2, right + 2, bottom + 2, COLOR_FRAME);
        graphics.fill(panelLeft, panelTop, right, bottom, COLOR_PANEL);
        graphics.fill(panelLeft + 1, panelTop + 1, right - 1, panelTop + 44, 0xFF0A1D30);
        graphics.fill(panelLeft + 1, panelTop + 42, right - 1, panelTop + 44, COLOR_CYAN);

        graphics.drawString(font, "VENDING MACHINE", panelLeft + 16, panelTop + 11, COLOR_TEXT, false);
        String mode = payload.configureAllowed() ? "OWNER CONTROL" : "CUSTOMER PURCHASE";
        graphics.drawString(font, mode, panelLeft + 16, panelTop + 28,
                payload.configureAllowed() ? COLOR_GREEN : COLOR_CYAN, false);
        String status = payload.creative() ? "CREATIVE STOCK / PROCEEDS VOIDED" : "INDEPENDENT UBS DISPLAY";
        graphics.drawString(font, status, right - font.width(status) - 16, panelTop + 18,
                payload.creative() ? COLOR_GOLD : COLOR_MUTED, false);

        graphics.drawString(font, "PRODUCT WINDOWS", cardsLeft, panelTop + 58, COLOR_CYAN, false);
        for (int slot = 0; slot < VendingMachineOpenPayloadSlots.COUNT; slot++) {
            drawProductCard(graphics, slot, mouseX, mouseY);
        }

        graphics.fill(infoLeft - 6, panelTop + 54, infoLeft - 4, bottom - 48, COLOR_FRAME);
        graphics.drawString(font, payload.configureAllowed() ? "MACHINE CONTROL" : "PAYMENT",
                infoLeft, panelTop + 58, COLOR_CYAN, false);
        if (payload.configureAllowed() && accountButton != null) {
            graphics.drawString(font, "SETTLEMENT ACCOUNT", infoLeft, accountLabelY, COLOR_MUTED, false);
            graphics.drawString(font, "PRICE (WHOLE DOLLARS)", infoLeft, priceLabelY, COLOR_MUTED, false);
        }
        drawInfoPanel(graphics);

        if (!feedbackMessage.isBlank()) {
            int feedbackColor = feedbackSuccess ? COLOR_GREEN : COLOR_RED;
            graphics.fill(panelLeft + 14, bottom - 62, right - 14, bottom - 42, 0xFF091522);
            graphics.fill(panelLeft + 14, bottom - 62, panelLeft + 18, bottom - 42, feedbackColor);
            graphics.drawString(font, fit(feedbackMessage, panelWidth - 46), panelLeft + 24, bottom - 56, feedbackColor, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        for (int slot = 0; slot < VendingMachineOpenPayloadSlots.COUNT; slot++) {
            int row = slot / 2;
            int column = slot % 2;
            int x = cardsLeft + column * (cardWidth + 12);
            int y = cardsTop + row * (cardHeight + 12);
            ItemStack item = payload.displayItem(slot);
            if (item.isEmpty()) {
                continue;
            }
            graphics.renderItem(item, x + 14, y + 13);
            graphics.renderItemDecorations(font, item, x + 14, y + 13);
            if (!inventoryPickerOpen && mouseX >= x && mouseX < x + cardWidth
                    && mouseY >= y && mouseY < y + cardHeight) {
                graphics.renderTooltip(font, item, mouseX, mouseY);
            }
        }
        if (inventoryPickerOpen) {
            renderInventoryPicker(graphics, mouseX, mouseY);
        }
    }

    private void drawProductCard(GuiGraphics graphics, int slot, int mouseX, int mouseY) {
        int row = slot / 2;
        int column = slot % 2;
        int x = cardsLeft + column * (cardWidth + 12);
        int y = cardsTop + row * (cardHeight + 12);
        boolean selected = payload.configureAllowed() && slot == selectedSlot;
        boolean hovered = mouseX >= x && mouseX < x + cardWidth && mouseY >= y && mouseY < y + cardHeight;
        int border = selected ? COLOR_GREEN : hovered ? COLOR_CYAN : COLOR_FRAME;
        graphics.fill(x, y, x + cardWidth, y + cardHeight, border);
        graphics.fill(x + 2, y + 2, x + cardWidth - 2, y + cardHeight - 2, COLOR_CARD);
        ItemStack item = payload.displayItem(slot);
        String itemName = item.isEmpty() ? "Empty display slot" : fit(item.getHoverName().getString(), cardWidth - 92);
        graphics.drawString(font, "SLOT " + (slot + 1), x + 58, y + 8, COLOR_MUTED, false);
        graphics.drawString(font, itemName, x + 58, y + 21, COLOR_TEXT, false);
        if (!item.isEmpty()) {
            graphics.drawString(font, "$" + MoneyText.abbreviate(BigDecimal.valueOf(payload.priceDollars(slot))),
                    x + 58, y + 34, COLOR_GOLD, false);
            String stock = payload.creative() ? "INFINITE" : "STOCK " + Math.max(0, payload.stockCount(slot));
            graphics.drawString(font, stock, x + cardWidth - font.width(stock) - 8, y + 8, COLOR_GREEN, false);
        } else {
            graphics.drawString(font, "Not configured", x + 58, y + 34, COLOR_MUTED, false);
        }
    }

    private void drawInfoPanel(GuiGraphics graphics) {
        int right = panelLeft + panelWidth - 16;
        int footerTop = panelTop + panelHeight - 86;
        if (payload.configureAllowed()) {
            int y = ownerControlsBottom + 16;
            if (y + 82 < footerTop) {
                graphics.drawString(font, "SELECTED SLOT", infoLeft, y, COLOR_MUTED, false);
                graphics.drawString(font, "Slot " + (selectedSlot + 1), infoLeft, y + 14, COLOR_TEXT, false);
                ItemStack selected = payload.displayItem(selectedSlot);
                graphics.drawString(font, selected.isEmpty() ? "No product configured" : fit(selected.getHoverName().getString(), infoWidth),
                        infoLeft, y + 29, selected.isEmpty() ? COLOR_MUTED : COLOR_TEXT, false);
                graphics.drawString(font, "CASH VAULT", infoLeft, y + 50, COLOR_MUTED, false);
                graphics.drawString(font, payload.creative() ? "Disabled for creative machine" :
                                payload.cashOccupiedSlots() + "/27 slots | $" + MoneyText.abbreviate(BigDecimal.valueOf(payload.cashValueCents(), 2)),
                        infoLeft, y + 64, payload.creative() ? COLOR_MUTED : COLOR_GOLD, false);
                if (y + 105 < footerTop) {
                    graphics.drawString(font, "LIFETIME SALES", infoLeft, y + 84, COLOR_MUTED, false);
                    graphics.drawString(font, "$" + MoneyText.abbreviate(BigDecimal.valueOf(payload.totalSalesDollars())),
                            infoLeft, y + 98, COLOR_GREEN, false);
                }
            }
        } else {
            int y = panelTop + 110;
            graphics.drawString(font, "SELECTED PAYMENT", infoLeft, y, COLOR_MUTED, false);
            graphics.drawString(font, "Use the button above to cycle payment rail.", infoLeft, y + 16, COLOR_TEXT, false);
            y += 54;
            graphics.drawString(font, payload.creative() ? "INFINITE STOCK" : "STOCK IS SERVER-SIDE",
                    infoLeft, y, payload.creative() ? COLOR_GOLD : COLOR_CYAN, false);
            graphics.drawString(font, payload.creative() ? "No cash vault is required." : "Physical cash is stored in the machine.",
                    infoLeft, y + 16, COLOR_MUTED, false);
            y += 62;
            graphics.drawString(font, "OWNER", infoLeft, y, COLOR_MUTED, false);
            graphics.drawString(font, payload.ownerName().isBlank() ? "Unassigned" : fit(payload.ownerName(), infoWidth),
                    infoLeft, y + 16, COLOR_TEXT, false);
        }
        graphics.fill(infoLeft, footerTop, right, footerTop + 2, COLOR_FRAME);
        String help = payload.configureAllowed()
                ? "Select a slot, set its item from inventory, or open its stock."
                : "Choose a product window and confirm the payment rail.";
        graphics.drawString(font, fit(help, infoWidth), infoLeft, footerTop + 10, COLOR_MUTED, false);
    }

    private void renderInventoryPicker(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0D, 0.0D, 600.0D);
        graphics.fill(0, 0, width, height, 0x92000000);

        int right = pickerLeft + pickerWidth;
        int bottom = pickerTop + pickerHeight;
        graphics.fill(pickerLeft - 2, pickerTop - 2, right + 2, bottom + 2, COLOR_FRAME);
        graphics.fill(pickerLeft, pickerTop, right, bottom, COLOR_PANEL);
        graphics.fill(pickerLeft + 1, pickerTop + 1, right - 1, pickerTop + 44, 0xFF0A1D30);
        graphics.fill(pickerLeft + 1, pickerTop + 42, right - 1, pickerTop + 44, COLOR_CYAN);
        graphics.drawString(font, "SET ITEM FROM INVENTORY", pickerLeft + 14, pickerTop + 10, COLOR_TEXT, false);
        graphics.drawString(font, "Display slot " + (selectedSlot + 1), pickerLeft + 14, pickerTop + 27, COLOR_MUTED, false);
        graphics.drawString(font, "Select a stack. Every matching item in your inventory will be moved to stock.",
                pickerLeft + 14, pickerTop + 57, COLOR_TEXT, false);
        if (inventoryPickerSelectedSlot >= 0) {
            ItemStack selected = selectedInventoryStack();
            String summary = selected.isEmpty()
                    ? "Selection unavailable"
                    : "Selected: " + selected.getHoverName().getString() + " | Total to move: "
                    + totalMatchingInventoryItems(selected);
            graphics.drawString(font, fit(summary, pickerWidth - 28), pickerLeft + 14, pickerTop + 70, COLOR_GREEN, false);
        }
        graphics.drawString(font, "MAIN INVENTORY", pickerGridLeft, pickerGridTop - 14, COLOR_CYAN, false);

        InventoryChoice hovered = null;
        for (InventoryChoice choice : inventoryChoices) {
            boolean isHovered = inside(mouseX, mouseY, choice.x(), choice.y(), choice.size(), choice.size());
            if (isHovered) {
                hovered = choice;
            }
            boolean selected = choice.inventorySlot() == inventoryPickerSelectedSlot;
            int border = selected ? COLOR_GREEN : isHovered ? COLOR_CYAN : COLOR_FRAME;
            int fill = choice.stack().isEmpty() ? 0xFF0A1929 : COLOR_CARD;
            graphics.fill(choice.x(), choice.y(), choice.x() + choice.size(), choice.y() + choice.size(), border);
            graphics.fill(choice.x() + 1, choice.y() + 1,
                    choice.x() + choice.size() - 1, choice.y() + choice.size() - 1, fill);
            if (!choice.stack().isEmpty()) {
                int itemX = choice.x() + (choice.size() - 16) / 2;
                int itemY = choice.y() + (choice.size() - 16) / 2;
                graphics.renderItem(choice.stack(), itemX, itemY);
                graphics.renderItemDecorations(font, choice.stack(), itemX, itemY);
            }
            if (selected) {
                graphics.fill(choice.x() + 2, choice.y() + choice.size() - 3,
                        choice.x() + choice.size() - 2, choice.y() + choice.size() - 2, COLOR_GREEN);
            }
        }

        int hotbarLabelY = pickerGridTop + 3 * (pickerCell + pickerGap) + 16;
        graphics.drawString(font, "HOTBAR", pickerGridLeft, hotbarLabelY, COLOR_CYAN, false);
        boolean cancelHovered = inside(mouseX, mouseY, pickerCancelX, pickerCancelY, pickerButtonWidth, 22);
        boolean continueHovered = inside(mouseX, mouseY, pickerContinueX, pickerContinueY, pickerButtonWidth, 22);
        drawModalButton(graphics, pickerCancelX, pickerCancelY, pickerButtonWidth, 22,
                "Cancel", COLOR_RED, cancelHovered, true);
        drawModalButton(graphics, pickerContinueX, pickerContinueY, pickerButtonWidth, 22,
                "Continue", COLOR_GREEN, continueHovered, inventoryPickerSelectedSlot >= 0);

        if (hovered != null && !hovered.stack().isEmpty()) {
            graphics.renderTooltip(font, hovered.stack(), mouseX, mouseY);
        }
        graphics.pose().popPose();
    }

    private ItemStack selectedInventoryStack() {
        for (InventoryChoice choice : inventoryChoices) {
            if (choice.inventorySlot() == inventoryPickerSelectedSlot) {
                return choice.stack();
            }
        }
        return ItemStack.EMPTY;
    }

    private int totalMatchingInventoryItems(ItemStack selected) {
        if (selected == null || selected.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (InventoryChoice choice : inventoryChoices) {
            if (ItemStack.isSameItemSameComponents(selected, choice.stack())) {
                total += choice.stack().getCount();
            }
        }
        return total;
    }

    private void drawModalButton(GuiGraphics graphics, int x, int y, int width, int height,
                                 String label, int accent, boolean hovered, boolean active) {
        int border = active ? (hovered ? COLOR_CYAN : COLOR_FRAME) : 0xFF3A4654;
        int fill = active ? (hovered ? 0xFF173B5A : 0xFF102A42) : 0xFF111A24;
        graphics.fill(x, y, x + width, y + height, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fill);
        graphics.fill(x + 3, y + 3, x + 6, y + height - 3, active ? accent : COLOR_MUTED);
        String text = fit(label, width - 28);
        graphics.drawString(font, text, x + 13, y + Math.max(1, (height - 8) / 2),
                active ? COLOR_TEXT : COLOR_MUTED, false);
    }

    private static BigDecimal parseDecimal(String value) {
        try {
            return new BigDecimal(value == null || value.isBlank() ? "0" : value);
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
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

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep the game world visible without the vanilla pause-screen blur.
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (inventoryPickerOpen && keyCode == 256) {
            inventoryPickerOpen = false;
            return true;
        }
        if (keyCode == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollDelta) {
        if (inventoryPickerOpen) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollDelta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!inventoryPickerOpen) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button != 0) {
            return true;
        }
        if (inside(mouseX, mouseY, pickerCancelX, pickerCancelY, pickerButtonWidth, 22)) {
            inventoryPickerOpen = false;
            return true;
        }
        if (inside(mouseX, mouseY, pickerContinueX, pickerContinueY, pickerButtonWidth, 22)
                && inventoryPickerSelectedSlot >= 0) {
            sendAction("set_item_inventory", selectedSlot, String.valueOf(inventoryPickerSelectedSlot));
            inventoryPickerOpen = false;
            return true;
        }
        for (InventoryChoice choice : inventoryChoices) {
            if (inside(mouseX, mouseY, choice.x(), choice.y(), choice.size(), choice.size())) {
                if (!choice.stack().isEmpty()) {
                    inventoryPickerSelectedSlot = choice.inventorySlot();
                }
                return true;
            }
        }
        return true;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private enum PaymentMethod {
        AUTO("Auto"),
        CASH("Cash"),
        CARD("Card"),
        MOBILE("Mobile");

        private final String label;

        PaymentMethod(String label) {
            this.label = label;
        }
    }

    private record InventoryChoice(int inventorySlot, ItemStack stack, int x, int y, int size) {
    }

    private static final class VendingMachineOpenPayloadSlots {
        private static final int COUNT = 8;
    }
}
