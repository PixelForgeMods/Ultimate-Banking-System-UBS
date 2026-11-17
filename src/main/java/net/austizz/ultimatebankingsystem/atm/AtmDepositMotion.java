package net.austizz.ultimatebankingsystem.atm;

public final class AtmDepositMotion {
    public static final int INSERT_TICKS = 40, COUNT_TICKS = 65, RETURN_TICKS = 30;
    private AtmDepositMotion() {}
    public static double approach(double age) { return AtmDispenseMotion.ease(age / 10); }
    public static double feed(double age) { return AtmDispenseMotion.ease((age - 10) / 18); }
    public static double withdraw(double age) { return AtmDispenseMotion.ease((age - 28) / 12); }
    public static double depth(double age) { return -.22 * (1 - approach(age)) + .17 * feed(age); }
}
