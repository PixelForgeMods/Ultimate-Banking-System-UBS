package net.austizz.ultimatebankingsystem.client;

final class AtmCameraMotion {
    record Pose(double x, double y, double z, float yaw, float pitch) {
    }

    private AtmCameraMotion() {
    }

    static Pose screen(int x, int y, int z, int facingX, int facingZ) {
        if (Math.abs(facingX) + Math.abs(facingZ) != 1) {
            throw new IllegalArgumentException("ATM camera requires a horizontal facing");
        }
        double distance = .5 - 1.955 / 16 + .30;
        return new Pose(x + .5 + facingX * distance, y + 24.22 / 16,
                z + .5 + facingZ * distance,
                (float) Math.toDegrees(Math.atan2(facingX, -facingZ)), 0);
    }

    static boolean feederCamera(net.austizz.ultimatebankingsystem.atm.AtmDispenseMotion.Phase phase) {
        return switch (phase) {
            case DEPOSIT_OPENING, DEPOSIT_FEEDING, DEPOSIT_INSERTING, DEPOSIT_RETURN_OPENING,
                    DEPOSIT_RETURNING, DEPOSIT_RETURN_READY, DEPOSIT_PICKUP -> true;
            default -> false;
        };
    }
    static Pose deposit(Pose docked, int facingX, int facingZ) {
        return new Pose(docked.x + facingX * .30, docked.y, docked.z + facingZ * .30, docked.yaw, 10);
    }
    static Pose lookAt(Pose eye, double x, double y, double z) {
        double dx=x-eye.x, dy=y-eye.y, dz=z-eye.z;
        return new Pose(eye.x,eye.y,eye.z,(float)Math.toDegrees(Math.atan2(-dx,dz)),(float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));
    }
    static Pose interpolate(Pose from, Pose to, double progress) {
        double t = Math.clamp(progress, 0, 1);
        double eased = t * t * t * (t * (t * 6 - 15) + 10);
        float deltaYaw = (to.yaw - from.yaw) % 360;
        if (deltaYaw > 180) deltaYaw -= 360;
        if (deltaYaw < -180) deltaYaw += 360;
        return new Pose(from.x + (to.x - from.x) * eased,
                from.y + (to.y - from.y) * eased,
                from.z + (to.z - from.z) * eased,
                from.yaw + deltaYaw * (float) eased,
                from.pitch + (to.pitch - from.pitch) * (float) eased);
    }

    static double followCursor(double current, double target, double seconds) {
        if (!Double.isFinite(current)) current = 0;
        if (!Double.isFinite(target)) target = current;
        if (!Double.isFinite(seconds)) seconds = 0;
        double weight = 1 - Math.exp(-10 * Math.clamp(seconds, 0, .25));
        return current + (Math.clamp(target, -1, 1) - current) * weight;
    }

    static double cursorAxis(double pixels, int windowSize) {
        if (windowSize <= 0 || !Double.isFinite(pixels)) return 0;
        return Math.clamp(pixels / windowSize * 2 - 1, -1, 1);
    }

    static double followVerticalCursor(double current, double target, double seconds) {
        return followCursor(current, target, Math.clamp(seconds, 0, .25) * .125);
    }

    static Pose cursorSway(Pose docked, double horizontal, double vertical) {
        return cursorSway(docked, horizontal, vertical, false);
    }

    static Pose cursorSway(Pose docked, double horizontal, double vertical, boolean inWall) {
        double y = Math.clamp(vertical, -1, 1);
        double down = Math.clamp((y - .10) / .60, 0, 1);
        double pitch = !inWall && y > 0 ? down * down * 30 : y * .85;
        return new Pose(docked.x, docked.y, docked.z,
                docked.yaw + (float) (Math.clamp(horizontal, -1, 1) * 1.25),
                docked.pitch + (float) pitch);
    }
}
