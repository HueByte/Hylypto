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

public class PatrollingStateHandler implements PatrolStateHandler {

    private static final System.Logger LOG = System.getLogger(PatrollingStateHandler.class.getName());

    private final PatrolConfig config;
    private int tickCount = 0;

    public PatrollingStateHandler(PatrolConfig config) {
        this.config = config;
    }

    @Override
    public PatrolState tick(PatrolGroup group, Store<EntityStore> store) {
        tickCount++;

        Vector3d centroid = calculateCentroid(group, store);
        if (centroid == null) {
            LOG.log(System.Logger.Level.WARNING, "[PATROL-TICK] group=" + group.getGroupId()
                    + " — centroid is null (no valid refs?), members=" + group.size());
            return PatrolState.PATROLLING;
        }

        // Advance waypoints for our tracking (doesn't affect the engine's BodyMotionPath)
        Vector3d waypoint = group.getCurrentWaypoint();
        if (waypoint != null) {
            double distToWp = centroid.distanceTo(waypoint);

            if (tickCount % 5 == 0) {
                LOG.log(System.Logger.Level.INFO, "[PATROL-TICK] group=" + group.getGroupId()
                        + " state=PATROLLING tick=" + tickCount
                        + " members=" + group.size()
                        + " wpIdx=" + group.getCurrentWaypointIndex()
                        + " centroid=(" + (int) centroid.x + "," + (int) centroid.y + "," + (int) centroid.z + ")"
                        + " waypoint=(" + (int) waypoint.x + "," + (int) waypoint.y + "," + (int) waypoint.z + ")"
                        + " dist=" + String.format("%.1f", distToWp));
            }

            if (distToWp <= config.waypointArrivalRadius) {
                if (group.advanceWaypoint()) {
                    waypoint = group.getCurrentWaypoint();
                    LOG.log(System.Logger.Level.INFO, "[PATROL-TICK] group=" + group.getGroupId()
                            + " — advancing to waypoint " + group.getCurrentWaypointIndex()
                            + " at (" + (int) waypoint.x + "," + (int) waypoint.y + "," + (int) waypoint.z + ")");
                    reassignPatrolPath(group, store);
                }
                // If no more waypoints, just keep walking — engine Wander fallback takes over
            }
        }

        // Despawn when far enough from all players (patrol has passed through)
        Vector3d nearestPlayer = PlayerFinder.findNearest(store, centroid);
        if (nearestPlayer != null) {
            double distToPlayer = centroid.distanceTo(nearestPlayer);

            if (distToPlayer >= config.despawnDistanceFromPlayer) {
                // Only despawn if we've been patrolling for a while (past the player area)
                if (group.getCurrentWaypointIndex() >= 1) {
                    LOG.log(System.Logger.Level.INFO, "[PATROL-TICK] group=" + group.getGroupId()
                            + " — " + String.format("%.0f", distToPlayer) + " blocks from player, DESPAWNING");
                    return PatrolState.DESPAWNING;
                }
            }

            // Check for player detection → AGGRO
            for (Ref<EntityStore> ref : group.getMemberRefs()) {
                if (!ref.isValid()) continue;
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform == null) continue;

                Vector3d zombiePos = transform.getPosition();
                double yaw = waypoint != null
                        ? Math.toDegrees(Math.atan2(waypoint.z - zombiePos.z, waypoint.x - zombiePos.x))
                        : 0;

                if (PlayerDetector.canDetect(zombiePos, yaw, nearestPlayer,
                        config.detectionRange, config.fovDegrees, config.proximityAlwaysDetect)) {
                    LOG.log(System.Logger.Level.INFO, "[PATROL-TICK] group=" + group.getGroupId()
                            + " — player detected at ("
                            + (int) nearestPlayer.x + "," + (int) nearestPlayer.y + "," + (int) nearestPlayer.z
                            + "), AGGRO!");
                    group.setLastKnownPlayerPosition(nearestPlayer);
                    return PatrolState.AGGRO;
                }
            }
        }

        return PatrolState.PATROLLING;
    }

    @Override
    public void onEnter(PatrolGroup group, Store<EntityStore> store) {
        LOG.log(System.Logger.Level.INFO, "[PATROL-ENTER] group=" + group.getGroupId()
                + " — entering PATROLLING state, members=" + group.size()
                + " wpIdx=" + group.getCurrentWaypointIndex());
        tickCount = 0;
        reassignPatrolPath(group, store);
    }

    @Override
    public void onExit(PatrolGroup group, Store<EntityStore> store) {}

    private void reassignPatrolPath(PatrolGroup group, Store<EntityStore> store) {
        var waypoints = group.getWaypoints();
        int startIdx = group.getCurrentWaypointIndex();
        if (startIdx >= waypoints.size()) return;

        TransientPath path = new TransientPath();
        for (int i = startIdx; i < waypoints.size(); i++) {
            path.addWaypoint(waypoints.get(i), new Vector3f(0, 0, 0));
        }

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
                LOG.log(System.Logger.Level.ERROR, "[PATROL-PATH] Failed to reassign path: " + e.getMessage());
            }
        }
        LOG.log(System.Logger.Level.INFO, "[PATROL-PATH] Reassigned TransientPath to " + assigned
                + " NPCs, waypoints=" + (waypoints.size() - startIdx));
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
