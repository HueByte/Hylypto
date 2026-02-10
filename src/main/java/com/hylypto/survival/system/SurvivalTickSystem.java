package com.hylypto.survival.system;

import com.hylypto.survival.component.SurvivalStatsComponent;

/**
 * ECS system that drains survival stats per tick.
 * Queries entities with SurvivalStatsComponent and applies drain rates.
 *
 * Implementation note: Will extend EntityTickingSystem from the Hytale API
 * once the dependency is resolved. For now, the logic is structured
 * to be easily adapted.
 */
public class SurvivalTickSystem {

    private static final float HUNGER_DRAIN_PER_TICK = 0.01f;
    private static final float THIRST_DRAIN_PER_TICK = 0.015f;
    private static final float FATIGUE_GAIN_PER_TICK = 0.005f;

    /**
     * Process a single entity's survival stats for one tick.
     */
    public void tick(SurvivalStatsComponent stats) {
        stats.drain(HUNGER_DRAIN_PER_TICK, THIRST_DRAIN_PER_TICK, FATIGUE_GAIN_PER_TICK);

        if (stats.isCritical()) {
            // Future: apply debuffs, send warnings, affect RTS unit effectiveness
        }
    }
}
