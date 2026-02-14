package com.hylypto.zombie.state;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hylypto.zombie.PatrolConfig;
import com.hylypto.zombie.PatrolGroup;
import com.hylypto.zombie.detection.PlayerFinder;

public class DespawningStateHandler implements PatrolStateHandler {

    private static final System.Logger LOG = System.getLogger(DespawningStateHandler.class.getName());
    private static final long DESPAWN_TIMEOUT_MS = 15_000;

    private final PatrolConfig config;

    public DespawningStateHandler(PatrolConfig config) {
        this.config = config;
    }

    @Override
    public PatrolState tick(PatrolGroup group, Store<EntityStore> store) {
        // Timeout fallback — if we've been in DESPAWNING for too long, force remove
        if (group.millisInCurrentState() >= DESPAWN_TIMEOUT_MS) {
            LOG.log(System.Logger.Level.INFO,
                    "Patrol " + group.getGroupId() + " — despawn timeout, force removing");
            removeAllMembers(group, store);
            return PatrolState.DESPAWNING;
        }

        Vector3d centroid = calculateCentroid(group, store);
        if (centroid == null) {
            // No valid members left — already cleaned up
            return PatrolState.DESPAWNING;
        }

        Vector3d nearestPlayer = PlayerFinder.findNearest(store, centroid);

        // If no players online, remove immediately
        if (nearestPlayer == null) {
            removeAllMembers(group, store);
            return PatrolState.DESPAWNING;
        }

        // Check if group centroid is far enough from player to despawn
        if (centroid.distanceTo(nearestPlayer) >= config.despawnDistanceFromPlayer) {
            LOG.log(System.Logger.Level.INFO,
                    "Patrol " + group.getGroupId() + " — far enough from players, removing entities");
            removeAllMembers(group, store);
        }

        return PatrolState.DESPAWNING;
    }

    @Override
    public void onEnter(PatrolGroup group, Store<EntityStore> store) {
        LOG.log(System.Logger.Level.INFO,
                "Patrol " + group.getGroupId() + " — entering DESPAWNING state (" + group.size() + " members)");
    }

    @Override
    public void onExit(PatrolGroup group, Store<EntityStore> store) {}

    private void removeAllMembers(PatrolGroup group, Store<EntityStore> store) {
        int removed = 0;
        for (Ref<EntityStore> ref : group.getMemberRefs()) {
            try {
                if (ref.isValid()) {
                    store.removeEntity(ref, RemoveReason.REMOVE);
                    removed++;
                }
            } catch (Exception e) {
                // Entity may have already been removed
            }
        }
        group.getMemberRefs().clear();
        // DON'T clear memberUUIDs here — PatrolManager.cleanupGroup() needs them
        // to remove entries from the zombieToGroup map
        LOG.log(System.Logger.Level.INFO,
                "Patrol " + group.getGroupId() + " — removed " + removed + " entities");
    }

    private Vector3d calculateCentroid(PatrolGroup group, Store<EntityStore> store) {
        double sumX = 0, sumY = 0, sumZ = 0;
        int count = 0;

        for (Ref<EntityStore> ref : group.getMemberRefs()) {
            if (!ref.isValid()) continue;
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) continue;
            Vector3d pos = transform.getPosition();
            sumX += pos.x;
            sumY += pos.y;
            sumZ += pos.z;
            count++;
        }

        if (count == 0) return null;
        return new Vector3d(sumX / count, sumY / count, sumZ / count);
    }
}
