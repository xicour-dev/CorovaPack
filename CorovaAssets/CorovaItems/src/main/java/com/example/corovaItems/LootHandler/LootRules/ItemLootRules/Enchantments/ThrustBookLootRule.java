package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Enchantments;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Book of Thrust enchantment book.
 */
public class ThrustBookLootRule extends AbstractItemLootRule {

    public ThrustBookLootRule() {
        super(
                "book_thrust",   // Item ID
                0.01,           // default 1% base chance
                0.005,          // default +0.5% per Looting level
                0.005           // default +0.5% per Luck level
        );

        registerMob("mobid_1",          0.01,  0.005,  0.005,  true);
        registerMob("mobid_2",          0.01,  0.005,  0.005,  true);
        registerMob("mobid_3",          0.01,  0.005,  0.005,  true);
    }
}
