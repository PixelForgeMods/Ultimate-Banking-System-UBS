package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.UltimateBankingSystem;
import net.austizz.ultimatebankingsystem.block.ModBlocks;
import net.austizz.ultimatebankingsystem.block.custom.ATMBlock;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.lwjgl.glfw.GLFW;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import net.austizz.ultimatebankingsystem.block.entity.custom.AtmBlockEntity;
import net.austizz.ultimatebankingsystem.sound.ModSounds;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.austizz.ultimatebankingsystem.item.ModItems;
import net.austizz.ultimatebankingsystem.item.CreditCardItem;
import net.austizz.ultimatebankingsystem.util.ItemStackDataCompat;
import net.austizz.ultimatebankingsystem.payments.CreditCardService;
import net.austizz.ultimatebankingsystem.network.AtmCardRequestPayload;
import net.austizz.ultimatebankingsystem.network.AtmCardResultPayload;
import net.austizz.ultimatebankingsystem.network.AtmPinRequestPayload;
import net.austizz.ultimatebankingsystem.network.AtmPinResultPayload;
import net.austizz.ultimatebankingsystem.network.AtmActionRequestPayload;
import net.austizz.ultimatebankingsystem.network.AtmActionResultPayload;
import net.austizz.ultimatebankingsystem.network.AtmDispenserPayload;
import net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion.Phase;
import net.austizz.ultimatebankingsystem.atm.AtmSoundTiming;
import net.minecraft.world.item.ItemStack;
import java.util.UUID;

@EventBusSubscriber(modid = UltimateBankingSystem.MODID, value = Dist.CLIENT)
public final class AtmCameraController {
    private static final long TRANSITION_NANOS = 900_000_000L;
    private static Session session;
    private static BlockPos pendingAtm;
    private static long pendingSince;
    private static double renderedFov = 70;

    public static boolean isViewing(BlockPos pos) { return session != null && !session.returning && session.atm.equals(pos); }
    public static boolean hasCardInReader(BlockPos pos) { return session != null && session.atm.equals(pos) && session.cardMotion.inReader(System.nanoTime()); }
    private static void hardwareAction(String action) { PacketDistributor.sendToServer(new AtmDispenserPayload(session.atm, action)); }

