package com.hylypto.combat;

import com.hylypto.combat.model.ArmorStats;
import com.hylypto.combat.model.WeaponStats;

/**
 * Pure function for damage calculation.
 * Applies armor resistance to weapon base damage.
 */
public final class DamageCalculator {

    private DamageCalculator() {}

    /**
     * Calculate final damage after armor resistance.
     *
     * @param weapon the attacking weapon stats
     * @param armor  the defending armor stats (may be null for unarmored)
     * @return final damage value, always >= 0
     */
    public static float calculate(WeaponStats weapon, ArmorStats armor) {
        float resistance = 0f;
        if (armor != null && armor.resistances() != null) {
            resistance = armor.resistances().getOrDefault(weapon.damageType(), 0f);
        }
        return Math.max(0, weapon.baseDamage() * (1.0f - resistance));
    }
}
