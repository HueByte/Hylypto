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
import com.hylypto.zombie.blockbreak.BlockBreakTracker;
import com.hylypto.zombie.detection.PlayerFinder;
import com.hylypto.zombie.screamer.ScreamerManager;

/**
 * Aggro state — updates TransientPath to point at the player so the engine's
 * Path BodyMotion walks the zombies toward them. The Attack action on the
 * instruction handles melee combat when within AttackDistance.
 */
public class AggroStateHandler implements PatrolStateHandler {

    private static final System.Logger LOG = System.getLogger(AggroStateHandler.class.getName());

    private final PatrolConfig config;
    private final ScreamerManager screamerManager;
    private final BlockBreakTracker blockBreakTracker;

    private long lastPlayerSeenMillis = 0;
    private Vector3d lastAssignedTarget = null;

    public AggroStateHandler(PatrolConfig config, ScreamerManager screamerManager,
                              BlockBreakTracker blockBreakTracker) {
        this.config = config;
        this.screamerManager = screamerManager;
        this.blockBreakTracker = blockBreakTracker;
    }

    @Override
    public PatrolState tick(PatrolGroup group, Store<EntityStore> store) {
        Vector3d centroid = calculateCentroid(group, store);
        if (centroid == null) return PatrolState.AGGRO;

        Vector3d nearestPlayer = PlayerFinder.findNearest(store, centroid);

        if (nearestPlayer != null) {
            boolean playerInRange = false;
            for (Ref<EntityStore> ref : group.getMemberRefs()) {
                if (!ref.isValid()) continue;
                TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
                if (transform == null) continue;
                if (transform.getPosition().distanceTo(nearestPlayer) <= config.aggroRange) {
                    playerInRange = true;
                    break;
                }
            }

            if (playerInRange) {
                lastPlayerSeenMillis = System.currentTimeMillis();
                group.setLastKnownPlayerPosition(nearestPlayer);

                // Re-assign chase path if player moved significantly (>3 blocks)
                if (lastAssignedTarget == null || nearestPlayer.distanceTo(lastAssignedTarget) > 3.0) {
                    assignChasePath(group, store, nearestPlayer);
                    lastAssignedTarget = nearestPlayer;
                }

                if (config.blockBreakEnabled) {
                    checkStuckZombiesForBlockBreak(group, store);
                }

                return PatrolState.AGGRO;
            }
        }

        // Player not in range — check timeout
        long timeSinceLastSeen = System.currentTimeMillis() - lastPlayerSeenMillis;
        if (timeSinceLastSeen >= config.aggroTimeoutSeconds * 1000L) {
            LOG.log(System.Logger.Level.INFO,
                    "Patrol " + group.getGroupId() + " — lost player for " + config.aggroTimeoutSeconds + "s, switching to SEARCHING");
            return PatrolState.SEARCHING;
        }

        return PatrolState.AGGRO;
    }

    @Override
    public void onEnter(PatrolGroup group, Store<EntityStore> store) {
        LOG.log(System.Logger.Level.INFO, "[AGGRO-ENTER] group=" + group.getGroupId()
                + " — AGGRO! members=" + group.size());
        lastPlayerSeenMillis = System.currentTimeMillis();
        lastAssignedTarget = null;

        // Assign a TransientPath toward the player — Path BodyMotion walks them there
        Vector3d playerPos = group.getLastKnownPlayerPosition();
        if (playerPos != null) {
            assignChasePath(group, store, playerPos);
            lastAssignedTarget = playerPos;
        }

        // Trigger screamer on first aggro
        if (group.hasScreamer() && !group.hasScreamed() && playerPos != null) {
            screamerManager.onScream(group);
            group.setHasScreamed(true);
        }
    }

    @Override
    public void onExit(PatrolGroup group, Store<EntityStore> store) {
        lastAssignedTarget = null;
    }

    /**
     * Sets a TransientPath with a single waypoint at the player's position.
     * The engine's Path BodyMotion follows it, and Attack action fires when close.
     */
    private void assignChasePath(PatrolGroup group, Store<EntityStore> store, Vector3d target) {
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
                LOG.log(System.Logger.Level.ERROR, "[AGGRO-PATH] Failed to assign chase path: " + e.getMessage());
            }
        }
        LOG.log(System.Logger.Level.DEBUG, "[AGGRO-PATH] Assigned chase path to " + assigned
                + " NPCs toward (" + (int) target.x + "," + (int) target.y + "," + (int) target.z + ")");
    }

    private void checkStuckZombiesForBlockBreak(PatrolGroup group, Store<EntityStore> store) {
        Vector3d target = group.getLastKnownPlayerPosition();
        if (target == null) return;

        for (Ref<EntityStore> ref : group.getMemberRefs()) {
            if (!ref.isValid()) continue;
            TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) continue;

            Vector3d pos = transform.getPosition();
            double dx = target.x - pos.x;
            double dz = target.z - pos.z;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 0.1) continue;

            int blockX = (int) Math.floor(pos.x + (dx / len));
            int blockY = (int) Math.floor(pos.y + 0.5);
            int blockZ = (int) Math.floor(pos.z + (dz / len));

            blockBreakTracker.hitBlock(blockX, blockY, blockZ);
        }
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
