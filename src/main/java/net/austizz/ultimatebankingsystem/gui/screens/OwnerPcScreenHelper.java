package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.i18n.UbsTranslations;
import net.austizz.ultimatebankingsystem.api.pc.PcAppNotification;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import java.time.ZoneId;
import java.util.List;
import net.austizz.ultimatebankingsystem.network.PcLoginPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.GlobalPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "ultimatebankingsystem", value = Dist.CLIENT)
public class OwnerPcScreenHelper {
    private static final Map<GlobalPos, PcDesktopWindows<PcDesktopScreen.App>> DESKTOPS = new HashMap<>();

    public static void openDesktop(GlobalPos position) {
        Minecraft.getInstance().setScreen(new PcLoginScreen(position));
    }

    static void openSignedInDesktop(GlobalPos position) {
        var minecraft = Minecraft.getInstance();
        var windows = DESKTOPS.computeIfAbsent(position, pc -> new PcDesktopWindows<>());
        minecraft.setScreen(new PcDesktopScreen(position, windows));
    }

    static PcNotifications notifications(GlobalPos position, PcNotifications fromDisk) {
        var session = DESKTOPS.get(position);
        return session == null ? fromDisk : session.notifications;
    }

    @SubscribeEvent
    static void tickClock(ClientTickEvent.Post event) {
        if (DESKTOPS.isEmpty()) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        long now = System.currentTimeMillis();
        var zone = ZoneId.systemDefault();
        for (var pc : DESKTOPS.entrySet()) {
            var position = pc.getKey();
            var windows = pc.getValue();
            if (windows.clock.tick(now, zone, windows.notifications, alert -> deliverClock(minecraft, position, windows, alert, now))) {
                PcDesktopPreferences.save(PcDesktopPreferences.path(minecraft, position), windows);
            }
        }
    }

    private static void deliverClock(Minecraft minecraft, GlobalPos position, PcDesktopWindows<PcDesktopScreen.App> windows,
                                     PcClock.Alert alert, long now) {
        String title = alert.title();
        String body = switch (alert.kind()) {
            case TIMER -> "Your countdown has finished.";
            case ALARM -> "Your alarm is ringing.";
            case FOCUS -> "Your focus session has ended.";
        };
        var actions = alert.alarm() == null ? List.<PcAppNotification.Action>of() : List.of(
                new PcAppNotification.Action("Snooze 10 min", () -> {
                    if (!windows.clock.alarms.contains(alert.alarm())) return;
                    alert.alarm().snooze = System.currentTimeMillis() + 600_000L;
                    PcDesktopPreferences.save(PcDesktopPreferences.path(minecraft, position), windows);
                }),
                new PcAppNotification.Action("Dismiss", () -> {}));
        var payload = new PcAppNotification("", title, body, -1, null,
                alert.kind() == PcClock.Kind.FOCUS ? PcAppNotification.Delivery.REMINDER : PcAppNotification.Delivery.ALARM,
                actions, null);
        if (minecraft.screen instanceof PcDesktopScreen desktop && desktop.isDesktop(position, windows)) {
            desktop.postClockNotification(payload);
        } else {
            var app = PcDesktopScreen.App.CLOCK;
            boolean ring = windows.notifications.post(app.id().toString(), app.title().getString(), payload,
                    "app:" + app.id(), now, true);
            windows.notifications.clearBanners();
            if (ring && minecraft.screen instanceof PcLoginScreen login && login.isFor(position)
                    && windows.notifications.lockScreen) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.15F, 0.4F));
            }
        }
    }

    public static void acceptLogin(PcLoginPayload.Response response) {
        if (Minecraft.getInstance().screen instanceof PcLoginScreen login) {
            login.accept(response);
        }
    }

    public static void clearDesktopSessions() {
        DESKTOPS.values().forEach(PcDesktopWindows::closeAll);
        DESKTOPS.clear();
    }

    public static void openOwnerPcScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        BankOwnerPcScreen freshScreen = new BankOwnerPcScreen(UbsTranslations.literal("Bank Owner PC"));
        minecraft.setScreen(freshScreen);
        freshScreen.relayoutForCurrentWindow();
    }

    public static void invalidateCachedScreen(BankOwnerPcScreen screen) {
        // No-op: screens are not globally cached anymore.
    }

    public static void invalidateCachedScreen() {
        // No-op: screens are not globally cached anymore.
    }
}
