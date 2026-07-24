package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Trinkets;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Blood Sugar trinket.
 *
 * Default Drop Chance Configuration:
 * - Base Chance:    1.5% (0.015)
 * - Looting Bonus: +1%  per level (0.01)
 * - Luck Bonus:    +1%  per level (0.01)
 *
 * Per-mob overrides and DropLimiter flags are listed inline.
 * DROPLIMITER ON  → competes in the single "limited" drop slot per death.
 * DROPLIMITER OFF → always drops when its roll succeeds (ignores the slot limit).
 */
public class BloodSugarLootRule extends AbstractItemLootRule {

    public BloodSugarLootRule() {
        super(
                "bloodsugar",   // Item ID (must match ItemManager registration)
                0.015,          // default 1.5% base chance
                0.01,           // default +1% per Looting level
                0.01            // default +1% per Luck level
        );

        //                   mob identifier                    base    loot   luck   DROPLIMITER
        registerMob("corovacore_giantbat",                   0.015,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("ENDER_ARCHER",                          0.015,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("corovacore_withermage_tag",                            0.015,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("corovamobs:resentful_enchanted",        0.015,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("corovacore_witherwizard",               0.015,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("reaper",                                0.015,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("healer_zombie_mob",                          0.03,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("soul_hound_mob",                      0.03,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("soul_blaze",                      0.03,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("nuclear_creeper_mob",                      0.03,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("dead_thor",                      0.03,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("neptune_mob",                      0.03,  0.01,  0.01,  false);  // DROPLIMITER: ON
        registerMob("EVJ_Spider",                      0.03,  0.01,  0.01,  false);  // DROPLIMITER: ON
    }
}