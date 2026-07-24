package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Trinkets;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Anchor trinket.
 *
 * Default Drop Chance Configuration:
 * - Base Chance:    1%    (0.01)
 * - Looting Bonus: +1.5% per level (0.015)
 * - Luck Bonus:    +1.5% per level (0.015)
 *
 * Per-mob overrides and DropLimiter flags are listed inline.
 * DROPLIMITER ON  → competes in the single "limited" drop slot per death.
 * DROPLIMITER OFF → always drops when its roll succeeds (ignores the slot limit).
 */
public class AnchorLootRule extends AbstractItemLootRule {

    public AnchorLootRule() {
        super(
                "anchor",   // Item ID (must match ItemManager registration)
                0.01,       // default 1% base chance
                0.015,      // default +1.5% per Looting level
                0.015       // default +1.5% per Luck level
        );

        //                   mob identifier                    base    loot    luck   DROPLIMITER
        registerMob("corovamobs_GJ_GuardianJockey",           0.01,   0.015,  0.015, false);  // DROPLIMITER: ON
        registerMob("drownedatlantian",                       0.01,   0.015,  0.015, false);  // DROPLIMITER: ON
        registerMob("drownedondolphin",                       0.01,   0.015,  0.015, false);  // DROPLIMITER: ON
        registerMob("corovacore_killerdolphin",                          0.01,   0.015,  0.015, false);  // DROPLIMITER: ON
    }
}