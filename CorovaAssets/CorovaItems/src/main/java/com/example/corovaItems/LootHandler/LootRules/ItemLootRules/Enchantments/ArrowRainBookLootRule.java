package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Enchantments;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Book of Arrow Rain enchantment book.
 */
public class ArrowRainBookLootRule extends AbstractItemLootRule {

    public ArrowRainBookLootRule() {
        super(
                "book_arrowrain",   // Item ID
                0.01,           // default 1% base chance
                0.005,          // default +0.5% per Looting level
                0.005           // default +0.5% per Luck level
        );

        registerMob("neptune_mob",          0.02,  0.03,  0.02,  true);
        registerMob("EVJ_Skeleton",          0.01,  0.005,  0.005,  true);
        registerMob("corovacore_straymage_tag",          0.05,  0.02,  0.02,  true);
        registerMob("mobid_4",          0.01,  0.005,  0.005,  true);
        registerMob("mobid_5",          0.01,  0.005,  0.005,  true);
    }
}
