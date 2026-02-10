package com.hylypto.waves.system;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hylypto.waves.HordeManager;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * ECS system that detects NPC deaths and notifies the HordeManager.
 * Only counts deaths of entities tracked as horde zombies (by UUID).
 */
public class ZombieDeathSystem extends DeathSystems.OnDeathSystem {

    private final HordeManager hordeManager;
    private ZombieAggroSystem aggroSystem;

    public ZombieDeathSystem(HordeManager hordeManager) {
        this.hordeManager = hordeManager;
    }

    public void setAggroSystem(ZombieAggroSystem aggroSystem) {
        this.aggroSystem = aggroSystem;
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
        if (!hordeManager.isHordeZombie(uuid)) return;

        hordeManager.onZombieKilled(uuid);

        if (aggroSystem != null) {
            aggroSystem.cleanupZombie(uuid);
        }
    }
}
