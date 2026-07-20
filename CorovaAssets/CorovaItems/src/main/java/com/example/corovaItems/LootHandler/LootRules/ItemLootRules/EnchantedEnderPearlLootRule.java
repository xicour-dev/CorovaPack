package com.example.corovaItems.LootHandler.LootRules.ItemLootRules;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Drops an Enchanted Ender Pearl from Endermen.
 *
 * Default Drop Chance Configuration:
 * - Base Chance:    3%    (0.03)
 * - Looting Bonus: +1%   per level (0.01)
 * - Luck Bonus:    +1%   per level (0.01)
 *
 * DROPLIMITER ON  → competes in the single "limited" drop slot per death.
 * DROPLIMITER OFF → always drops when its roll succeeds.
 */
public class EnchantedEnderPearlLootRule extends AbstractItemLootRule {

    public EnchantedEnderPearlLootRule() {
        super(
                "EnchantedEnderPearl",
                0.03,
                0.01,
                0.01
        );

        //              mob identifier   base   loot   luck   DROPLIMITER
        registerMob("enderman",          0.01,  0.005,  0.005,  false);  // DROPLIMITER: ON
    }
}