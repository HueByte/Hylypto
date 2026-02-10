package com.hylypto.survival.component;

/**
 * ECS component tracking survival stats: hunger, thirst, fatigue.
 * Values range from 0-100. Hunger/thirst deplete over time (100 = full),
 * fatigue accumulates over time (0 = rested, 100 = exhausted).
 */
public class SurvivalStatsComponent {

    private float hunger;
    private float thirst;
    private float fatigue;

    public SurvivalStatsComponent() {
        this(100f, 100f, 0f);
    }

    public SurvivalStatsComponent(float hunger, float thirst, float fatigue) {
        this.hunger = hunger;
        this.thirst = thirst;
        this.fatigue = fatigue;
    }

    public SurvivalStatsComponent(SurvivalStatsComponent other) {
        this.hunger = other.hunger;
        this.thirst = other.thirst;
        this.fatigue = other.fatigue;
    }

    /**
     * Drain survival stats by the given rates.
     * Hunger and thirst decrease, fatigue increases.
     */
    public void drain(float hungerRate, float thirstRate, float fatigueRate) {
        this.hunger = Math.max(0, hunger - hungerRate);
        this.thirst = Math.max(0, thirst - thirstRate);
        this.fatigue = Math.min(100, fatigue + fatigueRate);
    }

    /**
     * Returns true if any survival stat is in a critical state.
     * Critical state: hunger <= 10, thirst <= 10, or fatigue >= 90.
     */
    public boolean isCritical() {
        return hunger <= 10 || thirst <= 10 || fatigue >= 90;
    }

    /**
     * Returns effectiveness multiplier based on survival stats.
     * Used to weaken RTS units when logistics are poor.
     * Range: 0.5 (critical) to 1.0 (healthy).
     */
    public float getEffectivenessMultiplier() {
        float hungerFactor = hunger / 100f;
        float thirstFactor = thirst / 100f;
        float fatigueFactor = 1f - (fatigue / 100f);
        float average = (hungerFactor + thirstFactor + fatigueFactor) / 3f;
        return 0.5f + (average * 0.5f);
    }

    public float getHunger() {
        return hunger;
    }

    public void setHunger(float hunger) {
        this.hunger = Math.max(0, Math.min(100, hunger));
    }

    public float getThirst() {
        return thirst;
    }

    public void setThirst(float thirst) {
        this.thirst = Math.max(0, Math.min(100, thirst));
    }

    public float getFatigue() {
        return fatigue;
    }

    public void setFatigue(float fatigue) {
        this.fatigue = Math.max(0, Math.min(100, fatigue));
    }
}
