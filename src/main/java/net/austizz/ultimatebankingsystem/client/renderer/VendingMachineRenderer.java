package net.austizz.ultimatebankingsystem.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.austizz.ultimatebankingsystem.block.custom.VendingMachineBlock;
import net.austizz.ultimatebankingsystem.block.entity.custom.VendingMachineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

/** Renders only the eight dynamic product previews; the Blockbench mesh remains a baked model. */
public final class VendingMachineRenderer implements BlockEntityRenderer<VendingMachineBlockEntity> {
    // The display slots sit just above the shelf tops at 26, 21, 16 and 11 pixels.
    private static final float[] PRODUCT_Y = {1.72F, 1.40F, 1.09F, 0.78F};
    private static final float[] PRODUCT_X = {0.35F, 0.7125F};
    private static final float PRODUCT_Z = 0.59375F;
    private static final float PRODUCT_SCALE = 0.30F;

    public VendingMachineRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public AABB getRenderBoundingBox(VendingMachineBlockEntity machine) {
        return machine == null ? AABB.INFINITE : machine.getRenderBoundingBox();
    }

    @Override
    public void render(VendingMachineBlockEntity machine,
                       float partialTick,
                       PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       int packedOverlay) {
        BlockState state = machine.getBlockState();
        if (!state.hasProperty(VendingMachineBlock.HALF)
                || state.getValue(VendingMachineBlock.HALF) != DoubleBlockHalf.LOWER) {
            return;
        }

        // Keep the previews in the same coordinate space as the baked blockstate model.
        // The old renderer inverted north and south, which put the item models across
        // the wrong face and made them look like part of the machine texture.
        float yaw = modelYaw(state.getValue(VendingMachineBlock.FACING));
        for (int slot = 0; slot < VendingMachineBlockEntity.DISPLAY_SLOT_COUNT; slot++) {
            ItemStack stack = machine.getDisplayItem(slot);
            if (stack.isEmpty()) {
                continue;
            }

            int shelf = slot / VendingMachineBlockEntity.DISPLAY_SLOTS_PER_SHELF;
            int side = slot % VendingMachineBlockEntity.DISPLAY_SLOTS_PER_SHELF;

            poseStack.pushPose();
            poseStack.translate(0.5D, PRODUCT_Y[shelf], 0.5D);
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            poseStack.translate(PRODUCT_X[side] - 0.5F, 0.0D, PRODUCT_Z - 0.5F);
            // The exported machine faces north. This keeps product fronts facing the customer.
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            poseStack.scale(PRODUCT_SCALE, PRODUCT_SCALE, PRODUCT_SCALE);

            Minecraft.getInstance().getItemRenderer().renderStatic(
                    stack,
                    ItemDisplayContext.FIXED,
                    packedLight,
                    packedOverlay,
                    poseStack,
                    bufferSource,
                    machine.getLevel(),
                    0
            );
            poseStack.popPose();

            Vec3 labelOffset = new Vec3(
                    PRODUCT_X[side] - 0.5D,
                    0.23D,
                    PRODUCT_Z - 0.5D + 0.09D
            ).yRot((float) Math.toRadians(yaw));
            ShelfPriceLabelRenderer.queueDollars(
                    machine.getLevel(),
                    machine.getBlockPos(),
                    slot,
                    machine.getPriceDollars(slot),
                    new Vec3(0.5D, PRODUCT_Y[shelf], 0.5D).add(labelOffset)
            );
        }
    }

    private static float modelYaw(Direction facing) {
        return switch (facing) {
            case NORTH -> 0.0F;
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }
}
