package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Enchantments;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Book of Hook enchantment book.
 */
public class HookBookLootRule extends AbstractItemLootRule {

    public HookBookLootRule() {
        super(
                "book_hook",   // Item ID
                0.01,           // default 1% base chance
                0.005,          // default +0.5% per Looting level
                0.005           // default +0.5% per Luck level
        );

        registerMob("drownedatlantian",          0.01,  0.005,  0.005,  true);
        registerMob("dolphinguardianjockey",          0.01,  0.005,  0.005,  true);
        registerMob("mobid_3",          0.01,  0.005,  0.005,  true);
    }
}
