package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Enchantments;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Book of Flight enchantment book.
 *
 * Default Drop Chance Configuration:
 * - Base Chance:    1%    (0.01)
 * - Looting Bonus: +0.5% per level (0.005)
 * - Luck Bonus:    +0.5% per level (0.005)
 *
 * Replace "mobid_N" placeholders with the real mob scoreboard tag / PDC key.
 *
 * DROPLIMITER ON  → competes in the single "limited" drop slot per death.
 * DROPLIMITER OFF → always drops when its roll succeeds.
 */
public class FlightBookLootRule extends AbstractItemLootRule {

    public FlightBookLootRule() {
        super(
                "book_flight",   // Item ID (must match ItemManager registration)
                0.01,           // default 1% base chance
                0.005,          // default +0.5% per Looting level
                0.005           // default +0.5% per Luck level
        );

        //              mob identifier   base   loot    luck    DROPLIMITER
        registerMob("strafer_mob",          0.02,  0.02,  0.02,  false);  // DROPLIMITER: ON
        registerMob("kamikaze_phantom",          0.02,  0.02,  0.02,  true);  // DROPLIMITER: ON
        registerMob("laser_phantom_mob",          0.02,  0.02,  0.02,  true);  // DROPLIMITER: ON
        registerMob("cluster_ghast_mob",          0.02,  0.02,  0.02,  true);  // DROPLIMITER: ON
        registerMob("ender_ghast_mob",          0.02,  0.02,  0.02,  false);  // DROPLIMITER: ON
        registerMob("wither_ghast_mob",          0.02,  0.02,  0.02,  true);  // DROPLIMITER: ON
        registerMob("corovacore_largevex",          0.02,  0.02,  0.02,  true);  // DROPLIMITER: ON
    }
}