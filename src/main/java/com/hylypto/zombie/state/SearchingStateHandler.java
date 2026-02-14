package com.hylypto.zombie.state;

import com.hypixel.hytale.builtin.path.path.TransientPath;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hylypto.zombie.PatrolConfig;
import com.hylypto.zombie.PatrolGroup;
import com.hylypto.zombie.detection.PlayerDetector;
import com.hylypto.zombie.detection.PlayerFinder;

/**
 * Searching state — zombies move toward the last known player position.
 * Reassigns a TransientPath pointing at the last known position so the
 * BodyMotionPath instruction drives native engine navigation.
 */
public class SearchingStateHandler implements PatrolStateHandler {

    private static final System.Logger LOG = System.getLogger(SearchingStateHandler.class.getName());

    private final PatrolConfig config;

    public SearchingStateHandler(PatrolConfig config) {
        this.config = config;
    }

    @Override
    public PatrolState tick(PatrolGroup group, Store<EntityStore> store) {
        Vector3d centroid = calculateCentroid(group, store);
        if (centroid == null) return PatrolState.SEARCHING;

        // Check if any zombie re-detects a player
        Vector3d nearestPlayer = PlayerFinder.findNearest(store, centroid);
        if (nearestPlayer != null) {
            for (Ref<EntityStore> ref : group.getMemberRefs()) {
                if (!ref.isValid()) continue;
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform == null) continue;

                if (PlayerDetector.isWithinRange(transform.getPosition(), nearestPlayer, config.aggroRange)) {
                    LOG.log(System.Logger.Level.INFO,
                            "Patrol " + group.getGroupId() + " — re-detected player during search, AGGRO!");
                    group.setLastKnownPlayerPosition(nearestPlayer);
                    return PatrolState.AGGRO;
                }
            }
        }

        // Search timeout — resume patrol
        if (group.millisInCurrentState() >= config.searchDurationSeconds * 1000L) {
            LOG.log(System.Logger.Level.INFO,
                    "Patrol " + group.getGroupId() + " — search timeout, resuming patrol");
            return PatrolState.PATROLLING;
        }

        return PatrolState.SEARCHING;
    }

    @Override
    public void onEnter(PatrolGroup group, Store<EntityStore> store) {
        LOG.log(System.Logger.Level.INFO,
                "Patrol " + group.getGroupId() + " — searching around last known player position");

        // Reassign each NPC's TransientPath to point at the last known player position
        Vector3d lastKnown = group.getLastKnownPlayerPosition();
        if (lastKnown != null) {
            for (Ref<EntityStore> ref : group.getMemberRefs()) {
                if (!ref.isValid()) continue;
                try {
                    NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                    if (npc == null) continue;

                    TransientPath searchPath = new TransientPath();
                    searchPath.addWaypoint(lastKnown, new Vector3f(0, 0, 0));
                    npc.getPathManager().setTransientPath(searchPath);
                } catch (Exception e) {
                    LOG.log(System.Logger.Level.ERROR, "Failed to set search path: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void onExit(PatrolGroup group, Store<EntityStore> store) {}

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
