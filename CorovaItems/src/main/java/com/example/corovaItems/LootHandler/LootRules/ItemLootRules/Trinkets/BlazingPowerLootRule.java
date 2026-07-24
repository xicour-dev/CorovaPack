package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Trinkets;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Blazing Power trinket.
 *
 * Default Drop Chance Configuration:
 * - Base Chance:    1%   (0.01)
 * - Looting Bonus: +1%  per level (0.01)
 * - Luck Bonus:    +1%  per level (0.01)
 *
 * Per-mob overrides and DropLimiter flags are listed inline.
 * DROPLIMITER ON  → competes in the single "limited" drop slot per death.
 * DROPLIMITER OFF → always drops when its roll succeeds (ignores the slot limit).
 */
public class BlazingPowerLootRule extends AbstractItemLootRule {

    public BlazingPowerLootRule() {
        super(
                "blazingpower",   // Item ID (must match ItemManager registration)
                0.01,             // default 1% base chance
                0.01,             // default +1% per Looting level
                0.01              // default +1% per Luck level
        );

        //                   mob identifier                       base   loot   luck   DROPLIMITER
        registerMob("corovamobs:withering_inferno",              0.01,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("corovacore_cyclops",                        0.01,  0.01,  0.01,  false);  // DROPLIMITER: ON
        registerMob("corovacore_withermage_tag",                                0.01,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("corovamobs:resentful_enchanted",            0.01,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("VJ_Spider",                               0.01,  0.01,  0.01,  false);  // DROPLIMITER: ON
        registerMob("corovacore_witherwizard",                   0.01,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("reaper",                                    0.01,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("hell_hound_mob",                                 0.01,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("strafer_mob",                                0.07,  0.03,  0.03,  false);  // DROPLIMITER: ON
        registerMob("dead_thor",                                  0.03,  0.02,  0.02,  false);  // DROPLIMITER: ON
        registerMob("neptune_mob",                                   0.03,  0.02,  0.02,  false);  // DROPLIMITER: ON
    }
}