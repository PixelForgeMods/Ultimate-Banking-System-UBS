package net.austizz.ultimatebankingsystem.atm;

/** Timing of the supplied mono recordings, rounded to server ticks. */
public final class AtmSoundTiming {
    public static final int CARD_APPROACH = 8, CARD_MOTOR = 26, CARD_SLIDE = 12, CARD_PICKUP = 27;
    public static final int CARD_INSERT = CARD_APPROACH + 2 * CARD_MOTOR;
    public static final int CASH_PREPARE = 40, SHUTTER_OPEN = 25, CASH_FEED = 8, CASH_BILL_GAP = 2;
    public static final int CASH_BILL_PERIOD = CASH_FEED + CASH_BILL_GAP;
    public static int cashFeedTicks(int bills) { return Math.max(0, bills) * CASH_BILL_PERIOD; }
    public static int cashCycleTicks(int bills) { return CASH_PREPARE + SHUTTER_OPEN + cashFeedTicks(bills); }
    public static final int RECEIPT_TEAR = 9;
    private AtmSoundTiming() {}
    public static long nanos(int ticks) { return ticks * 50_000_000L; }
    public static double cardEjection(AtmDispenseMotion.Phase phase, double age) {
        return switch (phase) {
            case WAIT_CARD -> Math.clamp((age - (CARD_MOTOR - CARD_SLIDE)) / CARD_SLIDE, 0, 1);
            case TAKE_CARD -> 1;
            default -> 0;
        };
    }
}
