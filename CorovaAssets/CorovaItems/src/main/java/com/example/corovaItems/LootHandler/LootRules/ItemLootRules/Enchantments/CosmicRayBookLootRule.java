package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Enchantments;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Book of Cosmic Ray enchantment book.
 */
public class CosmicRayBookLootRule extends AbstractItemLootRule {

    public CosmicRayBookLootRule() {
        super(
                "book_cosmic_ray_1",   // Item ID
                0.01,           // default 1% base chance
                0.005,          // default +0.5% per Looting level
                0.005           // default +0.5% per Luck level
        );

        registerMob("mobid_1",          0.01,  0.005,  0.005,  true);
        registerMob("mobid_2",          0.01,  0.005,  0.005,  true);
        registerMob("mobid_3",          0.01,  0.005,  0.005,  true);
    }
}