    private AtmCameraController() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (session != null && event.usedConfiguredFov() && Double.isFinite(event.getFOV())
                && event.getFOV() > 0 && event.getFOV() < 180) renderedFov = event.getFOV();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (session != null) {
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND
                || mc.player == null || mc.level == null || mc.screen != null
                || mc.getCameraEntity() != mc.player || !mc.player.isAlive()
                || ClaimModeClientState.active() || SmartphoneClientState.isInteractive()) {
            return;
        }
        BlockPos atm = findAtm(mc);
        if (atm == null) return;
        if (pendingAtm == null) {
            pendingAtm = atm; pendingSince = System.nanoTime();
            PacketDistributor.sendToServer(new AtmDispenserPayload(atm, "VIEW_OPEN"));
        }
        event.setSwingHand(false); event.setCanceled(true);
    }

    public static void viewResult(net.austizz.ultimatebankingsystem.network.AtmViewResultPayload result) {
        var mc = Minecraft.getInstance();
        if (pendingAtm == null || !pendingAtm.equals(result.atm()) || session != null
                || mc.level == null || mc.player == null || mc.screen != null) {
            if (result.accepted() && mc.getConnection() != null) PacketDistributor.sendToServer(new AtmDispenserPayload(result.atm(), "VIEW_CLOSE"));
            return;
        }
        pendingAtm = null;
        if (!result.accepted()) { mc.player.displayClientMessage(Component.literal("This ATM is already in use."), true); return; }
        openCamera(mc, result.atm());
    }

    private static void openCamera(Minecraft mc, BlockPos atm) {
        session = new Session(mc, atm);
        mc.options.hideGui = true;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setScreen(session.screen);
        if (mc.screen != session.screen) {
            stop(mc);
            return;
        }
        clearWalkingInput(mc.player);
        Vec3 movement = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(0, movement.y, 0);
        mc.player.setSprinting(false);
        mc.setCameraEntity(session.camera);
        GLFW.glfwSetInputMode(mc.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
    }

    private static BlockPos findAtm(Minecraft mc) {
        if(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
            var state=mc.level.getBlockState(blockHit.getBlockPos());
            if(state.getBlock() instanceof net.austizz.ultimatebankingsystem.block.custom.InWallATMBlock) {
                if (state.getValue(net.austizz.ultimatebankingsystem.block.custom.InWallATMBlock.PART)
                        >= net.austizz.ultimatebankingsystem.block.custom.InWallATMBlock.WALL_CELLS) return null;
                var root=net.austizz.ultimatebankingsystem.block.custom.InWallATMBlock.origin(blockHit.getBlockPos(),state);
                boolean material=mc.player.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem;
                boolean removing=mc.player.isShiftKeyDown() && mc.player.getMainHandItem().isEmpty();
                if((material||removing) && net.austizz.ultimatebankingsystem.block.custom.InWallATMBlock.isSurround(blockHit.getLocation(),root,state.getValue(ATMBlock.FACING)))return null;
                return root;
            }
        }
        Vec3 start = mc.player.getEyePosition();
        Vec3 end = start.add(mc.player.getLookAngle().scale(Math.min(8, mc.player.blockInteractionRange())));
        double closest = start.distanceToSqr(end);
        if (mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS) {
            closest = Math.min(closest, start.distanceToSqr(mc.hitResult.getLocation()) + .000001);
        }
        BlockPos result = null;
        // A single tall block's base can be one cell below vanilla's ray traversal.
        for (BlockPos pos : BlockPos.betweenClosed(
                BlockPos.containing(Math.min(start.x, end.x), Math.min(start.y, end.y) - 1, Math.min(start.z, end.z)),
                BlockPos.containing(Math.max(start.x, end.x), Math.max(start.y, end.y), Math.max(start.z, end.z)))) {
            var state = mc.level.getBlockState(pos);
            if (!state.is(ModBlocks.ATM_MACHINE.get())) continue;
            var hit = state.getShape(mc.level, pos).clip(start, end, pos);
            if (hit != null && start.distanceToSqr(hit.getLocation()) <= closest) {
                closest = start.distanceToSqr(hit.getLocation());
                result = pos.immutable();
            }
        }
        return result;
    }

    private static void clearWalkingInput(LocalPlayer player) {
        // Detached-camera players retain the movement axes from their last controlled tick.
        player.xxa = 0;
        player.yya = 0;
        player.zza = 0;
        player.setJumping(false);
        player.input.leftImpulse = 0;
        player.input.forwardImpulse = 0;
        player.input.up = false;
        player.input.down = false;
        player.input.left = false;
        player.input.right = false;
        player.input.jumping = false;
        assert player.xxa == 0 && player.zza == 0 && !player.input.jumping
                : "ATM camera must not retain walking input";
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (session != null && event.getEntity() == session.player) {
            clearWalkingInput(session.player);
        }
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (pendingAtm != null && System.nanoTime() - pendingSince > 5_000_000_000L) pendingAtm = null;
        if (session == null) return;
        if (!session.returning && session.player.tickCount % 20 == 0) hardwareAction("VIEW_PING");
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != session.level || mc.player != session.player || !session.player.isAlive()
                || mc.screen != session.screen || mc.getCameraEntity() != session.camera) {
            stop(mc);
            return;
        }
        if (!session.returning && mc.level.getBlockEntity(session.atm) instanceof AtmBlockEntity atm
                && !atm.isViewer(mc.player)) { stop(mc); return; }
        var state = mc.level.getBlockState(session.atm);
        if (!(state.getBlock() instanceof ATMBlock) || state.getValue(ATMBlock.FACING) != session.facing
                || mc.player.distanceToSqr(Vec3.atCenterOf(session.atm)) > 64) {
            session.cardMotion.reset();
            session.cardRequest = null;
            returnToPlayer();
        }
    }

    @SubscribeEvent
    public static void onFrame(RenderFrameEvent.Pre event) {
        if (session == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != session.level || mc.player != session.player) {
            stop(mc);
            return;
        }
        mc.options.hideGui = true;
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        long now = System.nanoTime();
        updateCard(now);
        if (session.level.getBlockEntity(session.atm) instanceof AtmBlockEntity atm) {
            if (atm.sessionExpired() && !session.exitRequested && session.cardMotion.phase == AtmCardMotion.Phase.INSERTED) {
                atm.lcd().showNotice("Session expired. Your card is being returned.", now);
                returnToPlayer();
            }
            if (session.hardwareSeen && !atm.dispensing() && session.cardMotion.phase == AtmCardMotion.Phase.INSERTED) {
                atm.lcd().readPublic(atm.sharedLcd().publicSnapshot(now)); session.hardwareSeen = false;
            }
            boolean depositing = atm.deposit().active();
            if (session.depositWasActive && !depositing && !session.exitRequested) {
                atm.lcd().readPublic(atm.sharedLcd().publicSnapshot(now)); session.hardwareSeen = false;
            }
            session.depositWasActive = depositing;
            if (atm.phase() == Phase.WAIT_CARD || atm.phase() == Phase.TAKE_CARD)
                session.cardMotion.presentCard(AtmSoundTiming.cardEjection(atm.phase(), atm.phaseAge(event.getPartialTick().getGameTimeDeltaPartialTick(false))));
            atm.lcd().updateCardStatus(switch (session.cardMotion.phase) {
                case REQUESTING -> net.austizz.ultimatebankingsystem.atm.AtmLcdState.Status.CHECKING;
                case INSERTING -> net.austizz.ultimatebankingsystem.atm.AtmLcdState.Status.INSERTING;
                case INSERTED -> net.austizz.ultimatebankingsystem.atm.AtmLcdState.Status.INSERTED;
                case EJECTING, REMOVING -> net.austizz.ultimatebankingsystem.atm.AtmLcdState.Status.REMOVING;
                default -> net.austizz.ultimatebankingsystem.atm.AtmLcdState.Status.WELCOME;
            }, now);
            atm.lcd().showDispenser(atm.phase(), atm.phaseAge(0));
            if (atm.dispensing()) session.hardwareSeen = true;
            else if (session.hardwareSeen && session.cardMotion.phase == AtmCardMotion.Phase.EMPTY) { session.hardwareSeen = false; returnToPlayer(); }
            if (session.pinRequest != null && now - session.pinStarted > 10_000_000_000L) {
                session.pinRequest = null;
                atm.lcd().pinResult(false, "PIN check timed out. Please try again.");
            }
            if (session.menuRequest != null && now - session.menuStarted > 10_000_000_000L) {
                session.menuRequest = null;
                if (session.depositTray.pending) { session.depositTray.result(false, "Request timed out. Check the accepted total."); session.depositTray.clear(); }
                atm.lcd().actionResult(false, "", "Request timed out. Check balance or history before retrying.");
            }
        }
        double seconds = (now - session.lastFrame) / 1_000_000_000.0;
        session.lastFrame = now;
        double progress = (now - session.started) / (double) TRANSITION_NANOS;
        AtmCameraMotion.Pose target = session.docked;
        if (session.returning) {
            Vec3 eye = mc.player.getEyePosition(event.getPartialTick().getGameTimeDeltaPartialTick(false))
                    .add(session.returnOffset);
            boolean mirrored = session.cameraType == CameraType.THIRD_PERSON_FRONT;
            target = new AtmCameraMotion.Pose(eye.x, eye.y, eye.z,
                    mc.player.getYRot() + (mirrored ? 180 : 0), mc.player.getXRot() * (mirrored ? -1 : 1));
        } else {
            double mouseX = AtmCameraMotion.cursorAxis(mc.mouseHandler.xpos(), mc.getWindow().getScreenWidth());
            double mouseY = AtmCameraMotion.cursorAxis(mc.mouseHandler.ypos(), mc.getWindow().getScreenHeight());
            session.swayX = AtmCameraMotion.followCursor(session.swayX, mouseX, seconds);
            session.swayY = AtmCameraMotion.followVerticalCursor(session.swayY, mouseY, seconds);
            target = AtmCameraMotion.cursorSway(session.docked, session.swayX, session.swayY, AtmControls.inWall(session.atm));

        }
        var depositMachine = session.level.getBlockEntity(session.atm) instanceof AtmBlockEntity terminal ? terminal : null;
        boolean depositActive = !session.returning && !session.exitRequested && depositMachine != null && AtmCameraMotion.feederCamera(depositMachine.phase());
        boolean depositFocus = depositActive && (session.depositTray.count() > 0 || depositMachine.phase() == Phase.DEPOSIT_INSERTING
                || depositMachine.phase() == Phase.DEPOSIT_RETURNING || depositMachine.phase() == Phase.DEPOSIT_RETURN_READY || depositMachine.phase() == Phase.DEPOSIT_PICKUP);
        if (depositActive != session.depositCameraActive || depositFocus != session.depositCameraFocus) {
            session.depositCameraFrom = session.current; session.depositCameraStarted = now;
            if (!depositActive) { session.from = session.current; session.started = now; progress = 0; }
            session.depositCameraActive = depositActive; session.depositCameraFocus = depositFocus;
        }
        if (depositActive) {
            var base = AtmCameraMotion.deposit(session.docked, session.facing.getStepX(), session.facing.getStepZ());
            var slot = AtmControls.world(AtmControls.cashSlot(session.atm), session.atm, session.facing);
            target = depositFocus ? AtmCameraMotion.lookAt(base, slot.x, slot.y, slot.z)
                    : AtmCameraMotion.cursorSway(base, session.swayX, session.swayY, AtmControls.inWall(session.atm));
            session.current = AtmCameraMotion.interpolate(session.depositCameraFrom,target,(now-session.depositCameraStarted)/700_000_000.0);
        } else if (!session.returning && session.cardMotion.focusesCamera()) {
            long duration = session.cardMotion.phase == AtmCardMotion.Phase.INSERTING
                    ? AtmCardMotion.INSERT_NANOS : AtmCardMotion.REMOVE_NANOS;
            double cardProgress = (now - session.cardMotion.started) / (double) duration;
            var focus = AtmCameraMotion.interpolate(readerView(), target, (cardProgress - .70) / .30);
            session.current = AtmCameraMotion.interpolate(session.cardCameraFrom, focus,
                    (now - session.cardMotion.started) / 350_000_000.0);
        } else {
            session.current = AtmCameraMotion.interpolate(session.from, target, progress);
        }
        session.moveCamera();
        if (session.returning && progress >= 1) stop(mc);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHud(RenderGuiEvent.Pre event) {
        if (session != null) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHand(RenderHandEvent event) {
        if (session != null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onCardRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES || session == null || session.returning) return;
        AtmCardRenderer.render(event, session.atm, session.facing, session.player, session.card,
                session.cardHand, session.cardMotion.sample(System.nanoTime()));
    }

    private static AtmCameraMotion.Pose readerView() {
        var eye = session.docked;
        var target = AtmControls.world(AtmControls.readerSlot(session.atm), session.atm, session.facing);
        double dx = target.x - eye.x(), dy = target.y - eye.y(), dz = target.z - eye.z();
        return new AtmCameraMotion.Pose(eye.x(), eye.y(), eye.z(),
                (float) Math.toDegrees(Math.atan2(-dx, dz)),
                (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz))));
    }

    private static void cardMessage(String message) {
        if (session.level.getBlockEntity(session.atm) instanceof AtmBlockEntity atm) {
            atm.lcd().showNotice(message, System.nanoTime());
        }
    }

    private static void clearCardMessage() {
        if (session.level.getBlockEntity(session.atm) instanceof AtmBlockEntity atm) atm.lcd().clearNotice();
    }

    public static void cardResult(AtmCardResultPayload result) {
        if (session == null || session.returning || !result.atm().equals(session.atm)
                || !result.requestId().equals(session.cardRequest)
                || session.cardMotion.phase != AtmCardMotion.Phase.REQUESTING) return;
        session.cardRequest = null;
        if (!result.accepted() || result.card().isEmpty()) {
            session.cardMotion.reset();
            cardMessage(result.message());
            return;
        }
        session.card = result.card().copy();
        var visualTag = ItemStackDataCompat.getCustomData(session.card);
        visualTag.putBoolean(CreditCardItem.TAG_BACK_VISIBLE, false);
        ItemStackDataCompat.setCustomData(session.card, visualTag);
        session.cardHand = result.hand();
        session.cardCameraFrom = session.current;
        if (session.level.getBlockEntity(session.atm) instanceof AtmBlockEntity atm) atm.lcd().prepareCard(result.needsPin());
        session.cardMotion.accept(System.nanoTime());
        clearCardMessage();
    }

    private static void endBankSession() {
        if (session.bankToken != null && Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new AtmActionRequestPayload(session.bankToken, UUID.randomUUID(), 0, "END", ""));
        }
        session.bankToken = null;
        session.menuRequest = null;
    }

    private static void startRemoval() {
        boolean dispensing = session.level.getBlockEntity(session.atm) instanceof AtmBlockEntity machine && machine.dispensing();
        if (session.level.getBlockEntity(session.atm) instanceof AtmBlockEntity terminal && terminal.deposit().active()) {
            hardwareAction("DEPOSIT_ABORT"); dispensing = false;
        }
        if (dispensing) hardwareAction("CARD_BEGIN");
        endBankSession();
        session.pinRequest = null;
        if (session.level.getBlockEntity(session.atm) instanceof AtmBlockEntity atm) {
            atm.lcd().setStatus(net.austizz.ultimatebankingsystem.atm.AtmLcdState.Status.REMOVING);
        }
        session.cardCameraFrom = session.current;
        session.cardMotion.remove(System.nanoTime());
        if (!dispensing && session.cardMotion.phase == AtmCardMotion.Phase.EJECTING) hardwareAction("CARD_MOTOR");
        clearCardMessage();
    }

    public static void pinResult(AtmPinResultPayload result) {
        if (session == null || session.returning || session.cardMotion.phase != AtmCardMotion.Phase.INSERTED
                || !result.atm().equals(session.atm) || !result.requestId().equals(session.pinRequest)) return;
        session.pinRequest = null;
        if (session.level.getBlockEntity(session.atm) instanceof AtmBlockEntity atm) {
            atm.lcd().pinResult(result.accepted(), result.message());
            if (result.accepted()) { session.bankToken = result.sessionToken(); session.menuSequence = 0; }
        }
    }

    public static void depositStock(net.austizz.ultimatebankingsystem.network.AtmDepositStockPayload stock) {
        if (session != null && session.atm.equals(stock.atm())) session.depositTray.stock(stock.bills().stream().mapToInt(Integer::intValue).toArray());
    }
    private static void depositRequest(String action) {
        if (session == null || session.bankToken == null || session.menuRequest != null) return;
        session.menuRequest = UUID.randomUUID(); session.menuStarted = System.nanoTime();
        PacketDistributor.sendToServer(new AtmActionRequestPayload(session.bankToken, session.menuRequest, ++session.menuSequence, action, ""));
    }
    public static void actionResult(AtmActionResultPayload result) {
        if (session == null || session.returning || session.cardMotion.phase != AtmCardMotion.Phase.INSERTED
                || !result.requestId().equals(session.menuRequest)) return;
        session.menuRequest = null;
        if (session.depositTray.pending) session.depositTray.result(result.success(), result.message());
        else if (!result.success()) session.depositTray.message = result.message();
        if (session.level.getBlockEntity(session.atm) instanceof AtmBlockEntity atm) {
            atm.lcd().actionResult(result.success(), result.balance(), result.message());
        }
    }

    private static void updateCard(long now) {
        if (session.cardMotion.phase == AtmCardMotion.Phase.REQUESTING
                && now - session.cardMotion.started > 10_000_000_000L) {
            session.cardMotion.reset();
            session.cardRequest = null;
            cardMessage("Card validation timed out. Try again.");
        }
        if (!session.cardMotion.advance(now)) return;
        session.from = session.current;
        session.started = now - TRANSITION_NANOS;
        if (session.cardMotion.phase == AtmCardMotion.Phase.REMOVING) {
            session.cardCameraFrom = session.current;
            return;
        }
        if (session.cardMotion.phase == AtmCardMotion.Phase.INSERTED) {
            if (session.exitRequested) startRemoval();
            else clearCardMessage();
        } else {
            session.card = ItemStack.EMPTY;
            hardwareAction("CARD_TAKEN");
            if (session.exitRequested) returnToPlayer();
            else clearCardMessage();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerRender(RenderPlayerEvent.Pre event) {
        // Hide only our local render while the camera passes through the player's head.
        if (session != null && event.getEntity() == session.player) event.setCanceled(true);
    }

    private static void returnToPlayer() {
        if (session == null || session.returning) return;
        session.exitRequested = true;
        if (session.cardMotion.phase == AtmCardMotion.Phase.INSERTED) {
            startRemoval();
            return;
        }
        if (session.cardMotion.phase == AtmCardMotion.Phase.INSERTING
                || session.cardMotion.phase == AtmCardMotion.Phase.EJECTING
                || session.cardMotion.phase == AtmCardMotion.Phase.REMOVING) return;
        session.cardMotion.reset();
        session.cardRequest = null;
        session.from = session.current;
        session.started = System.nanoTime();
        session.returning = true;
    }

    private static void stop(Minecraft mc) {
        if (session == null) return;
        endBankSession();
        if (Minecraft.getInstance().getConnection() != null) hardwareAction("VIEW_CLOSE");
        Session old = session;
        if (old.level.getBlockEntity(old.atm) instanceof AtmBlockEntity atm) {
            atm.lcd().setStatus(net.austizz.ultimatebankingsystem.atm.AtmLcdState.Status.WELCOME);
            atm.lcd().clearNotice();
        }
        session = null;
        mc.options.hideGui = old.hideGui;
        mc.options.setCameraType(old.cameraType);
        if (mc.getCameraEntity() == old.camera) mc.setCameraEntity(mc.player);
        GLFW.glfwSetInputMode(mc.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
        if (mc.screen == old.screen) mc.setScreen(null);
    }

    private static final class Session {
        final ClientLevel level;
        final LocalPlayer player;
        final BlockPos atm;
        final Direction facing;
        final boolean hideGui;
        final CameraType cameraType;
        final RemotePlayer camera;
        final Screen screen = new CameraScreen();
        final AtmCameraMotion.Pose original;
        final AtmCameraMotion.Pose docked;
        final Vec3 returnOffset;
        AtmCameraMotion.Pose from;
        AtmCameraMotion.Pose current;
        long started = System.nanoTime();
        long lastFrame = started;
        long trayFrame = started;
        double swayX;
        double swayY;
        boolean returning;
        final AtmCardMotion cardMotion = new AtmCardMotion();
        UUID cardRequest;
        UUID pinRequest;
        long pinStarted;
        UUID bankToken;
        UUID menuRequest;
        int menuSequence;
        long menuStarted;
        boolean hardwareSeen;
        final AtmDepositTray depositTray = new AtmDepositTray();
        boolean depositCameraActive, depositCameraFocus, depositWasActive;
        long depositCameraStarted;
        AtmCameraMotion.Pose depositCameraFrom;
        ItemStack card = ItemStack.EMPTY;
        InteractionHand cardHand = InteractionHand.MAIN_HAND;
        AtmCameraMotion.Pose cardCameraFrom;
        boolean exitRequested;

        Session(Minecraft mc, BlockPos atm) {
            this.level = mc.level;
            this.player = mc.player;
            this.atm = atm;
            this.facing = level.getBlockState(atm).getValue(ATMBlock.FACING);
            this.hideGui = mc.options.hideGui;
            this.cameraType = mc.options.getCameraType();
            var view = mc.gameRenderer.getMainCamera();
            Vec3 start = view.getPosition();
            original = new AtmCameraMotion.Pose(start.x, start.y, start.z, view.getYRot(), view.getXRot());
            from = current = original;
            returnOffset = start.subtract(player.getEyePosition(mc.getTimer().getGameTimeDeltaPartialTick(false)));
            if (AtmControls.inWall(atm)) {
                var screen = AtmControls.screen(atm);
                var center = AtmControls.rotateX(screen.bounds().getCenter().subtract(screen.origin()), screen.angle()).add(screen.origin());
                // Center on the recess, with room below the LCD for the keypad and cash tray.
                var eye = AtmControls.world(new Vec3(16, center.y, -2.75), atm, facing);
                var target = AtmControls.world(new Vec3(16, center.y - 1.75, center.z), atm, facing);
                docked = AtmCameraMotion.lookAt(new AtmCameraMotion.Pose(eye.x, eye.y, eye.z, 0, 0), target.x, target.y, target.z);
            } else docked = AtmCameraMotion.screen(atm.getX(), atm.getY(), atm.getZ(), facing.getStepX(), facing.getStepZ());
            camera = new RemotePlayer(level, player.getGameProfile());
            camera.setPose(player.getPose());
            camera.noPhysics = true;
            moveCamera();
        }

        void moveCamera() {
            camera.setPos(current.x(), current.y() - camera.getEyeHeight(), current.z());
            camera.setYRot(current.yaw());
            camera.setXRot(current.pitch());
            // LivingEntity camera yaw interpolates head rotation, not body rotation.
            camera.setYHeadRot(current.yaw());
            camera.yHeadRotO = camera.getYHeadRot();
            camera.xo = camera.getX();
            camera.yo = camera.getY();
            camera.zo = camera.getZ();
            camera.yRotO = camera.getYRot();
            camera.xRotO = camera.getXRot();
            assert camera.getViewYRot(0) == current.yaw()
                    && camera.getViewYRot(.5F) == current.yaw()
                    && camera.getViewYRot(1) == current.yaw() : "ATM camera yaw must not oscillate between ticks";
        }
    }

    private static final class CameraScreen extends Screen {
        CameraScreen() {
            super(Component.literal("ATM camera"));
        }

        @Override
        protected void init() {
            GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(),
                    minecraft.getWindow().getScreenWidth() / 2.0, minecraft.getWindow().getScreenHeight() / 2.0);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (session == null || session.returning) return true;
            if (session.level.getBlockEntity(session.atm) instanceof AtmBlockEntity terminal && terminal.deposit().active()) {
                if (terminal.phase() == Phase.DEPOSIT_FEEDING && !session.depositTray.pending && session.menuRequest == null) {
                    int cell = AtmDepositTray.hit(mouseX, mouseY, width, height);
                    if (cell >= 0 && (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
                        session.depositTray.take(cell, button == GLFW.GLFW_MOUSE_BUTTON_RIGHT); return true;
                    }
                }
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && session.depositTray.count() == 0) {
                    int control = buttonAt(mouseX, mouseY);
                    if (control >= 0 && control < 24 && terminal.press(control, System.nanoTime())) {
                        hardwareAction("KEY_" + control);
                        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.ATM_KEYPRESS.get(), 1, .45F));
                    }
                    if (terminal.phase() == Phase.DEPOSIT_FEEDING && (control == 23 || control == AtmControls.LCD)) depositRequest("DEPOSIT_COUNT");
                    else if (terminal.phase() == Phase.DEPOSIT_REVIEW && control == 22) hardwareAction("DEPOSIT_BACK");
                    else if (terminal.phase() == Phase.DEPOSIT_REVIEW && control == 19) depositRequest("DEPOSIT_CONFIRM");
                    else if ((terminal.phase() == Phase.DEPOSIT_REVIEW || terminal.phase() == Phase.DEPOSIT_FEEDING) && (control == 23 || control == 3)) hardwareAction("RETURN_DEPOSIT");
                    else if (terminal.phase() == Phase.DEPOSIT_FEEDING && (control == 19 || control == 3)) hardwareAction("RETURN_DEPOSIT");
                    else if (terminal.phase() == Phase.DEPOSIT_RETURN_READY && (control == AtmControls.CASH || control == 23)) hardwareAction("PICK_DEPOSIT");
                }
                return true;
            }
            if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
            int index = buttonAt(mouseX, mouseY);
            if (index == AtmControls.CASH || index == AtmControls.RECEIPT) {
                hardwareAction(index == AtmControls.CASH ? "PICK_CASH" : "PICK_RECEIPT");
                return true;
            }
            if (index == AtmControls.READER) {
                if (session.level.getBlockEntity(session.atm) instanceof AtmBlockEntity atm && atm.dispensing()
                        && atm.phase() != Phase.TAKE_CARD) return true;
                if (session.cardMotion.phase == AtmCardMotion.Phase.INSERTED) startRemoval();
                else if (session.cardMotion.request(System.nanoTime())) {
                    session.cardRequest = UUID.randomUUID();
                    cardMessage("Checking card...");
                    PacketDistributor.sendToServer(new AtmCardRequestPayload(session.atm, session.cardRequest));
                }
                return true;
            }
            if (index >= 0 && session.level.getBlockEntity(session.atm) instanceof AtmBlockEntity atm
                    && atm.press(index, System.nanoTime())) {
                hardwareAction("KEY_" + index);
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.ATM_KEYPRESS.get(), 1, .45F));
                switch (atm.lcd().press(index)) {
                    case CANCEL -> returnToPlayer();
                    case RECEIPT_YES -> hardwareAction("RECEIPT_YES");
                    case RECEIPT_NO -> hardwareAction("RECEIPT_NO");
                    case PICK_CASH -> hardwareAction("PICK_CASH");
                    case SUBMIT_PIN -> {
                        var tag = ItemStackDataCompat.getCustomData(session.card);
                        boolean creating = atm.lcd().creatingPin();
                        String repeated = atm.lcd().takePin();
                        String pin = creating ? atm.lcd().takeFirstPin() : repeated;
                        if (!tag.hasUUID(CreditCardService.TAG_CARD_ID)) {
                            atm.lcd().pinResult(false, "Please insert your card again.");
                        } else {
                            session.pinRequest = UUID.randomUUID();
                            session.pinStarted = System.nanoTime();
                            PacketDistributor.sendToServer(new AtmPinRequestPayload(session.atm, session.pinRequest,
                                    tag.getUUID(CreditCardService.TAG_CARD_ID), pin, creating, creating ? repeated : ""));
                        }
                    }
                    case COPY_ACCOUNT -> {
                        if (atm.lcd().accountId().matches("[0-9a-fA-F-]{36}")) {
                            minecraft.keyboardHandler.setClipboard(atm.lcd().accountId());
                            atm.lcd().showNotice("Account ID copied.", System.nanoTime());
                        }
                    }
                    case PAY_LIST, PAY_PLAYERS, PAY_SEND, PAY_ACCEPT, PAY_DECLINE, ACCOUNT_INFO, LIMITS, SET_LIMIT, SET_PRIMARY, BALANCE, HISTORY, CHECK_WITHDRAW, WITHDRAW, DEPOSIT, OPEN_DEPOSIT -> {
                        if (session.bankToken == null) atm.lcd().actionResult(false, "", "Please reinsert your card and sign in.");
                        else {
                            session.menuRequest = UUID.randomUUID();
                            session.menuStarted = System.nanoTime();
                            PacketDistributor.sendToServer(new AtmActionRequestPayload(session.bankToken, session.menuRequest,
                                    ++session.menuSequence, atm.lcd().pendingAction().name(), atm.lcd().amount(), atm.lcd().requestOption(), atm.lcd().payTarget(), atm.lcd().limitDurationMinutes(),
                                    atm.lcd().pendingAction() == net.austizz.ultimatebankingsystem.atm.AtmLcdState.Action.SET_LIMIT ? atm.lcd().takePin() : ""));
                        }
                    }
                    default -> { }
                }
            }
            return true;
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (session == null || session.returning || session.depositTray.pending || session.depositTray.count() == 0) return true;
            if (!(session.level.getBlockEntity(session.atm) instanceof AtmBlockEntity atm) || atm.phase() != Phase.DEPOSIT_FEEDING) return true;
            if (AtmDepositTray.hit(mouseX, mouseY, width, height) >= 0) return true;
            if (session.menuRequest == null && session.bankToken != null && session.depositTray.submit(buttonAt(mouseX,mouseY) == AtmControls.CASH)) {
                session.menuRequest = UUID.randomUUID();session.menuStarted = System.nanoTime();
                PacketDistributor.sendToServer(new net.austizz.ultimatebankingsystem.network.AtmDepositFeedPayload(session.bankToken, session.menuRequest,
                        ++session.menuSequence, java.util.Arrays.stream(session.depositTray.dragging).boxed().toList()));
            } else { session.depositTray.clear(); session.depositTray.message = "Drop bills on the open feeder."; }
            return true;
        }
        private int buttonAt(double mouseX, double mouseY) {
            if (session == null || session.returning || System.nanoTime() - session.started < TRANSITION_NANOS
                    || session.cardMotion.busy()
                    || !(session.level.getBlockEntity(session.atm) instanceof AtmBlockEntity)) return -1;
            double x = Math.clamp(mouseX, 8, Math.max(8, width - 8));
            double y = Math.clamp(mouseY, 8, Math.max(8, height - 8));
            var view = minecraft.gameRenderer.getMainCamera();
            var clip = new Vector4f((float) (x / width * 2 - 1), (float) (1 - y / height * 2), -1, 1);
            clip.mul(new Matrix4f(minecraft.gameRenderer.getProjectionMatrix(renderedFov)).invert());
            var direction = new Vector3f(clip.x, clip.y, clip.z).normalize().rotate(view.rotation());
            Vec3 start = view.getPosition();
            Vec3 end = start.add(direction.x * 4, direction.y * 4, direction.z * 4);
            int hit = AtmControls.pick(start, end, session.atm, session.facing);
            var atm = (AtmBlockEntity) session.level.getBlockEntity(session.atm);
            if (hit == AtmControls.CASH && atm.phase() != Phase.CASH_READY && atm.phase() != Phase.DEPOSIT_FEEDING && atm.phase() != Phase.DEPOSIT_RETURN_READY) return -1;
            if (hit == AtmControls.RECEIPT && atm.phase() != Phase.RECEIPT_READY) return -1;
            if (hit == AtmControls.READER && atm.dispensing() && (atm.phase() != Phase.TAKE_CARD || session.cardMotion.phase != AtmCardMotion.Phase.INSERTED)) return -1;
            return hit;
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (session == null || session.returning
                    || session.cardMotion.phase == AtmCardMotion.Phase.INSERTING
                    || session.cardMotion.phase == AtmCardMotion.Phase.EJECTING
                || session.cardMotion.phase == AtmCardMotion.Phase.REMOVING
                    || System.nanoTime() - session.started < TRANSITION_NANOS) return;
            var atm = session.level.getBlockEntity(session.atm) instanceof AtmBlockEntity terminal ? terminal : null;
            boolean tray = atm != null && atm.deposit().feedingScreen();
            double seconds = (System.nanoTime() - session.trayFrame) / 1_000_000_000.0; session.trayFrame = System.nanoTime();
            session.depositTray.fade(tray, seconds);
            AtmDepositTrayRenderer.render(graphics, session.depositTray, width, height, mouseX, mouseY,
                    atm != null && atm.phase() == Phase.DEPOSIT_FEEDING && !session.depositTray.pending && session.menuRequest == null,
                    atm != null && atm.phase() == Phase.DEPOSIT_FEEDING && buttonAt(mouseX,mouseY) == AtmControls.CASH);
            int x = Math.clamp(mouseX, 8, Math.max(8, width - 8));
            int y = Math.clamp(mouseY, 8, Math.max(8, height - 8));
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 400);
            var vertices = buttonAt(x, y) >= 0 ? AtmCursorShape.HOVER : AtmCursorShape.IDLE;
            var buffer = graphics.bufferSource().getBuffer(RenderType.gui());
            for (var vertex : vertices) {
                buffer.addVertex(graphics.pose().last().pose(), vertex.x(), vertex.y(), 0)
                        .setColor(255, 255, 255, vertex.alpha());
            }
            graphics.flush();
            graphics.pose().popPose();
        }

        @Override
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }

        @Override
        public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                if (session != null && session.depositTray.count() > 0 && !session.depositTray.pending) session.depositTray.clear();
                else returnToPlayer();
            }
            return true;
        }

        @Override
        public void onClose() {
            returnToPlayer();
        }

        @Override
        public void removed() {
            stop(Minecraft.getInstance());
        }
    }
}
