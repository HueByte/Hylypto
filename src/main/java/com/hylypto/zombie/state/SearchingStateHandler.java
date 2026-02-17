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

public class SearchingStateHandler implements PatrolStateHandler {

    private static final System.Logger LOG = System.getLogger(SearchingStateHandler.class.getName());

    private final PatrolConfig config;
    private int tickCount = 0;

    public SearchingStateHandler(PatrolConfig config) {
        this.config = config;
    }

    @Override
    public PatrolState tick(PatrolGroup group, Store<EntityStore> store) {
        tickCount++;
        Vector3d centroid = calculateCentroid(group, store);
        if (centroid == null) {
            LOG.log(System.Logger.Level.WARNING, "[SEARCH-TICK] group=" + group.getGroupId()
                    + " — centroid null, members=" + group.size());
            return PatrolState.SEARCHING;
        }

        // Check if any zombie re-detects a player
        Vector3d nearestPlayer = PlayerFinder.findNearest(store, centroid);
        if (nearestPlayer != null) {
            for (Ref<EntityStore> ref : group.getMemberRefs()) {
                if (!ref.isValid()) continue;
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform == null) continue;

                if (PlayerDetector.isWithinRange(transform.getPosition(), nearestPlayer, config.aggroRange)) {
                    LOG.log(System.Logger.Level.INFO, "[SEARCH-TICK] group=" + group.getGroupId()
                            + " — re-detected player during search, AGGRO!");
                    group.setLastKnownPlayerPosition(nearestPlayer);
                    return PatrolState.AGGRO;
                }
            }
        }

        long elapsed = group.millisInCurrentState();
        if (tickCount % 5 == 0) {
            LOG.log(System.Logger.Level.INFO, "[SEARCH-TICK] group=" + group.getGroupId()
                    + " tick=" + tickCount + " elapsed=" + elapsed + "ms"
                    + " timeout=" + (config.searchDurationSeconds * 1000L) + "ms");
        }

        // Search timeout — resume patrol
        if (elapsed >= config.searchDurationSeconds * 1000L) {
            LOG.log(System.Logger.Level.INFO, "[SEARCH-TICK] group=" + group.getGroupId()
                    + " — search timeout, resuming PATROLLING");
            return PatrolState.PATROLLING;
        }

        return PatrolState.SEARCHING;
    }

    @Override
    public void onEnter(PatrolGroup group, Store<EntityStore> store) {
        LOG.log(System.Logger.Level.INFO, "[SEARCH-ENTER] group=" + group.getGroupId()
                + " — searching around last known player position, members=" + group.size());
        tickCount = 0;

        // Assign a TransientPath toward last known player position
        // The engine's BodyMotionPath instruction handles movement natively
        Vector3d lastKnown = group.getLastKnownPlayerPosition();
        if (lastKnown != null) {
            assignSearchPath(group, store, lastKnown);
        }
    }

    @Override
    public void onExit(PatrolGroup group, Store<EntityStore> store) {}

    /**
     * Assigns a TransientPath to last known player position for all group members.
     * The engine's BodyMotionPath handles the actual movement.
     */
    private void assignSearchPath(PatrolGroup group, Store<EntityStore> store, Vector3d target) {
        TransientPath path = new TransientPath();
        path.addWaypoint(target, new Vector3f(0, 0, 0));

        int assigned = 0;
        for (Ref<EntityStore> ref : group.getMemberRefs()) {
            if (!ref.isValid()) continue;
            try {
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                if (npc != null) {
                    npc.getPathManager().setTransientPath(path);
                    assigned++;
                }
            } catch (Exception e) {
                LOG.log(System.Logger.Level.ERROR, "[SEARCH-PATH] Failed to assign search path: " + e.getMessage());
            }
        }
        LOG.log(System.Logger.Level.INFO, "[SEARCH-PATH] Assigned search path to " + assigned
                + " NPCs toward (" + (int) target.x + "," + (int) target.y + "," + (int) target.z + ")");
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
