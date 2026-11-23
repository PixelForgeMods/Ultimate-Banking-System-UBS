package net.austizz.ultimatebankingsystem.gui.screens;

import net.austizz.ultimatebankingsystem.api.pc.PcAppNotification;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PcNotificationsTest {
    @Test
    void sendsQuietNotificationsToHistoryAndAppliesDeliveryExceptions() {
        var model = new PcNotifications();
        model.priority("photos", PcNotifications.Priority.TOP);
        assertFalse(model.post("photos", "Photos", new PcAppNotification("Screenshot saved", ""), "", 1, true));
        assertNull(model.nextBanner(true));
        assertEquals("photos", model.groups().getFirst().app());

        assertTrue(model.post("clock", "Clock", alert(PcAppNotification.Delivery.REMINDER), "", 2, true));
        assertEquals("clock", model.nextBanner(true).app);
        model.allowCalls = false;
        assertFalse(model.post("phone", "Phone", alert(PcAppNotification.Delivery.CALL), "", 3, true));
        assertNull(model.nextBanner(true));
        model.lockCalls = false;
        assertEquals(2, model.lockEntries().size());

        model.app("photos").quietException = true;
        assertTrue(model.post("photos", "Photos", new PcAppNotification("Ready", ""), "", 4, true));
        assertEquals("photos", model.nextBanner(true).app);
        model.priority("phone", PcNotifications.Priority.TOP);
        assertEquals(PcNotifications.Priority.NORMAL, model.app("photos").priority);
        assertEquals("phone", model.groups().getFirst().app());
    }

    @Test
    void focusAndOvernightScheduleDoNotChangeManualMode() {
        var model = new PcNotifications();
        model.focusUntil = 100;
        assertTrue(model.quiet(50, 12, false));
        assertFalse(model.quiet(101, 12, false));
        model.manualQuiet = true;
        assertTrue(model.quiet(101, 12, false));
        model.manualQuiet = false;
        model.scheduled = true;
        assertTrue(model.quiet(101, 23, false));
        assertTrue(model.quiet(101, 2, false));
        assertFalse(model.quiet(101, 7, false));
        model.presentationQuiet = true;
        assertTrue(model.quiet(101, 12, true));
    }

    @Test
    void updatesProgressInPlaceAndRestoresHistoryAndPrivacySettings() {
        var model = new PcNotifications();
        var image = UUID.randomUUID();
        var first = new PcAppNotification("transfer", "Transferring", "Starting", 10, image,
                PcAppNotification.Delivery.NORMAL, List.of(), null);
        model.app("files").hideLockContent = true;
        model.app("files").badge = false;
        model.post("files", "Files", first, "file:" + image, 1, false);
        var banner = model.nextBanner(false);
        var finished = new PcAppNotification("transfer", "Transfer complete", "Ready", 100,
                image, PcAppNotification.Delivery.NORMAL, List.of(), null);
        assertFalse(model.post("files", "Files", finished, "file:" + image, 2, false));
        assertSame(banner, model.groups().getFirst().entries().getFirst());
        assertEquals(100, banner.payload.progress());
        assertEquals(0, model.unread("files"));
        assertEquals(1, model.unread(null));

        var properties = new Properties();
        model.store(properties);
        var restored = new PcNotifications();
        restored.load(properties);
        var entry = restored.groups().getFirst().entries().getFirst();
        assertEquals(finished, entry.payload);
        assertEquals("file:" + image, entry.target);
        assertTrue(restored.app("files").hideLockContent);
        assertNull(restored.nextBanner(false));
        assertFalse(restored.post("files", "Files", finished, "file:" + image, 3, false));
        assertEquals(1, restored.groups().getFirst().entries().size());
        restored.seen();
        assertTrue(restored.lockEntries().isEmpty());
        restored.clear("files");
        assertTrue(restored.groups().isEmpty());
    }

    @Test
    void capsHistoryAndKeepsBannerAndHistoryPreferencesIndependent() {
        var model = new PcNotifications();
        model.app("banner").history = false;
        model.app("history").banners = false;
        for (int i = 0; i <= PcNotifications.LIMIT; i++) {
            assertFalse(model.post("history", "History", new PcAppNotification("Event " + i, ""), "", i, false));
        }
        assertTrue(model.post("banner", "Banner", new PcAppNotification("Banner only", ""), "", 100, false));
        assertEquals("banner", model.nextBanner(false).app);
        var entries = model.groups().getFirst().entries();
        assertEquals(PcNotifications.LIMIT, entries.size());
        assertEquals("Event 1", entries.getLast().payload.title());
        assertEquals(1, model.groups().size());
    }

    private static PcAppNotification alert(PcAppNotification.Delivery reason) {
        return new PcAppNotification("", "Alert", "", -1, null, reason, List.of(), null);
    }
}
