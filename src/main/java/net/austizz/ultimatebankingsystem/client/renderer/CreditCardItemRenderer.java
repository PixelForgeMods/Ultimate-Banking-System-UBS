package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.payments.CreditCardPrint;
import net.austizz.ultimatebankingsystem.payments.CreditCardService;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.austizz.ultimatebankingsystem.item.CreditCardItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

public final class CreditCardItemRenderer extends BlockEntityWithoutLevelRenderer {
    public static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            UltimateBankingSystem.MODID, "item/credit_card_body");

    public CreditCardItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        var mc = Minecraft.getInstance();
        var tag = ItemStackDataCompat.getCustomData(stack);
        pose.pushPose();
        if (tag.getBoolean(CreditCardItem.TAG_BACK_VISIBLE)) {
            pose.translate(.5, .5, .5);
            pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.translate(-.5, -.5, -.5);
        }
        var model = mc.getModelManager().getModel(ModelResourceLocation.standalone(MODEL));
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()),
                null, model, 1, 1, 1, light, overlay);
        var print = CreditCardPrint.fromTag(tag);
        if (print.number().isEmpty()) {
            pose.popPose();
            return;
        }
        String holder = print.holder();
        if (holder.isEmpty() && tag.hasUUID(CreditCardService.TAG_OWNER_ID) && mc.getConnection() != null) {
            var player = mc.getConnection().getPlayerInfo(tag.getUUID(CreditCardService.TAG_OWNER_ID));
            if (player != null) holder = player.getProfile().getName().toUpperCase(Locale.ROOT);
        }
        text(pose, buffers, print.bank(), 11.78, 10.05, true, .40F, 7.5F, light);
        text(pose, buffers, print.number(), 11.78, 7.30, true, .46F, 7.5F, light);
        text(pose, buffers, "CARDHOLDER", 11.78, 6.40, true, .15F, 4.8F, light);
        text(pose, buffers, holder, 11.78, 6.12, true, .27F, 4.8F, light);
        text(pose, buffers, "VALID THRU", 6.30, 6.40, true, .15F, 1.5F, light);
        text(pose, buffers, print.expiry(), 6.30, 6.12, true, .27F, 1.5F, light);
        if (!print.cvc().isEmpty()) {
            text(pose, buffers, "CVC", 10.48, 8.13, false, .15F, 1.1F, light);
            text(pose, buffers, print.cvc(), 10.48, 7.86, false, .28F, 1.1F, light);
        }
        pose.popPose();
    }

    private static void text(PoseStack pose, MultiBufferSource buffers, String text, double x, double y,
                             boolean front, float height, float width, int light) {
        if (text.isEmpty()) return;
        var font = Minecraft.getInstance().font;
        float scale = Math.min(height / 9, width / Math.max(1, font.width(text))) / 16;
        pose.pushPose();
        pose.translate(x / 16, y / 16, (front ? 7.959 : 8.041) / 16);
        if (front) pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(scale, -scale, scale);
        font.drawInBatch(text, .35F, .35F, 0xFF122632, false, pose.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0, light);
        pose.translate(0, 0, .01);
        font.drawInBatch(text, 0, 0, 0xFFE1E9E7, false, pose.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }
}
