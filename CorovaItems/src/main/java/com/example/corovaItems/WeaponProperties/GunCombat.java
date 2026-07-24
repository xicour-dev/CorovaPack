package com.example.corovaItems.WeaponProperties;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * GunCombat — Utility for managing gun-related combat logic.
 *
 * Provides a way to track when a LivingEntity's damage event originates
 * from a custom gun, allowing other systems like CorovaCombat to bypass
 * standard melee cooldown checks.
 *
 * This class serves as a coordination point for firearm-based systems,
 * ensuring that rapid-fire mechanisms are not suppressed by the global
 * combat cooldown tracker in CorovaCombat.
 */
public class GunCombat {

    /**
     * Set of UUIDs currently in the process of firing a gun.
     * This is used as a flag to notify CorovaCombat to ignore melee cooldowns.
     */
    public static final Set<UUID> firingGuns = new HashSet<>();

}
