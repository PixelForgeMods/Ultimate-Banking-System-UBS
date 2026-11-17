package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.gui.screens.ShelfFillWizardScreen;
import net.austizz.ultimatebankingsystem.network.ShelfFillActionPayload;
import net.austizz.ultimatebankingsystem.shelf.ShelfService;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID, value = Dist.CLIENT)
public final class ShelfFillSelectionEvents {
    private ShelfFillSelectionEvents() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND
                || !ShelfFillWizardScreen.isSelecting()) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity().isShiftKeyDown()) {
            PacketDistributor.sendToServer(ShelfFillActionPayload.of("DONE"));
        } else if (ShelfService.isShelf(event.getLevel().getBlockState(event.getPos()))
                && ShelfService.getDisplayEntity(event.getLevel(), event.getPos()) != null
                && ShelfService.getDisplayEntity(event.getLevel(), event.getPos()).isShopMode()) {
            var pos = event.getPos();
            ShelfFillWizardScreen.togglePosition(ShelfService.toLowerShelfPos(event.getLevel(), pos));
            PacketDistributor.sendToServer(ShelfFillActionPayload.toggle(pos.getX(), pos.getY(), pos.getZ()));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND
                || !ShelfFillWizardScreen.isSelecting()) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getEntity().isShiftKeyDown()) {
            PacketDistributor.sendToServer(ShelfFillActionPayload.of("DONE"));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (!event.getLevel().isClientSide() || event.getHand() != InteractionHand.MAIN_HAND
                || !ShelfFillWizardScreen.isSelecting()) return;
        if (event.getEntity().isShiftKeyDown()) {
            PacketDistributor.sendToServer(ShelfFillActionPayload.of("DONE"));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getLevel().isClientSide() || !ShelfFillWizardScreen.isSelecting()
                || !event.getEntity().isShiftKeyDown()) return;
        event.setCanceled(true);
        ShelfFillWizardScreen.cancelSelection();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        if (!event.getLevel().isClientSide() || !ShelfFillWizardScreen.isSelecting()
                || !event.getEntity().isShiftKeyDown()) return;
        ShelfFillWizardScreen.cancelSelection();
    }

    @SubscribeEvent
    public static void renderSelection(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || !ShelfFillWizardScreen.isSelecting()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        var buffers = minecraft.renderBuffers().bufferSource();
        var lines = buffers.getBuffer(RenderType.lines());
        for (BlockPos pos : ShelfFillWizardScreen.selectedPositions()) {
            if (pos.distToCenterSqr(camera) > 128 * 128) continue;
            AABB bounds = new AABB(pos).inflate(0.025);
            LevelRenderer.renderLineBox(pose, lines, bounds.minX, bounds.minY, bounds.minZ,
                    bounds.maxX, bounds.maxY, bounds.maxZ, 0.39F, 0.94F, 0.63F, 0.95F);
        }
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    @SubscribeEvent
    public static void renderSelectionHint(RenderGuiEvent.Post event) {
        if (!ShelfFillWizardScreen.isSelecting()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null) return;
        var graphics = event.getGuiGraphics();
        int x = graphics.guiWidth() / 2 - 182;
        int y = graphics.guiHeight() - 125;
        graphics.fill(x, y, x + 364, y + 48, 0xE610263B);
        graphics.fill(x, y, x + 3, y + 48, 0xFF64DCA0);
        graphics.drawString(minecraft.font, "SHELF SELECTION  |  "
                        + ShelfFillWizardScreen.selectedPositions().size() + " shelves",
                x + 10, y + 6, 0xFFF3F8FF, false);
        graphics.drawString(minecraft.font, "Right-click: toggle   Sneak + right-click: review",
                x + 10, y + 20, 0xFF9AB3C7, false);
        graphics.drawString(minecraft.font, "Sneak + left-click: cancel selection",
                x + 10, y + 33, 0xFFF4C66A, false);
    }
}
