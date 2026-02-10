package com.hylypto.combat.model;

/**
 * Immutable weapon stat definition.
 * fireRate and magazineSize are 0 for melee weapons.
 */
public record WeaponStats(
        String weaponId,
        DamageType damageType,
        float baseDamage,
        float fireRate,
        int magazineSize,
        float range
) {}
