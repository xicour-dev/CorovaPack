package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Trinkets;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Backpack trinket.
 *
 * Default Drop Chance Configuration:
 * - Base Chance:    1%    (0.01)
 * - Looting Bonus: +0.5% per level (0.005)
 * - Luck Bonus:    +0.5% per level (0.005)
 *
 * DROPLIMITER ON  → competes in the single "limited" drop slot per death.
 * DROPLIMITER OFF → always drops when its roll succeeds.
 */
public class BackpackLootRule extends AbstractItemLootRule {

    public BackpackLootRule() {
        super(
                "backpack",
                0.01,
                0.005,
                0.005
        );

        //              mob identifier   base   loot    luck    DROPLIMITER
        registerMob("corovamobs:resentful_enchanted",          0.05,  0.05,  0.05,  false);  // DROPLIMITER: ON
        registerMob("VJ_Skeleton",          0.01,  0.005,  0.005,  false);  // DROPLIMITER: ON
        registerMob("EVJ_Skeleton",          0.01,  0.005,  0.005,  false);  // DROPLIMITER: ON
        registerMob("drownedatlantian",          0.01,  0.005,  0.005,  false);  // DROPLIMITER: ON
        registerMob("dead_thor_mob",          0.01,  0.005,  0.005,  false);  // DROPLIMITER: ON
        registerMob("BJ_Skeleton",          0.01,  0.005,  0.005,  false);  // DROPLIMITER: ON
        registerMob("neptune_mob",          0.01,  0.005,  0.005,  false);  // DROPLIMITER: ON
        registerMob("dead_thor_mob",          0.01,  0.005,  0.005,  false);  // DROPLIMITER: ON
        registerMob("dead_thor_mob",          0.01,  0.005,  0.005,  false);  // DROPLIMITER: ON







    }
}