package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Enchantments;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Book of Lightning enchantment book.
 *
 * Default Drop Chance Configuration:
 * - Base Chance:    1%    (0.01)
 * - Looting Bonus: +0.5% per level (0.005)
 * - Luck Bonus:    +0.5% per level (0.005)
 *
 * Replace "mobid_N" placeholders with the real mob scoreboard tag / PDC key.
 *
 * DROPLIMITER ON  → competes in the single "limited" drop slot per death.
 * DROPLIMITER OFF → always drops when its roll succeeds.
 */
public class LightningBookLootRule extends AbstractItemLootRule {

    public LightningBookLootRule() {
        super(
                "book_lightning",   // Item ID (must match ItemManager registration)
                0.01,           // default 1% base chance
                0.005,          // default +0.5% per Looting level
                0.005           // default +0.5% per Luck level
        );

        //              mob identifier   base   loot    luck    DROPLIMITER   minLvl  maxLvl
        registerMob("dead_thor",          0.06,  0.03,  0.03,  null,         1,      1);
        registerMob("whirlwind_fury_mob",          0.01,  0.005,  0.005,  true);  // DROPLIMITER: ON
        registerMob("mobid_3",          0.01,  0.005,  0.005,  true);  // DROPLIMITER: ON
        registerMob("mobid_4",          0.01,  0.005,  0.005,  true);  // DROPLIMITER: ON
        registerMob("mobid_5",          0.01,  0.005,  0.005,  true);  // DROPLIMITER: ON
    }
}