package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Enchantments;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Book of Vein Miner enchantment book.
 */
public class VeinMinerBookLootRule extends AbstractItemLootRule {

    public VeinMinerBookLootRule() {
        super(
                "book_veinminer",   // Item ID
                0.01,           // default 1% base chance
                0.005,          // default +0.5% per Looting level
                0.005           // default +0.5% per Luck level
        );

        registerMob("undead_miner_mob",          0.01,  0.01,  0.01,  true);
        registerMob("zombified_excavator_mob",          0.01,  0.005,  0.005,  true);
        registerMob("cave_dweller_mob",          0.01,  0.005,  0.005,  true);
        registerMob("inner_earth_cannibal_mob",          0.01,  0.01,  0.01,  true);
        registerMob("husked_digger_mob",          0.01,  0.01,  0.01,  true);
    }
}
