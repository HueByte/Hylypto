package com.hylypto.survival;

import com.hylypto.zombie.PatrolManager;

/**
 * Manager for the survival pillar.
 * Holds the patrol system and future subsystems (injury, environmental hazards, etc).
 */
public class SurvivalManager {

    private final PatrolManager patrolManager;

    public SurvivalManager(PatrolManager patrolManager) {
        this.patrolManager = patrolManager;
    }

    public PatrolManager getPatrolManager() {
        return patrolManager;
    }

    public void shutdown() {
        patrolManager.shutdown();
    }
}
