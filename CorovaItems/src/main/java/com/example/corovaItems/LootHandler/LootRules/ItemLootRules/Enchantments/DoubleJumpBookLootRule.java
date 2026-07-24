package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Enchantments;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Book of Double Jump enchantment book.
 */
public class DoubleJumpBookLootRule extends AbstractItemLootRule {

    public DoubleJumpBookLootRule() {
        super(
                "book_doublejump",   // Item ID
                0.01,           // default 1% base chance
                0.005,          // default +0.5% per Looting level
                0.005           // default +0.5% per Luck level
        );

        registerMob("hell_hound_mob",          0.01,  0.005,  0.005,  true);
        registerMob("soul_hound_mob",          0.01,  0.005,  0.005,  true);
        registerMob("mobid_3",          0.01,  0.005,  0.005,  true);
        registerMob("mobid_4",          0.01,  0.005,  0.005,  true);
        registerMob("mobid_5",          0.01,  0.005,  0.005,  true);
    }
}
