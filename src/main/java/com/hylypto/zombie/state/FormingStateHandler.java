package com.hylypto.zombie.state;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hylypto.zombie.PatrolConfig;
import com.hylypto.zombie.PatrolGroup;

public class FormingStateHandler implements PatrolStateHandler {

    private static final System.Logger LOG = System.getLogger(FormingStateHandler.class.getName());
    private final PatrolConfig config;

    public FormingStateHandler(PatrolConfig config) {
        this.config = config;
    }

    @Override
    public PatrolState tick(PatrolGroup group, Store<EntityStore> store) {
        long elapsed = group.millisInCurrentState();
        if (elapsed >= config.swarmFormationSeconds * 1000L) {
            LOG.log(System.Logger.Level.INFO,
                    "Patrol " + group.getGroupId() + " formed (" + group.size() + " members) — starting patrol");
            return PatrolState.PATROLLING;
        }
        return PatrolState.FORMING;
    }

    @Override
    public void onEnter(PatrolGroup group, Store<EntityStore> store) {}

    @Override
    public void onExit(PatrolGroup group, Store<EntityStore> store) {}
}
