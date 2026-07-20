package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Weapons;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Copper Mjolnir weapon.
 *
 * Default Drop Chance Configuration:
 * - Base Chance:    1%    (0.01)
 * - Looting Bonus: +0.5% per level (0.005)
 * - Luck Bonus:    +0.5% per level (0.005)
 *
 * DROPLIMITER ON  → competes in the single "limited" drop slot per death.
 * DROPLIMITER OFF → always drops when its roll succeeds.
 */
public class CopperMjolnirLootRule extends AbstractItemLootRule {

    public CopperMjolnirLootRule() {
        super(
                "copper_mjolnir",
                0.01,
                0.005,
                0.005
        );

        //              mob identifier   base   loot    luck    DROPLIMITER
        registerMob("dead_thor",          0.1,  0.03,  0.03,  true);  // DROPLIMITER: ON
        registerMob("mobid_2",          0.01,  0.005,  0.005,  true);  // DROPLIMITER: ON
        registerMob("mobid_3",          0.01,  0.005,  0.005,  true);  // DROPLIMITER: ON
    }
}