package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.network.CourierOrderPayload;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/** Compact in-world route tracker for accepted Courier deliveries. */
@OnlyIn(Dist.CLIENT)
public final class CourierDeliveryHudRenderer {
    private static final int PANEL_WIDTH = 226;
    private static final int HEADER_HEIGHT = 34;
    private static final int CARD_HEIGHT = 54;
    private static final int PANEL_BG = 0xE90C1B2A;
    private static final int CARD_BG = 0xD9142B40;
    private static final int FRAME = 0xFF315D7A;
    private static final int CYAN = 0xFF55D4FF;
    private static final int GREEN = 0xFF59DCA5;
    private static final int GOLD = 0xFFFFCA64;
    private static final int TEXT = 0xFFF0F7FF;
    private static final int MUTED = 0xFFAAC2D6;
    private static final int WARNING = 0xFFFF8C70;
    private static final long STANDARD_ROUTE_SECONDS = 15L * 60L;
    private static final String[] COMPASS = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};

    private CourierDeliveryHudRenderer() {
    }

    public static void render(Minecraft minecraft, GuiGraphics graphics) {
        if (minecraft == null || graphics == null || minecraft.player == null
                || minecraft.level == null || !CourierDeliveryClientState.isActive()) {
            return;
        }
        List<CourierOrderPayload> deliveries = CourierDeliveryClientState.deliveries();
        if (deliveries.isEmpty()) {
            return;
        }

        Font font = minecraft.font;
        int panelHeight = CourierDeliveryClientState.isCollapsed()
                ? 26 : HEADER_HEIGHT + deliveries.size() * CARD_HEIGHT;
        HudLayoutManager.Bounds panel = HudLayoutManager.placeBottomRight(
                "courier-routes", PANEL_WIDTH, panelHeight, 8);
        int x = panel.x();
        int y = panel.y();

        graphics.pose().pushPose();
        graphics.pose().translate(0.0D, 0.0D, 1040.0D);
        drawPanel(graphics, x, y, PANEL_WIDTH, panelHeight, PANEL_BG, FRAME, CYAN);
        if (CourierDeliveryClientState.isCollapsed()) {
            String line = "COURIER  " + deliveries.size() + " ACTIVE  |  X EXPAND";
            drawFit(graphics, font, line, x + 10, y + 9, PANEL_WIDTH - 20, TEXT);
            graphics.pose().popPose();
            return;
        }

        graphics.drawString(font, "COURIER ROUTES", x + 10, y + 7, CYAN, false);
        drawFitRight(graphics, font, deliveries.size() + " ACTIVE  |  X COLLAPSE",
                x + PANEL_WIDTH - 8, y + 7, PANEL_WIDTH - 92, MUTED);
        CourierOrderPayload next = deliveries.getFirst();
        String route = "NEXT " + safe(next.destinationName()) + "  "
                + directionLabel(minecraft, next) + "  " + distanceLabel(minecraft, next);
        drawFit(graphics, font, route, x + 10, y + 20, PANEL_WIDTH - 20, TEXT);
        int cardY = y + HEADER_HEIGHT;
        for (CourierOrderPayload delivery : deliveries) {
            renderRouteCard(minecraft, graphics, font, delivery, x + 6, cardY, PANEL_WIDTH - 12);
            cardY += CARD_HEIGHT;
        }
        graphics.pose().popPose();
    }

    private static void renderRouteCard(Minecraft minecraft, GuiGraphics graphics, Font font,
                                         CourierOrderPayload delivery, int x, int y, int width) {
        long seconds = CourierDeliveryClientState.secondsRemaining(delivery);
        int accent = seconds <= 60L ? WARNING : GREEN;
        drawPanel(graphics, x, y, width, CARD_HEIGHT - 4, CARD_BG, FRAME, accent);

        ItemStack stack = itemStack(delivery.itemId());
        graphics.renderItem(stack, x + 7, y + 8);

        int textX = x + 31;
        int rightWidth = Math.min(88, Math.max(68, width / 2 - 8));
        int textWidth = Math.max(70, width - 40 - rightWidth);
        int right = x + width - 8;
        String idLine = "#" + delivery.shortId() + "  " + safe(delivery.contractTier());
        drawFit(graphics, font, idLine, textX, y + 5, textWidth, accent);
        drawFit(graphics, font, safe(delivery.itemName()) + " x" + delivery.quantity(),
                textX, y + 19, textWidth, TEXT);
        drawFit(graphics, font, "TO " + safe(delivery.destinationName()),
                textX, y + 33, textWidth, MUTED);

        String reward = MoneyText.abbreviateWithDollar(
                BigDecimal.valueOf(Math.max(0L, delivery.rewardCents()), 2));
        String timer = formatCountdown(seconds);
        String rightLine = reward + "  " + timer;
        drawFitRight(graphics, font, rightLine, right, y + 5, rightWidth,
                seconds <= 60L ? WARNING : GOLD);

        String distance = distanceLabel(minecraft, delivery);
        drawFitRight(graphics, font, distance, right, y + 19, rightWidth, MUTED);
        String bonus = delivery.moneyMultiplierPercent() == 100 && delivery.xpMultiplierPercent() == 100
                ? "STANDARD PAY"
                : multiplierLabel(delivery.moneyMultiplierPercent(), "PAY") + " / "
                + multiplierLabel(delivery.xpMultiplierPercent(), "XP");
        drawFitRight(graphics, font, bonus, right, y + 33, rightWidth, CYAN);

        int barLeft = x + 8;
        int barRight = x + width - 8;
        int barTop = y + CARD_HEIGHT - 12;
        graphics.fill(barLeft, barTop, barRight, barTop + 4, 0xFF1B3040);
        int fill = Math.round((barRight - barLeft)
                * Math.max(0.0F, Math.min(1.0F, seconds / (float) STANDARD_ROUTE_SECONDS)));
        if (fill > 0) {
            graphics.fillGradient(barLeft, barTop, barLeft + fill, barTop + 4,
                    seconds <= 60L ? WARNING : GREEN, CYAN);
        }
    }

    private static String directionLabel(Minecraft minecraft, CourierOrderPayload delivery) {
        if (minecraft == null || minecraft.player == null || delivery.destinationX() == 0
                && delivery.destinationY() == 0 && delivery.destinationZ() == 0) {
            return "ROUTE";
        }
        if (!sameDimension(minecraft, delivery)) {
            return "OTHER DIM";
        }
        double dx = delivery.destinationX() + 0.5D - minecraft.player.getX();
        double dz = delivery.destinationZ() + 0.5D - minecraft.player.getZ();
        if (Math.abs(dx) < 0.5D && Math.abs(dz) < 0.5D) {
            return "HERE";
        }
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        if (angle < 0.0D) {
            angle += 360.0D;
        }
        int compassIndex = (int) Math.round(angle / 45.0D) & 7;
        float relative = Mth.wrapDegrees((float) (angle - minecraft.player.getYRot()));
        String side = relative < -22.5F ? "LEFT" : relative > 22.5F ? "RIGHT" : "AHEAD";
        return COMPASS[compassIndex] + " " + side;
    }

    private static String distanceLabel(Minecraft minecraft, CourierOrderPayload delivery) {
        if (minecraft == null || minecraft.player == null || !sameDimension(minecraft, delivery)) {
            return delivery.routeDistanceBlocks() < 0 ? "CROSS-DIM" : delivery.routeDistanceBlocks() + " blocks";
        }
        double dx = delivery.destinationX() + 0.5D - minecraft.player.getX();
        double dz = delivery.destinationZ() + 0.5D - minecraft.player.getZ();
        return Math.max(0L, Math.round(Math.sqrt(dx * dx + dz * dz))) + " blocks";
    }

    private static boolean sameDimension(Minecraft minecraft, CourierOrderPayload delivery) {
        return delivery != null && delivery.destinationDimension() != null
                && delivery.destinationDimension().equals(minecraft.level.dimension().location().toString());
    }

    private static ItemStack itemStack(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id == null ? "" : id);
        if (key == null) {
            return new ItemStack(Items.PAPER);
        }
        Item item = BuiltInRegistries.ITEM.get(key);
        return item == null ? new ItemStack(Items.PAPER) : new ItemStack(item);
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y, int width, int height,
                                  int background, int frame, int accent) {
        graphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, 0x65000000);
        graphics.fill(x, y, x + width, y + height, frame);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, background);
        graphics.fill(x + 2, y + 2, x + width - 2, y + 4, accent);
        graphics.fill(x + 2, y + 4, x + 4, y + height - 2, accent);
    }

    private static void drawFit(GuiGraphics graphics, Font font, String value, int x, int y,
                                int maxWidth, int color) {
        String text = fit(font, safe(value), maxWidth);
        graphics.drawString(font, text, x, y, color, false);
    }

    private static void drawFitRight(GuiGraphics graphics, Font font, String value, int right,
                                     int y, int maxWidth, int color) {
        String text = fit(font, safe(value), maxWidth);
        graphics.drawString(font, text, right - font.width(text), y, color, false);
    }

    private static String fit(Font font, String value, int maxWidth) {
        if (font.width(value) <= maxWidth) {
            return value;
        }
        String suffix = "...";
        return font.plainSubstrByWidth(value, Math.max(4, maxWidth - font.width(suffix))) + suffix;
    }

    private static String formatCountdown(long seconds) {
        long safeSeconds = Math.max(0L, seconds);
        return String.format(Locale.ROOT, "%02d:%02d", safeSeconds / 60L, safeSeconds % 60L);
    }

    private static String multiplierLabel(int percent, String label) {
        int delta = percent - 100;
        return (delta >= 0 ? "+" : "") + delta + "% " + label;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
