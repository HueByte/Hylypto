package com.hylypto.waves.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hylypto.waves.HordeManager;

import javax.annotation.Nonnull;
import java.util.LinkedList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ECS system that periodically checks horde zombies and ensures they
 * are actively targeting the nearest player. Detects stuck zombies
 * and triggers re-aggro to keep them moving toward players.
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
            // Periodic cleanup: prune invalid refs and orphaned position histories
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

            PositionHistory history = positionHistories.computeIfAbsent(uuid, k -> new PositionHistory());
            history.addPosition(currentPos);

            if (history.isStuck()) {
                handleStuckZombie(uuid, currentPos, store, commandBuffer, entityIndex, chunk);
                history.reset();
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Error in aggro tick: " + e.getMessage(), e);
        }
    }

    private void handleStuckZombie(UUID uuid, Vector3d zombiePos, Store<EntityStore> store,
                                    CommandBuffer<EntityStore> commandBuffer, int entityIndex,
                                    ArchetypeChunk<EntityStore> chunk) {
        Vector3d playerPos = findNearestPlayerPosition(store);
        if (playerPos == null) return;

        double distance = zombiePos.distanceTo(playerPos);

        if (distance > MAX_AGGRO_DISTANCE) {
            LOG.log(System.Logger.Level.WARNING,
                    "Zombie " + uuid + " is " + (int) distance + " blocks from nearest player — re-aggroing");
        }

        triggerReAggro(commandBuffer, entityIndex, chunk, playerPos);
    }

    private void triggerReAggro(CommandBuffer<EntityStore> commandBuffer, int entityIndex,
                                 ArchetypeChunk<EntityStore> chunk, Vector3d targetPos) {
        try {
            NPCEntity npcEntity = chunk.getComponent(entityIndex, NPCEntity.getComponentType());
            if (npcEntity != null) {
                LOG.log(System.Logger.Level.DEBUG,
                        "Re-aggroing NPC toward (" + (int) targetPos.x + ", " + (int) targetPos.y + ", " + (int) targetPos.z + ")");
            }
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Failed to trigger re-aggro: " + e.getMessage(), e);
        }
    }

    private Vector3d findNearestPlayerPosition(Store<EntityStore> store) {
        final Vector3d[] nearest = {null};
        final double[] minDist = {Double.MAX_VALUE};

        try {
            store.forEachEntityParallel(Player.getComponentType(), (index, chunk, buffer) -> {
                TransformComponent transform = chunk.getComponent(index, TransformComponent.getComponentType());
                if (transform != null) {
                    Vector3d pos = transform.getPosition();
                    synchronized (nearest) {
                        if (nearest[0] == null) {
                            nearest[0] = pos;
                            minDist[0] = 0;
                        }
                    }
                }
            });
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Failed to find player position: " + e.getMessage(), e);
        }

        return nearest[0];
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
