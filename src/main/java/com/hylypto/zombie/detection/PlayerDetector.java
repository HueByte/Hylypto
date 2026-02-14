package com.hylypto.zombie.detection;

import com.hypixel.hytale.math.vector.Vector3d;

public final class PlayerDetector {

    private PlayerDetector() {}

    /**
     * Check if a zombie can detect a player using distance + forward cone.
     * Always detects within proximity range (hearing/smell).
     */
    public static boolean canDetect(Vector3d zombiePos, double zombieYawDegrees,
                                     Vector3d playerPos, double detectionRange,
                                     double fovDegrees, double proximityRange) {
        double distance = zombiePos.distanceTo(playerPos);

        // Always detect very close players (hearing/smell)
        if (distance <= proximityRange) {
            return true;
        }

        // Too far — can't detect
        if (distance > detectionRange) {
            return false;
        }

        // Check if player is within forward facing cone
        double dx = playerPos.x - zombiePos.x;
        double dz = playerPos.z - zombiePos.z;
        double angleToPlayer = Math.toDegrees(Math.atan2(dz, dx));

        // Normalize yaw to same range as atan2 result
        double facingAngle = zombieYawDegrees % 360;
        double diff = Math.abs(angleToPlayer - facingAngle);
        if (diff > 180) diff = 360 - diff;

        return diff <= fovDegrees / 2.0;
    }

    public static boolean isWithinRange(Vector3d a, Vector3d b, double range) {
        return a.distanceTo(b) <= range;
    }
}
