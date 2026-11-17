package net.austizz.ultimatebankingsystem.client;

import net.austizz.ultimatebankingsystem.atm.AtmSoundTiming;

final class AtmCardMotion {
    enum Phase { EMPTY, REQUESTING, INSERTING, INSERTED, EJECTING, REMOVING }
    record Sample(double cardX, double cardY, double cardZ,
                  double handX, double handY, double handZ, boolean visible, boolean handVisible) {}

    static final long INSERT_NANOS = AtmSoundTiming.nanos(AtmSoundTiming.CARD_INSERT);
    static final long EJECT_NANOS = AtmSoundTiming.nanos(AtmSoundTiming.CARD_MOTOR);
    static final long REMOVE_NANOS = AtmSoundTiming.nanos(AtmSoundTiming.CARD_PICKUP);
    Phase phase = Phase.EMPTY;
    long started;
    private double ejection;

    void presentCard(double progress) {
        if (phase == Phase.INSERTED) ejection = Math.clamp(progress, 0, 1);
    }
    boolean inReader(long now) {
        return phase == Phase.INSERTED || (phase == Phase.INSERTING || phase == Phase.EJECTING || phase == Phase.REMOVING) && sample(now).cardZ() > -.04;
    }

    boolean busy() {
        return phase == Phase.REQUESTING || phase == Phase.INSERTING || phase == Phase.EJECTING || phase == Phase.REMOVING;
    }

    boolean focusesCamera() {
        return phase == Phase.INSERTING || phase == Phase.REMOVING;
    }

    boolean request(long now) {
        if (phase != Phase.EMPTY) return false;
        phase = Phase.REQUESTING;
        started = now;
        return true;
    }

    boolean accept(long now) {
        if (phase != Phase.REQUESTING) return false;
        phase = Phase.INSERTING;
        started = now;
        ejection = 0;
        return true;
    }

    void reset() {
        phase = Phase.EMPTY;
        ejection = 0;
    }

    boolean remove(long now) {
        if (phase != Phase.INSERTED) return false;
        phase = ejection >= 1 ? Phase.REMOVING : Phase.EJECTING;
        started = now;
        return true;
    }

    boolean advance(long now) {
        if (phase == Phase.EJECTING && now - started >= EJECT_NANOS) {
            phase = Phase.REMOVING; started = now; ejection = 1;
            return true;
        }
        if (phase == Phase.INSERTING && now - started >= INSERT_NANOS) {
            phase = Phase.INSERTED;
            return true;
        }
        if (phase == Phase.REMOVING && now - started >= REMOVE_NANOS) {
            phase = Phase.EMPTY;
            return true;
        }
        return false;
    }

    Sample sample(long now) {
        if (phase == Phase.EJECTING) {
            double ticks = (now - started) / 50_000_000.0;
            double feed = smooth((ticks - (AtmSoundTiming.CARD_MOTOR - AtmSoundTiming.CARD_SLIDE)) / AtmSoundTiming.CARD_SLIDE);
            double extended = smooth(ejection) + (1 - smooth(ejection)) * feed;
            return new Sample(0, 0, .0628 - .0478 * extended, 0, 0, 0, true, false);
        }
        if (phase == Phase.INSERTED && ejection > 0) return new Sample(0, 0, .0628 - .0478 * smooth(ejection), 0, 0, 0, true, false);
        if (phase != Phase.INSERTING && phase != Phase.REMOVING) return new Sample(0, 0, 0, 0, 0, 0, false, false);
        double t = Math.clamp((now - started) / (double) (phase == Phase.INSERTING ? INSERT_NANOS : REMOVE_NANOS), 0, 1);
        double x, y, z, hx, hy, hz;
        if (phase == Phase.INSERTING) {
            double ticks = (now - started) / 50_000_000.0;
            double approach = smooth(ticks / AtmSoundTiming.CARD_APPROACH);
            x = -.12 * (1 - approach);
            y = -.18 * (1 - approach);
            z = -.20 + .1372 * approach + .1256 * smooth((ticks - AtmSoundTiming.CARD_APPROACH) / AtmSoundTiming.CARD_MOTOR);
            double withdraw = smooth((ticks - AtmSoundTiming.CARD_APPROACH - AtmSoundTiming.CARD_MOTOR) / 8);
            hx = x - .14 * withdraw;
            hy = y - .055 - .20 * withdraw;
            hz = Math.min(z - .03, -.025) - .18 * withdraw;
        } else {
            double reach = smooth(t / .30), pull = smooth((t - .30) / .38), leave = smooth((t - .68) / .32);
            x = -.14 * leave;
            y = -.22 * leave;
            double initialZ = .0628 - .0478 * smooth(ejection);
            z = initialZ + (.015 - initialZ) * reach - .08 * pull - .135 * leave;
            hx = -.14 * (1 - reach) + x;
            hy = -.20 * (1 - reach) + y - .055;
            hz = -.18 * (1 - reach) + Math.min(z - .03, -.025);
        }
        return new Sample(x, y, z, hx, hy, hz, true, true);
    }

    private static double smooth(double t) {
        t = Math.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
