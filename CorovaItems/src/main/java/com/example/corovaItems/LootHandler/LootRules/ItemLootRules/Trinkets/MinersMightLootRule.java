package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Trinkets;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Miner's Might trinket.
 *
 * Default Drop Chance Configuration:
 * - Base Chance:    1.5% (0.015)
 * - Looting Bonus: +1.5% per level (0.015)
 * - Luck Bonus:    +1.5% per level (0.015)
 *
 * Per-mob overrides and DropLimiter flags are listed inline.
 * DROPLIMITER ON  → competes in the single "limited" drop slot per death.
 * DROPLIMITER OFF → always drops when its roll succeeds (ignores the slot limit).
 */
public class MinersMightLootRule extends AbstractItemLootRule {

    public MinersMightLootRule() {
        super(
                "minersmight",   // Item ID (must match ItemManager registration)
                0.015,           // default 1.5% base chance
                0.015,           // default +1.5% per Looting level
                0.015            // default +1.5% per Luck level
        );

        //                   mob identifier          base    loot    luck    DROPLIMITER
        registerMob("corovacore_cavebat",           0.015,  0.015,  0.015,  true);  // DROPLIMITER: ON
        registerMob("undead_miner_mob",             0.03,  0.015,  0.015,  true);  // DROPLIMITER: ON
        registerMob("giantbat",             0.03,  0.015,  0.015,  true);  // DROPLIMITER: ON
        registerMob("witherbat",             0.01,  0.005,  0.005,  true);  // DROPLIMITER: ON
        registerMob("cyclops",             0.01,  0.005,  0.005,  true);  // DROPLIMITER: ON
    }
}