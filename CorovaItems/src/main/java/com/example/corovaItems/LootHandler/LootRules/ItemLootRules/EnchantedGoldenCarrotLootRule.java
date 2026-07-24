package com.example.corovaItems.LootHandler.LootRules.ItemLootRules;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Enchanted Golden Carrot food item.
 *
 * Default Drop Chance Configuration:
 * - Base Chance:    1%  (0.01)
 * - Looting Bonus: +1% per level (0.01)
 * - Luck Bonus:    +1% per level (0.01)
 *
 * DROPLIMITER ON  → item competes in the single "limited" drop slot per death.
 *                   If multiple ON-items roll successfully, only ONE is kept.
 * DROPLIMITER OFF → item always drops when its roll succeeds, regardless of
 *                   what other items also dropped (does not consume the slot).
 */
public class EnchantedGoldenCarrotLootRule extends AbstractItemLootRule {

    public EnchantedGoldenCarrotLootRule() {
        super(
                "enchantedgoldencarrot",  // Item ID (must match ItemManager registration)
                0.01,                     // default 1% base chance
                0.01,                     // default +1% per Looting level
                0.01                      // default +1% per Luck level
        );

        //                   mob identifier                    base   loot   luck   DROPLIMITER
        registerMob("corovacore_killerbunny",                    0.08,  0.04,  0.04,  false);   // DROPLIMITER: ON
    }
}