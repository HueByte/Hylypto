package com.hylypto.zombie.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hylypto.zombie.detection.PlayerFinder;
import com.hylypto.zombie.HordeManager;

import javax.annotation.Nonnull;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ECS system that monitors horde zombies for stuck detection.
 * The built-in Zombie AI (BodyMotionFind) handles pathfinding and
 * movement toward players automatically — we do NOT override it.
 * This system only detects stuck zombies and logs aggro range info.
 */
public class ZombieAggroSystem extends DelayedEntitySystem<EntityStore> {

    private static final System.Logger LOG = System.getLogger(ZombieAggroSystem.class.getName());

    private static final float CHECK_INTERVAL = 1.0f;
    private static final double MOVEMENT_THRESHOLD = 2.5;
    private static final double MAX_AGGRO_DISTANCE = 40.0;
    private static final int STUCK_CHECK_COUNT = 5;
    private static final int MAX_HISTORY = 10;
    private static final int PRUNE_INTERVAL_TICKS = 10;

    private final HordeManager hordeManager;
    private final Map<UUID, PositionHistory> positionHistories = new ConcurrentHashMap<>();
    private int tickCounter = 0;

    public ZombieAggroSystem(HordeManager hordeManager) {
        super(CHECK_INTERVAL);
        this.hordeManager = hordeManager;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(NPCEntity.getComponentType(), UUIDComponent.getComponentType());
    }

    @Override
    public void tick(float deltaTime, int entityIndex, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            // Periodic cleanup
            if (++tickCounter >= PRUNE_INTERVAL_TICKS) {
                tickCounter = 0;
                hordeManager.pruneInvalidRefs();
                positionHistories.keySet().removeIf(uuid -> !hordeManager.isHordeZombie(uuid));
            }

            UUIDComponent uuidComp = chunk.getComponent(entityIndex, UUIDComponent.getComponentType());
            if (uuidComp == null) return;

            UUID uuid = uuidComp.getUuid();
            if (!hordeManager.isHordeZombie(uuid)) return;

            TransformComponent transform = chunk.getComponent(entityIndex, TransformComponent.getComponentType());
            if (transform == null) return;

            Vector3d currentPos = transform.getPosition();

            // Track position history for stuck detection
            PositionHistory history = positionHistories.computeIfAbsent(uuid, k -> new PositionHistory());
            history.addPosition(currentPos);

            if (history.isStuck()) {
                // Log stuck zombie — the engine AI should handle re-aggro naturally
                Vector3d playerPos = PlayerFinder.findNearest(store, currentPos);
                if (playerPos != null) {
                    double dist = currentPos.distanceTo(playerPos);
                    LOG.log(System.Logger.Level.INFO,
                            "Zombie " + uuid + " stuck, " + (int) dist + " blocks from player");
                    if (dist > MAX_AGGRO_DISTANCE) {
                        LOG.log(System.Logger.Level.WARNING,
                                "Zombie " + uuid + " outside aggro range (" + (int) dist + " > " + (int) MAX_AGGRO_DISTANCE + ")");
                    }
                }
                history.reset();
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Error in aggro tick: " + e.getMessage(), e);
        }
    }

    public void cleanupZombie(UUID uuid) {
        positionHistories.remove(uuid);
        hordeManager.cleanupZombie(uuid);
    }

    public void clearAll() {
        positionHistories.clear();
    }

    // --- Inner class for movement tracking ---

    private static class PositionHistory {
        private final LinkedList<Vector3d> positions = new LinkedList<>();

        void addPosition(Vector3d pos) {
            positions.addLast(pos);
            if (positions.size() > MAX_HISTORY) {
                positions.removeFirst();
            }
        }

        boolean isStuck() {
            if (positions.size() < STUCK_CHECK_COUNT) return false;

            Vector3d oldest = positions.get(positions.size() - STUCK_CHECK_COUNT);
            for (int i = positions.size() - STUCK_CHECK_COUNT + 1; i < positions.size(); i++) {
                if (oldest.distanceTo(positions.get(i)) > MOVEMENT_THRESHOLD) {
                    return false;
                }
            }
            return true;
        }

        void reset() {
            positions.clear();
        }
    }
}
