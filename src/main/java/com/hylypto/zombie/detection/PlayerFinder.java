package com.hylypto.zombie.detection;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Shared utility for finding the nearest player position.
 * Replaces the duplicated findNearestPlayerPosition logic across state handlers.
 */
public final class PlayerFinder {

    private PlayerFinder() {}

    /**
     * Finds the position of the nearest player to a given reference point.
     * Uses forEachEntityParallel for performance.
     *
     * @param store the entity store
     * @param referencePos the position to measure distance from (e.g. group centroid)
     * @return nearest player position, or null if no players found
     */
    public static Vector3d findNearest(Store<EntityStore> store, Vector3d referencePos) {
        final Vector3d[] nearest = {null};
        final double[] minDist = {Double.MAX_VALUE};

        try {
            store.forEachEntityParallel(Player.getComponentType(), (index, chunk, buffer) -> {
                TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
                if (transform != null) {
                    Vector3d pos = transform.getPosition();
                    double dist = referencePos.distanceTo(pos);
                    synchronized (nearest) {
                        if (dist < minDist[0]) {
                            minDist[0] = dist;
                            nearest[0] = pos;
                        }
                    }
                }
            });
        } catch (Exception e) {
            // No players online
        }

        return nearest[0];
    }

    /**
     * Finds any player position (when no reference point matters).
     */
    public static Vector3d findAny(Store<EntityStore> store) {
        return findNearest(store, new Vector3d(0, 0, 0));
    }
}
