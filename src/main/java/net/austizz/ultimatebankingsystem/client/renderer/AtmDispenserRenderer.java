package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion;
import net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion.Phase;
import net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity;
import net.austizz.ultimatebankingsystem.client.AtmControls;
import net.austizz.ultimatebankingsystem.client.AtmCameraController;
import net.austizz.ultimatebankingsystem.item.DollarBills;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.austizz.ultimatebankingsystem.client.AtmReceiptPrint;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

final class AtmDispenserRenderer {
    private static final ResourceLocation SHUTTER = ResourceLocation.fromNamespaceAndPath(UltimateBankingSystem.MODID, "block/atm/cash_shutter");
    private static final java.util.LinkedHashMap<String, ResourceLocation> RECEIPTS = new java.util.LinkedHashMap<>(16, .75F, true);
    private AtmDispenserRenderer() {}

    static void render(AtmBlockEntity atm, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var mc = Minecraft.getInstance();
        double age = atm.phaseAge(partial);
        var phase = atm.phase();
        double shutter = AtmDispenseMotion.shutter(phase, age);
        pose.pushPose();
        pose.translate(0, shutter * .8 / 16, shutter * .22 / 16);
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(Sheets.cutoutBlockSheet()),
                atm.getBlockState(), mc.getModelManager().getModel(ModelResourceLocation.standalone(AtmControls.inWall(atm.getBlockPos()) ? AtmControls.part("cash_shutter") : SHUTTER)), 1, 1, 1, light, overlay);
        pose.popPose();

