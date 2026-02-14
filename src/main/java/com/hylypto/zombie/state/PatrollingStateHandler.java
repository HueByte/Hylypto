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

import java.util.List;

/**
 * Patrolling state — the BodyMotionPath instruction in the Hylypto_Patrol_Zombie role
 * handles all movement natively. The TransientPath was assigned at spawn time.
 * This handler only tracks waypoint progress (via centroid) and player detection.
 */
public class PatrollingStateHandler implements PatrolStateHandler {

    private static final System.Logger LOG = System.getLogger(PatrollingStateHandler.class.getName());

    private final PatrolConfig config;

    public PatrollingStateHandler(PatrolConfig config) {
        this.config = config;
    }

    @Override
    public PatrolState tick(PatrolGroup group, Store<EntityStore> store) {
        Vector3d waypoint = group.getCurrentWaypoint();
        if (waypoint == null) {
            LOG.log(System.Logger.Level.INFO, "Patrol " + group.getGroupId() + " — route complete, despawning");
            return PatrolState.DESPAWNING;
        }

        // Calculate group centroid from valid refs
        Vector3d centroid = calculateCentroid(group, store);
        if (centroid == null) return PatrolState.PATROLLING;

        // Check if centroid reached the current waypoint
        // (The engine BodyMotionPath drives the actual movement — we just track progress)
        if (centroid.distanceTo(waypoint) <= config.waypointArrivalRadius) {
            if (!group.advanceWaypoint()) {
                LOG.log(System.Logger.Level.INFO, "Patrol " + group.getGroupId() + " — reached final waypoint");
                return PatrolState.DESPAWNING;
            }
            waypoint = group.getCurrentWaypoint();
            LOG.log(System.Logger.Level.DEBUG,
                    "Patrol " + group.getGroupId() + " — advancing to waypoint " + group.getCurrentWaypointIndex());
        }

        // Check for player detection
        Vector3d nearestPlayer = PlayerFinder.findNearest(store, centroid);
        if (nearestPlayer != null) {
            for (Ref<EntityStore> ref : group.getMemberRefs()) {
                if (!ref.isValid()) continue;
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform == null) continue;

                Vector3d zombiePos = transform.getPosition();
                double yaw = Math.toDegrees(Math.atan2(
                        waypoint.z - zombiePos.z, waypoint.x - zombiePos.x));

                if (PlayerDetector.canDetect(zombiePos, yaw, nearestPlayer,
                        config.detectionRange, config.fovDegrees, config.proximityAlwaysDetect)) {
                    LOG.log(System.Logger.Level.INFO, "Patrol " + group.getGroupId() + " — player detected! AGGRO");
                    group.setLastKnownPlayerPosition(nearestPlayer);
                    return PatrolState.AGGRO;
                }
            }
        }

        return PatrolState.PATROLLING;
    }

    @Override
    public void onEnter(PatrolGroup group, Store<EntityStore> store) {
        LOG.log(System.Logger.Level.DEBUG, "Patrol " + group.getGroupId() + " — entering PATROLLING state");

        // Reassign the patrol path from the current waypoint onward
        // (needed when returning from SEARCHING/AGGRO back to PATROLLING)
        List<Vector3d> waypoints = group.getWaypoints();
        int startIndex = group.getCurrentWaypointIndex();
        if (startIndex < waypoints.size()) {
            for (Ref<EntityStore> ref : group.getMemberRefs()) {
                if (!ref.isValid()) continue;
                try {
                    NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                    if (npc == null) continue;

                    TransientPath patrolPath = new TransientPath();
                    for (int i = startIndex; i < waypoints.size(); i++) {
                        patrolPath.addWaypoint(waypoints.get(i), new Vector3f(0, 0, 0));
                    }
                    npc.getPathManager().setTransientPath(patrolPath);
                } catch (Exception e) {
                    LOG.log(System.Logger.Level.ERROR, "Failed to reassign patrol path: " + e.getMessage());
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
