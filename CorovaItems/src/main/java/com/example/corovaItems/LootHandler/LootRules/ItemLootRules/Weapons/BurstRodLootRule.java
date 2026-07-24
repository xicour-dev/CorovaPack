package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Weapons;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Burst Rod weapon.
 *
 * Default Drop Chance Configuration:
 * - Base Chance:    1%    (0.01)
 * - Looting Bonus: +0.5% per level (0.005)
 * - Luck Bonus:    +0.5% per level (0.005)
 *
 * DROPLIMITER ON  → competes in the single "limited" drop slot per death.
 * DROPLIMITER OFF → always drops when its roll succeeds.
 */
public class BurstRodLootRule extends AbstractItemLootRule {

    public BurstRodLootRule() {
        super(
                "burstrod",
                0.01,
                0.005,
                0.005
        );

        //              mob identifier   base   loot    luck    DROPLIMITER
        registerMob("strafer_mob",          0.04,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("mobid_2",          0.01,  0.005,  0.005,  true);  // DROPLIMITER: ON
        registerMob("mobid_3",          0.01,  0.005,  0.005,  true);  // DROPLIMITER: ON
    }
}