package com.hylypto.zombie.system;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hylypto.zombie.PatrolManager;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * ECS system that detects patrol zombie deaths and notifies PatrolManager.
 */
public class PatrolDeathSystem extends DeathSystems.OnDeathSystem {

    private static final System.Logger LOG = System.getLogger(PatrolDeathSystem.class.getName());

    private final PatrolManager patrolManager;

    public PatrolDeathSystem(PatrolManager patrolManager) {
        this.patrolManager = patrolManager;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(NPCEntity.getComponentType(), UUIDComponent.getComponentType());
    }

    @Override
    public void onComponentAdded(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull DeathComponent component,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uuidComp == null) return;

        UUID uuid = uuidComp.getUuid();
        if (!patrolManager.isPatrolZombie(uuid)) return;

        LOG.log(System.Logger.Level.DEBUG, "Patrol zombie died: " + uuid);
        patrolManager.onZombieDeath(uuid);
    }
}
