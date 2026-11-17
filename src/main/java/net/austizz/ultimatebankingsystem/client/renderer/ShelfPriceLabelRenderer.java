package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.austizz.ultimatebankingsystem.shelf.ShelfPrice;
import net.austizz.ultimatebankingsystem.util.MoneyText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Renders client-side price tags above displayed shelf items without creating world entities. */
public final class ShelfPriceLabelRenderer {
    private static final float LABEL_SCALE = 0.024F;
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int PRICE_COLOR = 0xFFFFD45C;
    private static final int UNPRICED_COLOR = 0xFFB5C7D9;
    private static final int LABEL_BACKGROUND = 0xB0081422;
    private static final double LABEL_RENDER_RANGE_SQ = 72.0D * 72.0D;
    private static final Map<LabelKey, PendingLabel> PENDING_LABELS = new LinkedHashMap<>();

    private ShelfPriceLabelRenderer() {
    }

    public static void queue(Level level,
                             BlockPos blockPos,
                             int slot,
                             long priceCents,
                             boolean hasPrice,
                             Vec3 localAnchor) {
        if (level == null || !level.isClientSide() || blockPos == null || localAnchor == null) {
            return;
        }

        String text = hasPrice
                ? MoneyText.abbreviateRoundedWithDollar(ShelfPrice.toDollars(priceCents))
                : "NO PRICE";
        int color = hasPrice ? PRICE_COLOR : UNPRICED_COLOR;
        Vec3 worldAnchor = Vec3.atLowerCornerOf(blockPos).add(localAnchor);
        LabelKey key = new LabelKey(level.dimension().location().toString(), blockPos.asLong(), slot);
        PENDING_LABELS.put(key, new PendingLabel(worldAnchor, text, color));
    }

    public static void queueDollars(Level level,
                                    BlockPos blockPos,
                                    int slot,
                                    long priceDollars,
                                    Vec3 localAnchor) {
        if (level == null || !level.isClientSide() || blockPos == null || localAnchor == null) {
            return;
        }

        String text = MoneyText.abbreviateRoundedWithDollar(BigDecimal.valueOf(Math.max(0L, priceDollars)));
        Vec3 worldAnchor = Vec3.atLowerCornerOf(blockPos).add(localAnchor);
        LabelKey key = new LabelKey(level.dimension().location().toString(), blockPos.asLong(), slot);
        PENDING_LABELS.put(key, new PendingLabel(worldAnchor, text, PRICE_COLOR));
    }

    public static void renderWorld(RenderLevelStageEvent event, Minecraft minecraft) {
        if (event == null
                || minecraft == null
                || minecraft.level == null
                || minecraft.player == null
                || minecraft.font == null
                || minecraft.getEntityRenderDispatcher() == null
                || minecraft.options.hideGui) {
            PENDING_LABELS.clear();
            return;
        }
        if (PENDING_LABELS.isEmpty()) {
            return;
        }

        String dimensionId = minecraft.level.dimension().location().toString();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = null;
        try {
            for (Map.Entry<LabelKey, PendingLabel> entry : PENDING_LABELS.entrySet()) {
                LabelKey key = entry.getKey();
                PendingLabel label = entry.getValue();
                if (key == null || label == null
                        || label.anchor().distanceToSqr(camera) > LABEL_RENDER_RANGE_SQ) {
                    continue;
                }
                if (!dimensionId.equals(key.dimensionId())) {
                    continue;
                }
                if (buffers == null) {
                    buffers = minecraft.renderBuffers().bufferSource();
                }
                drawWorldLabel(event.getPoseStack(), buffers, minecraft, camera, label);
            }
            if (buffers != null) {
                buffers.endBatch();
            }
        } finally {
            PENDING_LABELS.clear();
        }
    }

    private static void drawWorldLabel(PoseStack poseStack,
                                       MultiBufferSource.BufferSource buffers,
                                       Minecraft minecraft,
                                       Vec3 camera,
                                       PendingLabel label) {
        Font font = minecraft.font;

        poseStack.pushPose();
        poseStack.translate(label.anchor().x - camera.x,
                label.anchor().y - camera.y,
                label.anchor().z - camera.z);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
        Matrix4f matrix = poseStack.last().pose();
        float x = -font.width(label.text()) / 2.0F;
        font.drawInBatch(label.text(), x, 0.0F, label.color(), false, matrix, buffers,
                Font.DisplayMode.SEE_THROUGH, LABEL_BACKGROUND, FULL_BRIGHT);
        poseStack.popPose();
    }

    private record LabelKey(String dimensionId, long blockPos, int slot) {
    }

    private record PendingLabel(Vec3 anchor, String text, int color) {
    }
}
