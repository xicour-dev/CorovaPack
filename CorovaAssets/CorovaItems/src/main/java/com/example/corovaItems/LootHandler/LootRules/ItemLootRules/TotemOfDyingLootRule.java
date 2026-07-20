package com.example.corovaItems.LootHandler.LootRules.ItemLootRules;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Totem of Dying trinket.
 *
 * Default Drop Chance Configuration:
 * - Base Chance:    1%    (0.01)
 * - Looting Bonus: +0.5% per level (0.005)
 * - Luck Bonus:    +0.5% per level (0.005)
 *
 * Per-mob overrides and DropLimiter flags are listed inline.
 * DROPLIMITER ON  → competes in the single "limited" drop slot per death.
 * DROPLIMITER OFF → always drops when its roll succeeds (ignores the slot limit).
 */
public class TotemOfDyingLootRule extends AbstractItemLootRule {

    public TotemOfDyingLootRule() {
        super(
                "totemofdying",   // Item ID (must match ItemManager registration)
                0.01,             // default 1% base chance
                0.005,            // default +0.5% per Looting level
                0.005             // default +0.5% per Luck level
        );

        //                   mob identifier                    base   loot    luck    DROPLIMITER
        registerMob("corovamobs_GJ_GuardianJockey",          0.01,  0.005,  0.005,  true);  // DROPLIMITER: ON
        registerMob("VJ_Skeleton",                           0.01,  0.005,  0.005,  true);  // DROPLIMITER: ON
        registerMob("ENDER_ARCHER",                          0.01,  0.005,  0.005,  true);  // DROPLIMITER: ON
        registerMob("corovamobs:resentful_enchanted",        0.01,  0.005,  0.005,  true);  // DROPLIMITER: ON  (PDC key)
        registerMob("corovacore_witherwizard",               0.01,  0.005,  0.005,  true);  // DROPLIMITER: ON
        registerMob("reaper",                                0.01,  0.005,  0.005,  true);  // DROPLIMITER: ON
    }
}