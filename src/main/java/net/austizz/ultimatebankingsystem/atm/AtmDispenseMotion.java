package net.austizz.ultimatebankingsystem.atm;

public final class AtmDispenseMotion {
    public enum Phase { IDLE, WAIT_CARD, TAKE_CARD, CARD_REMOVING, WAIT_CASH, OPENING, DISPENSING, CASH_READY,
        PICKING_CASH, CLOSING, RECEIPT_CHOICE, PRINTING, RECEIPT_READY, PICKING_RECEIPT, DEPOSIT_OPENING, DEPOSIT_FEEDING, DEPOSIT_INSERTING, DEPOSIT_COUNTING, DEPOSIT_REVIEW,
        DEPOSIT_RETURN_OPENING, DEPOSIT_RETURNING, DEPOSIT_RETURN_READY, DEPOSIT_PICKUP, DEPOSIT_CLOSING }
    private AtmDispenseMotion() {}
    public static final double RECEIPT_TILT = 44, RECEIPT_LENGTH = .12;
    public static boolean receiptTimedOut(double age) { return age >= 200; }
    public static double readerBrightness(Phase phase, double age) {
        return phase == Phase.TAKE_CARD ? .775 + .225 * Math.cos(age * Math.PI * 2 / 48) : 1;
    }
    public record Led(float red, float green, float blue) {}
    private static final Led READER_GREEN = new Led(85 / 255F, 1, 119 / 255F);
    public static Led readerLight(Phase phase, double age) {
        float brightness = (float) readerBrightness(phase, age);
        return new Led(READER_GREEN.red * brightness, READER_GREEN.green * brightness, READER_GREEN.blue * brightness);
    }
    public static Led guideLight(boolean cardInside) { return cardInside ? READER_GREEN : new Led(.08F, .26F, .12F); }
    public record Pickup(double reach, double x, double y, double z, double pitch, double roll) {}
    public static Pickup pickup(double age, boolean receipt) {
        double reach = ease(age / 10), lift = ease((age - 10) / 8), stow = ease((age - 19) / 7);
        return new Pickup(reach, .05 * lift + .13 * stow,
                (receipt ? .10 : .36) * lift - .60 * stow,
                (receipt ? -.10 : .12) * lift - .55 * stow,
                (receipt ? -10 : -65) * lift + 20 * stow, -8 * lift + 8 * stow);
    }
    public static double ease(double value) { double t = Math.clamp(value, 0, 1); return t * t * t * (t * (t * 6 - 15) + 10); }
    public static double shutter(Phase phase, double age) {
        return switch (phase) {
            case OPENING, DEPOSIT_OPENING, DEPOSIT_RETURN_OPENING -> ease(age / AtmSoundTiming.SHUTTER_OPEN);
            case DISPENSING, CASH_READY, PICKING_CASH, DEPOSIT_FEEDING, DEPOSIT_INSERTING, DEPOSIT_RETURNING, DEPOSIT_RETURN_READY, DEPOSIT_PICKUP -> 1;
            case CLOSING, DEPOSIT_COUNTING, DEPOSIT_CLOSING -> 1 - ease(age / AtmSoundTiming.SHUTTER_OPEN);
            default -> 0;
        };
    }
    public static int billCount(int[] plan) {
        int count = 0;
        for (int i = 0; i < Math.min(7, plan.length); i++) count += plan[i];
        return count;
    }
    public static int billsStarted(Phase phase, double age, int count) {
        return switch (phase) {
            case DISPENSING -> Math.min(count, Math.max(0, (int) (age / AtmSoundTiming.CASH_BILL_PERIOD) + 1));
            case CASH_READY, PICKING_CASH -> count;
            default -> 0;
        };
    }
    public static double cash(Phase phase, double age, int bill) {
        return switch (phase) { case DISPENSING -> ease((age - bill * AtmSoundTiming.CASH_BILL_PERIOD) / AtmSoundTiming.CASH_FEED); case CASH_READY, PICKING_CASH -> 1; default -> 0; };
    }
    public static double cashDepth(Phase phase, double age, int bill) {
        // The bill center stops at the slot plane, leaving its rear half inside the ATM.
        return .17 * (1 - cash(phase, age, bill));
    }
    public static double receipt(Phase phase, double age) {
        return switch (phase) { case PRINTING -> ease((age - 10) / 30); case RECEIPT_READY, PICKING_RECEIPT -> 1; default -> 0; };
    }
}
