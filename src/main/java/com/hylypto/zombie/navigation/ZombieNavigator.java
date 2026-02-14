package com.hylypto.zombie.navigation;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.movement.Steering;
import com.hypixel.hytale.server.npc.movement.controllers.MotionController;
import com.hypixel.hytale.server.npc.movement.controllers.ProbeMoveData;
import com.hypixel.hytale.server.npc.navigation.AStarBase;
import com.hypixel.hytale.server.npc.navigation.AStarNode;
import com.hypixel.hytale.server.npc.navigation.AStarNodePoolProviderSimple;
import com.hypixel.hytale.server.npc.navigation.AStarWithTarget;
import com.hypixel.hytale.server.npc.navigation.PathFollower;
import com.hypixel.hytale.server.npc.role.Role;

/**
 * Per-NPC navigation wrapper for patrol/search movement.
 *
 * Uses the same engine primitives as BodyMotionFind:
 * - AStarWithTarget for A* pathfinding
 * - PathFollower to walk the waypoint chain
 * - Writes into Role.bodySteering so the MotionController handles
 *   physics, acceleration, inertia, and animation naturally.
 *
 * Uses setTranslationRelativeSpeed() to match engine behavior —
 * the MotionController interprets relative speed (0.0-1.0) to
 * determine actual movement velocity based on NPC's walk/run config.
 *
 * For aggro/chasing, we do NOT use this — the engine's built-in
 * BodyMotionFind instruction handles it automatically.
 */
public class ZombieNavigator {

    private static final System.Logger LOG = System.getLogger(ZombieNavigator.class.getName());
    private static final int COMPUTE_ITERATIONS_PER_TICK = 200;
    private static final double ARRIVAL_THRESHOLD = 2.0;
    private static final double WAYPOINT_RADIUS = 1.5;

    private final AStarWithTarget pathfinder = new AStarWithTarget();
    private final PathFollower follower = new PathFollower();
    private final Steering steeringBuffer = new Steering();
    private final ProbeMoveData probeData = new ProbeMoveData();
    private final AStarNodePoolProviderSimple nodePoolProvider = new AStarNodePoolProviderSimple();

    private Vector3d currentTarget;
    private boolean pathReady = false;
    private boolean arrived = false;
    private double relativeSpeed = 0.8;

    public ZombieNavigator() {
        follower.setWaypointRadius(WAYPOINT_RADIUS);
        follower.setRelativeSpeed(1.0);
    }

    /**
     * Start computing a path from the NPC's current position to the target.
     * Must be called on the world thread.
     *
     * @param relSpeed relative speed (0.0-1.0), where 1.0 = max for this NPC
     */
    public void navigateTo(Vector3d target, double relSpeed, Ref<EntityStore> ref,
                            Store<EntityStore> store) {
        this.currentTarget = target;
        this.relativeSpeed = relSpeed;
        this.pathReady = false;
        this.arrived = false;

        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (npc == null || transform == null || npc.getRole() == null) return;

        Role role = npc.getRole();
        MotionController controller = role.getActiveMotionController();
        if (controller == null) return;

        Vector3d start = transform.getPosition();
        WalkingEvaluator evaluator = new WalkingEvaluator(target, ARRIVAL_THRESHOLD);

        pathfinder.clearPath();
        follower.clearPath();
        steeringBuffer.clear();

        AStarBase.Progress progress = pathfinder.initComputePath(
                ref, start, target, evaluator, controller, probeData, nodePoolProvider, store);

        if (progress == AStarBase.Progress.COMPUTING) {
            progress = pathfinder.computePath(ref, controller, probeData,
                    COMPUTE_ITERATIONS_PER_TICK, store);
        }

        if (progress == AStarBase.Progress.ACCOMPLISHED || progress == AStarBase.Progress.TERMINATED) {
            AStarNode path = pathfinder.getPath();
            if (path != null) {
                follower.setPath(path, start);
                pathReady = true;
                LOG.log(System.Logger.Level.DEBUG, "Path computed: " + path.getLength() + " waypoints");
            } else {
                pathReady = false;
                LOG.log(System.Logger.Level.DEBUG, "A* terminated with no path, falling back to direct");
            }
        } else {
            LOG.log(System.Logger.Level.DEBUG, "A* progress: " + progress);
        }
    }

    /**
     * Continue moving the NPC along the computed path.
     * Writes a normalized direction + relative speed into bodySteering.
     * The MotionController translates this into actual movement velocity,
     * physics, and animation — same as the engine's own motion components.
     * Returns true if the NPC has arrived at the target.
     */
    public boolean tick(Ref<EntityStore> ref, Store<EntityStore> store) {
        if (arrived) return true;
        if (currentTarget == null) return false;

        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (npc == null || transform == null || npc.getRole() == null) return false;

        Role role = npc.getRole();
        MotionController controller = role.getActiveMotionController();
        Vector3d pos = transform.getPosition();

        // Check arrival
        if (pos.distanceTo(currentTarget) <= ARRIVAL_THRESHOLD) {
            arrived = true;
            return true;
        }

        Steering bodySteering = role.getBodySteering();

        if (pathReady && controller != null) {
            // Use PathFollower to get the next waypoint direction
            steeringBuffer.clear();
            follower.executePath(pos, controller, steeringBuffer);

            if (steeringBuffer.hasTranslation()) {
                // PathFollower produced a direction — normalize and write to bodySteering
                Vector3d dir = steeringBuffer.getTranslation();
                double len = Math.sqrt(dir.x * dir.x + dir.y * dir.y + dir.z * dir.z);
                if (len > 0.01) {
                    // Set normalized direction
                    bodySteering.setTranslation(dir.x / len, dir.y / len, dir.z / len);
                    // Let the MotionController scale by its own max speed
                    bodySteering.setTranslationRelativeSpeed(relativeSpeed);
                    // Face movement direction
                    float yaw = (float) Math.toDegrees(Math.atan2(dir.x, dir.z));
                    bodySteering.setYaw(yaw);
                }
                return false;
            }
        }

        // Fallback: no path or PathFollower exhausted — steer directly toward target
        steerDirect(pos, currentTarget, bodySteering);
        return false;
    }

    /**
     * Direct steering toward target — used as fallback when A* fails.
     * Sets normalized direction + relative speed.
     */
    private void steerDirect(Vector3d from, Vector3d to, Steering bodySteering) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 0.5) return;

        bodySteering.setTranslation(dx / dist, 0, dz / dist);
        bodySteering.setTranslationRelativeSpeed(relativeSpeed);
        float yaw = (float) Math.toDegrees(Math.atan2(dx, dz));
        bodySteering.setYaw(yaw);
    }

    public boolean hasArrived() {
        return arrived;
    }

    public boolean hasPath() {
        return pathReady;
    }

    public Vector3d getCurrentTarget() {
        return currentTarget;
    }

    public void stop() {
        currentTarget = null;
        pathReady = false;
        arrived = false;
        follower.clearPath();
        pathfinder.clearPath();
    }
}
