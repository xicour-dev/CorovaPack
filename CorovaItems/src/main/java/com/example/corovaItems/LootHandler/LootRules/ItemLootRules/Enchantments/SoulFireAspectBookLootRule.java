package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Enchantments;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Book of Soul Fire Aspect enchantment book.
 */
public class SoulFireAspectBookLootRule extends AbstractItemLootRule {

    public SoulFireAspectBookLootRule() {
        super(
                "book_soul_fire_aspect_1",   // Item ID
                0.01,           // default 1% base chance
                0.005,          // default +0.5% per Looting level
                0.005           // default +0.5% per Luck level
        );

        registerMob("soul_hound_mob",          0.01,  0.005,  0.005,  true);
        registerMob("soul_blaze",          0.01,  0.005,  0.005,  true);
        registerMob("mobid_3",          0.01,  0.005,  0.005,  true);
    }
}
