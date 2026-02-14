package com.hylypto.zombie.system;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.DelayedEntitySystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hylypto.zombie.PatrolConfig;
import com.hylypto.zombie.PatrolManager;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * ECS system that drives the patrol state machine.
 * Ticks each patrol zombie, deduplicates per-group via PatrolManager.
 */
public class PatrolTickSystem extends DelayedEntitySystem<EntityStore> {

    private static final System.Logger LOG = System.getLogger(PatrolTickSystem.class.getName());

    private final PatrolManager patrolManager;

    public PatrolTickSystem(PatrolManager patrolManager, PatrolConfig config) {
        super(config.tickIntervalSeconds);
        this.patrolManager = patrolManager;
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
            UUIDComponent uuidComp = chunk.getComponent(entityIndex, UUIDComponent.getComponentType());
            if (uuidComp == null) return;

            UUID uuid = uuidComp.getUuid();
            if (!patrolManager.isPatrolZombie(uuid)) return;

            UUID groupId = patrolManager.getGroupIdForZombie(uuid);
            if (groupId == null) return;

            patrolManager.tickGroup(groupId, store);
        } catch (Exception e) {
            LOG.log(System.Logger.Level.ERROR, "Error in patrol tick: " + e.getMessage(), e);
        }
    }
}
