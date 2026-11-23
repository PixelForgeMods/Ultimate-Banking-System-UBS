package net.austizz.ultimatebankingsystem.api.pc;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** A notification sent by an in-game PC app. Use the same nonempty key to update a running operation. */
public record PcAppNotification(String key, String title, String body, int progress, UUID image,
                                Delivery delivery, List<Action> actions, Reply reply) {
    public static final int NO_PROGRESS = -1;
    public static final int INDETERMINATE = -2;

    public enum Delivery { NORMAL, REMINDER, ALARM, CALL }
    public record Action(String title, Runnable run) {}
    public record Reply(String hint, Consumer<String> submit) {}

    public PcAppNotification {
        key = limit(key, 80);
        title = limit(title, 120);
        body = limit(body, 1000);
        if (title.isBlank()) throw new IllegalArgumentException("A notification needs a title.");
        progress = Math.clamp(progress, INDETERMINATE, 100);
        delivery = java.util.Objects.requireNonNull(delivery);
        actions = List.copyOf(actions).stream().limit(2).toList();
    }

    public PcAppNotification(String title, String body) {
        this("", title, body, NO_PROGRESS, null, Delivery.NORMAL, List.of(), null);
    }

    private static String limit(String value, int max) {
        return value.substring(0, Math.min(value.length(), max));
    }
}
