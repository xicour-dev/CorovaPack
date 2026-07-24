package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Enchantments;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Book of Music enchantment book.
 */
public class MusicBookLootRule extends AbstractItemLootRule {

    public MusicBookLootRule() {
        super(
                "book_music",   // Item ID
                0.01,           // default 1% base chance
                0.005,          // default +0.5% per Looting level
                0.005           // default +0.5% per Luck level
        );

        registerMob("lost_merchant_boss",          0.025,  0.005,  0.005,  false);
        registerMob("mobid_2",          0.01,  0.005,  0.005,  true);
        registerMob("mobid_3",          0.01,  0.005,  0.005,  true);
        registerMob("mobid_4",          0.01,  0.005,  0.005,  true);
        registerMob("mobid_5",          0.01,  0.005,  0.005,  true);
    }
}
