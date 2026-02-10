package com.hylypto.combat.model;

import java.util.Map;

/**
 * Immutable armor stat definition.
 * Resistances map damage types to reduction factors (0.0 = none, 1.0 = immune).
 */
public record ArmorStats(
        String armorId,
        int tier,
        Map<DamageType, Float> resistances
) {}
