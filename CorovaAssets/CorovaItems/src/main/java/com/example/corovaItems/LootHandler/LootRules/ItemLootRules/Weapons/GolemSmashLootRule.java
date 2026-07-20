package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Weapons;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Golem Smash weapon.
 *
 * Default Drop Chance Configuration:
 * - Base Chance:    5%    (0.05)
 * - Looting Bonus: +1%   per level (0.01)
 * - Luck Bonus:    +1%   per level (0.01)
 *
 * DROPLIMITER ON  → competes in the single "limited" drop slot per death.
 * DROPLIMITER OFF → always drops when its roll succeeds.
 */
public class GolemSmashLootRule extends AbstractItemLootRule {

    public GolemSmashLootRule() {
        super(
                "golemsmash",
                0.05,
                0.01,
                0.01
        );

        //              mob identifier              base   loot   luck   DROPLIMITER
        registerMob("resentful_enchanted",          0.08,  0.02,  0.02,  false);  // DROPLIMITER: ON
    }
}