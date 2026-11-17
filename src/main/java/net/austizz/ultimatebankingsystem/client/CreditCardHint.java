package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.item.CreditCardItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class CreditCardHint {
    private CreditCardHint() {}

    public static void render(Minecraft mc, GuiGraphics graphics) {
        if (mc.player == null || mc.player.isSpectator()
                || !(mc.player.getMainHandItem().getItem() instanceof CreditCardItem
                || mc.player.getOffhandItem().getItem() instanceof CreditCardItem)) return;
        var hint = Component.translatable("hint.ultimatebankingsystem.credit_card.flip",
                mc.options.keyShift.getTranslatedKeyMessage(), mc.options.keyUse.getTranslatedKeyMessage());
        int width = Math.min(mc.font.width(hint), Math.max(1, graphics.guiWidth() - 16));
        var lines = mc.font.split(hint, width);
        // Follow vanilla's raised item-name position when extra health/armor rows are present.
        int padding = Math.max(80, Math.max(mc.gui.leftHeight, mc.gui.rightHeight) + 21);
        var bounds = HudLayoutManager.placeBottomCenter("credit-card.flip", width + 8,
                lines.size() * mc.font.lineHeight + 4, padding);
        int y = bounds.y() + 2;
        for (var line : lines) {
            graphics.drawString(mc.font, line, (graphics.guiWidth() - mc.font.width(line)) / 2, y, 0xFFD2DCDF);
            y += mc.font.lineHeight;
        }
    }
}
