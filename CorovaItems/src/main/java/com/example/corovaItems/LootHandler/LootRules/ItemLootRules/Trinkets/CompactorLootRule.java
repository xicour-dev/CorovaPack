package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Trinkets;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Compactor trinket.
 *
 * Default Drop Chance Configuration:
 * - Base Chance:    1%    (0.01)
 * - Looting Bonus: +0.5% per level (0.005)
 * - Luck Bonus:    +0.5% per level (0.005)
 *
 * DROPLIMITER ON  → competes in the single "limited" drop slot per death.
 * DROPLIMITER OFF → always drops when its roll succeeds.
 */
public class CompactorLootRule extends AbstractItemLootRule {

    public CompactorLootRule() {
        super(
                "compactor",
                0.01,
                0.005,
                0.005
        );

        //              mob identifier   base   loot    luck    DROPLIMITER
        registerMob("mobid_1",          0.01,  0.005,  0.005,  true);  // DROPLIMITER: ON
        registerMob("mobid_2",          0.01,  0.005,  0.005,  true);  // DROPLIMITER: ON
        registerMob("mobid_3",          0.01,  0.005,  0.005,  true);  // DROPLIMITER: ON
    }
}