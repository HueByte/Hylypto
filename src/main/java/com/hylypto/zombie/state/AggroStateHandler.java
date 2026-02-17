package com.hylypto.zombie.state;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.builtin.path.path.TransientPath;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hylypto.zombie.PatrolConfig;
import com.hylypto.zombie.PatrolGroup;
import com.hylypto.zombie.blockbreak.BlockBreakTracker;
import com.hylypto.zombie.detection.PlayerFinder;
import com.hylypto.zombie.screamer.ScreamerManager;

/**
 * Aggro state — the built-in Zombie AI (BodyMotionFind) already chases
 * players with proper pathfinding, steering, and animation.
 * We do NOT override movement here. This handler only tracks whether
 * the player is still in range and handles state transitions.
 */
public class AggroStateHandler implements PatrolStateHandler {

    private static final System.Logger LOG = System.getLogger(AggroStateHandler.class.getName());

    private final PatrolConfig config;
    private final ScreamerManager screamerManager;
    private final BlockBreakTracker blockBreakTracker;

    private long lastPlayerSeenMillis = 0;

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

                // The engine's Zombie AI handles chasing — we just track state

                // Check for stuck zombies near blocks — trigger block breaking
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

        // Clear TransientPath so the engine's Seek instruction doesn't try to cast it
        // to IPrefabPath (causes ClassCastException). Seek uses its own A* pathfinding.
        clearTransientPaths(group, store);

        // Trigger screamer on first aggro
        if (group.hasScreamer() && !group.hasScreamed() && group.getLastKnownPlayerPosition() != null) {
            screamerManager.onScream(group);
            group.setHasScreamed(true);
        }
    }

    @Override
    public void onExit(PatrolGroup group, Store<EntityStore> store) {}

    private void clearTransientPaths(PatrolGroup group, Store<EntityStore> store) {
        for (Ref<EntityStore> ref : group.getMemberRefs()) {
            if (!ref.isValid()) continue;
            try {
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                if (npc != null) {
                    npc.getPathManager().setTransientPath(new TransientPath());
                }
            } catch (Exception e) {
                LOG.log(System.Logger.Level.ERROR, "[AGGRO] Failed to clear path: " + e.getMessage());
            }
        }
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
