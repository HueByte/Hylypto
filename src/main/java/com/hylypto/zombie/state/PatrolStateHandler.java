package com.hylypto.zombie.state;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hylypto.zombie.PatrolGroup;

public interface PatrolStateHandler {

    PatrolState tick(PatrolGroup group, Store<EntityStore> store);

    void onEnter(PatrolGroup group, Store<EntityStore> store);

    void onExit(PatrolGroup group, Store<EntityStore> store);
}
