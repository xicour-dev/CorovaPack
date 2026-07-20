package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Enchantments;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Book of Critical enchantment book.
 */
public class CriticalBookLootRule extends AbstractItemLootRule {

    public CriticalBookLootRule() {
        super(
                "book_critical_1",   // Item ID
                0.01,           // default 1% base chance
                0.005,          // default +0.5% per Looting level
                0.005           // default +0.5% per Luck level
        );

        registerMob("risen_juggernaut_mob",          0.02,  0.02,  0.02,  true);
        registerMob("sparringskeleton",          0.01,  0.005,  0.005,  true);
        registerMob("hell_hound_mob",          0.01,  0.005,  0.005,  true);
        registerMob("soul_hound_mob",          0.01,  0.005,  0.005,  true);

    }
}
