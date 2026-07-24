package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Trinkets;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Energy Formula trinket.
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
public class EnergyFormulaLootRule extends AbstractItemLootRule {

    public EnergyFormulaLootRule() {
        super(
                "energyformula",   // Item ID (must match ItemManager registration)
                0.015,             // default 1.5% base chance
                0.015,             // default +1.5% per Looting level
                0.015              // default +1.5% per Luck level
        );

        //                   mob identifier                    base    loot    luck    DROPLIMITER
        registerMob("guardian_jockey_mob",                   0.015,  0.015,  0.015,  true);  // DROPLIMITER: ON
        registerMob("hell_hound_mob",                        0.015,  0.015,  0.015,  true);  // DROPLIMITER: ON
        registerMob("corovacore_witherwizard",               0.015,  0.015,  0.015,  true);  // DROPLIMITER: ON
        registerMob("zombiejockey",               0.015,  0.015,  0.015,  true);  // DROPLIMITER: ON
        registerMob("rabid_wolf_mob",               0.015,  0.015,  0.015,  true);  // DROPLIMITER: ON
        registerMob("soul_hound_mob",               0.015,  0.015,  0.015,  true);  // DROPLIMITER: ON
        registerMob("undead_pvper_mob",               0.015,  0.015,  0.015,  true);  // DROPLIMITER: ON
        registerMob("hunter_spider_mob",               0.015,  0.015,  0.015,  true);  // DROPLIMITER: ON
        registerMob("corovacore_unknownknight",               0.015,  0.015,  0.015,  true);  // DROPLIMITER: ON
        registerMob("EVJ_Spider",               0.015,  0.015,  0.015,  true);  // DROPLIMITER: ON
        registerMob("corovacore_killerbunny",               0.015,  0.015,  0.015,  true);  // DROPLIMITER: ON
    }
}