package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Enchantments;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Book of Steed enchantment book.
 */
public class SteedBookLootRule extends AbstractItemLootRule {

    public SteedBookLootRule() {
        super(
                "book_steed",   // Item ID
                0.01,           // default 1% base chance
                0.005,          // default +0.5% per Looting level
                0.005           // default +0.5% per Luck level
        );

        registerMob("corovacore_witherwizard",          0.01,  0.005,  0.005,  true);
        registerMob("corovacore_skeletonjockeypack_tag",          0.005,  0.005,  0.005,  true);
        registerMob("mobid_3",          0.01,  0.005,  0.005,  true);
        registerMob("mobid_4",          0.01,  0.005,  0.005,  true);
        registerMob("mobid_5",          0.01,  0.005,  0.005,  true);
    }
}