        light = net.minecraft.client.renderer.LightTexture.pack(Math.max(10, net.minecraft.client.renderer.LightTexture.block(light)), net.minecraft.client.renderer.LightTexture.sky(light));
        renderDeposit(atm, age, pose, buffers, light, overlay);
        double receipt = AtmDispenseMotion.receipt(phase, age);
        if (phase == Phase.DISPENSING || phase == Phase.CASH_READY || phase == Phase.PICKING_CASH) {
            pose.pushPose();
            Vec3 slot = AtmControls.cashSlot(atm.getBlockPos()).scale(1.0 / 16);
            pose.translate(slot.x, slot.y, slot.z);
            if (phase == Phase.PICKING_CASH) pickupTransform(pose, age, false);
            var plan = atm.cashPlan();
            int count = AtmDispenseMotion.billCount(plan);
            int started = AtmDispenseMotion.billsStarted(phase, age, count);
            double layerHeight = Math.min(.0011, .018 / Math.max(1, count));
            int rendered = 0;
            for (int denomination = 0; denomination < 7 && rendered < started; denomination++) {
                int copies = Math.min(plan[denomination], started - rendered);
                for (int n = 0; n < copies; n++) {
                    pose.pushPose();
                    pose.translate(0, rendered * layerHeight, AtmDispenseMotion.cashDepth(phase, age, rendered));
                    pose.mulPose(Axis.XP.rotationDegrees(90));
                    pose.scale(.31F, .31F, .05F);
                    var stack = new ItemStack(DollarBills.getCashItemForDenominationCents(DollarBills.CASH_DENOMINATIONS_CENTS_DESC[denomination]));
                    mc.getItemRenderer().renderStatic(stack, ItemDisplayContext.NONE, light, overlay, pose, buffers, mc.level, 0);
                    pose.popPose(); rendered++;
                }
            }
            if (phase == Phase.PICKING_CASH) hand(atm, pose, buffers, age, light);
            pose.popPose();
        }
        ResourceLocation printedReceipt = phase == Phase.PRINTING || phase == Phase.RECEIPT_READY || phase == Phase.PICKING_RECEIPT
                ? receiptTexture(atm.receiptDetails()) : null;
        if (receipt > 0 && printedReceipt != null) {
            pose.pushPose();
            var slot = AtmControls.receiptSlot(atm.getBlockPos()).scale(1.0 / 16);
            pose.translate(slot.x, slot.y, slot.z - .1 / 16);
            if (phase == Phase.PICKING_RECEIPT) pickupTransform(pose, age * 26 / 22, true);
            pose.pushPose();
            pose.mulPose(Axis.XP.rotationDegrees((float) AtmDispenseMotion.RECEIPT_TILT));
            float length = (float) (AtmDispenseMotion.RECEIPT_LENGTH * receipt);
            var buffer = buffers.getBuffer(RenderType.text(printedReceipt));
            var matrix = pose.last().pose();
            // Crop at the slot edge: the full, already-printed sheet translates through this edge.
            float firstV = (float) (1 - receipt);
            float[][] vertices = {{.045F, 0, 0, firstV}, {.045F, -length, 0, 1},
                    {-.045F, -length, 1, 1}, {-.045F, 0, 1, firstV}};
            for (float[] v : vertices)
                buffer.addVertex(matrix, v[0], v[1], 0).setColor(0xFFFFFFFF).setUv(v[2], v[3]).setLight(light);
            pose.popPose();
            if (phase == Phase.PICKING_RECEIPT) hand(atm, pose, buffers, age * 26 / 22, light);
            pose.popPose();
        }
    }
    private static void renderDeposit(AtmBlockEntity atm, double age, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        var phase = atm.phase();
        boolean inserting = phase == Phase.DEPOSIT_INSERTING;
        boolean returning = phase == Phase.DEPOSIT_RETURNING || phase == Phase.DEPOSIT_RETURN_READY || phase == Phase.DEPOSIT_PICKUP;
        if (!inserting && !returning) return;
        var mc = Minecraft.getInstance();
        var slot = AtmControls.cashSlot(atm.getBlockPos()).scale(1.0/16);
        pose.pushPose(); pose.translate(slot.x,slot.y,slot.z);
        if (phase == Phase.DEPOSIT_PICKUP) pickupTransform(pose, age, false);
        double approach = net.austizz.ultimatebankingsystem.atm.AtmDepositMotion.approach(age);
        double withdraw = net.austizz.ultimatebankingsystem.atm.AtmDepositMotion.withdraw(age);
        double depth = inserting ? net.austizz.ultimatebankingsystem.atm.AtmDepositMotion.depth(age)
                : phase == Phase.DEPOSIT_RETURNING ? .17 * (1-AtmDispenseMotion.ease(age/net.austizz.ultimatebankingsystem.atm.AtmDepositMotion.RETURN_TICKS)) : 0;
        var plan = inserting ? atm.deposit().feeding() : atm.deposit().detected();
        int layer = 0;
        for (int denomination=0;denomination<7;denomination++) {
            // Visible stack layers represent all notes held in custody, without rendering hundreds of overlapping faces.
            int copies = Math.min(plan[denomination], 3);
            for(int n=0;n<copies;n++) {
                pose.pushPose(); pose.translate(inserting ? .12*(1-approach):0, layer*.0008-(inserting ? .18*(1-approach):0), depth);
                pose.mulPose(Axis.XP.rotationDegrees(90));pose.scale(.31F,.31F,.05F);
                mc.getItemRenderer().renderStatic(new ItemStack(DollarBills.getCashItemForDenominationCents(DollarBills.CASH_DENOMINATIONS_CENTS_DESC[denomination])),
                        ItemDisplayContext.NONE,light,overlay,pose,buffers,mc.level,0);
                pose.popPose();layer++;
            }
        }
        var actor = atm.deposit().owner() == null ? null : mc.level.getPlayerByUUID(atm.deposit().owner());
        if (actor instanceof net.minecraft.client.player.AbstractClientPlayer player && !player.isInvisible()) {
            var renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
            boolean right = player.getMainArm() == HumanoidArm.RIGHT;
            pose.pushPose();
            if (inserting) pose.translate((right?1:-1)*(.13+.12*(1-approach)+.15*withdraw), -.12-.18*(1-approach)-.20*withdraw, -.29-.22*(1-approach)-.20*withdraw);
            else pose.translate((right?1:-1)*(.20*(1-AtmDispenseMotion.pickup(age,false).reach())+.13), -.30+.18*AtmDispenseMotion.pickup(age,false).reach(), .12*(1-AtmDispenseMotion.pickup(age,false).reach())-.29);
            if (inserting || phase == Phase.DEPOSIT_PICKUP) {
                pose.mulPose(Axis.XP.rotationDegrees(75));pose.scale(.4F,.4F,.4F);
                if(right)renderer.renderRightHand(pose,buffers,light,player);else renderer.renderLeftHand(pose,buffers,light,player);
            }
            pose.popPose();
        }
        pose.popPose();
    }
    private static ResourceLocation receiptTexture(String details) {
        var cached = RECEIPTS.get(details);
        if (cached != null) return cached;
        var printed = AtmReceiptPrint.print(details);
        var nativeImage = new NativeImage(printed.getWidth(), printed.getHeight(), false);
        for (int y = 0; y < printed.getHeight(); y++) for (int x = 0; x < printed.getWidth(); x++) {
            int argb = printed.getRGB(x, y);
            nativeImage.setPixelRGBA(x, y, argb & 0xFF00FF00 | (argb >> 16 & 255) | (argb & 255) << 16);
        }
        var manager = Minecraft.getInstance().getTextureManager();
        var id = manager.register("atm_printed_receipt", new DynamicTexture(nativeImage));
        RECEIPTS.put(details, id);
        if (RECEIPTS.size() > 16) {
            var oldest = RECEIPTS.entrySet().iterator();
            var entry = oldest.next();
            manager.release(entry.getValue()); oldest.remove();
        }
        return id;
    }
    private static void pickupTransform(PoseStack pose, double age, boolean receipt) {
        var motion = AtmDispenseMotion.pickup(age, receipt);
        pose.translate(motion.x(), motion.y(), motion.z());
        pose.mulPose(Axis.XP.rotationDegrees((float) motion.pitch()));
        pose.mulPose(Axis.ZP.rotationDegrees((float) motion.roll()));
    }
    private static void hand(AtmBlockEntity atm, PoseStack pose, MultiBufferSource buffers, double age, int light) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isInvisible() || !mc.player.getUUID().equals(atm.collector()) || !AtmCameraController.isViewing(atm.getBlockPos())) return;
        double reach = AtmDispenseMotion.pickup(age, false).reach();
        var renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(mc.player);
        boolean right = mc.player.getMainArm() == HumanoidArm.RIGHT;
        pose.pushPose();
        pose.translate((right ? 1 : -1) * (.20 * (1 - reach) + .13), -.30 + .18 * reach, .12 * (1 - reach) - .29);
        pose.mulPose(Axis.XP.rotationDegrees(75)); pose.mulPose(Axis.ZP.rotationDegrees(right ? -10 : 10));
        pose.scale(.4F, .4F, .4F);
        if (right) renderer.renderRightHand(pose, buffers, light, mc.player); else renderer.renderLeftHand(pose, buffers, light, mc.player);
        pose.popPose();
    }
}
