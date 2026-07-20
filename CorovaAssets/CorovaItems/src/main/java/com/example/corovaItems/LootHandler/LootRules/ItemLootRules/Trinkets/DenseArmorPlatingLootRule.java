package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Trinkets;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Dense Armor Plating trinket.
 *
 * Default Drop Chance Configuration:
 * - Base Chance:    1%  (0.01)
 * - Looting Bonus: +1% per level (0.01)
 * - Luck Bonus:    +1% per level (0.01)
 *
 * The Resentful Enchanted Golem uses a PersistentDataContainer key
 * (corovamobs:resentful_enchanted) instead of a scoreboard tag —
 * the "namespace:key" format in registerMob handles this automatically.
 *
 * Per-mob overrides and DropLimiter flags are listed inline.
 * DROPLIMITER ON  → competes in the single "limited" drop slot per death.
 * DROPLIMITER OFF → always drops when its roll succeeds (ignores the slot limit).
 */
public class DenseArmorPlatingLootRule extends AbstractItemLootRule {

    public DenseArmorPlatingLootRule() {
        super(
                "densearmorplating",   // Item ID (must match ItemManager registration)
                0.01,                  // default 1% base chance
                0.01,                  // default +1% per Looting level
                0.01                   // default +1% per Luck level
        );

        //                   mob identifier                       base   loot   luck   DROPLIMITER
        registerMob("corovamobs:resentful_enchanted",            0.09,  0.03,  0.03,  false);  // DROPLIMITER: ON  (PDC key)
        registerMob("corovacore_straymage_tag",                                 0.16,  0.05,  0.05,  true);  // DROPLIMITER: ON
        registerMob("corovacore_withermage_tag",                                0.04,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("bull_mob",                                0.04,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("grenade_creeper_mob",                                0.04,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("undead_pvper_mob",                                0.04,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("corovacore_unknownknight",                                0.04,  0.01,  0.01,  true);  // DROPLIMITER: ON
        registerMob("mutant_copper_golem_boss",                                0.04,  0.01,  0.01,  false);  // DROPLIMITER: OFF
        registerMob("lost_merchant_boss",                                0.04,  0.01,  0.01,  false);  // DROPLIMITER: OFF
    }
}