package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Trinkets;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Block Reach Extender trinket.
 *
 * Default Drop Chance Configuration:
 * - Base Chance:    1%    (0.01)
 * - Looting Bonus: +0.5% per level (0.005)
 * - Luck Bonus:    +0.5% per level (0.005)
 *
 * DROPLIMITER ON  → competes in the single "limited" drop slot per death.
 * DROPLIMITER OFF → always drops when its roll succeeds.
 */
public class BlockReachExtenderLootRule extends AbstractItemLootRule {

    public BlockReachExtenderLootRule() {
        super(
                "blockreachextender",
                0.01,
                0.005,
                0.005
        );

        //              mob identifier   base   loot    luck    DROPLIMITER
        registerMob("undead_miner_mob",          0.03,  0.02,  0.02,  true);
        registerMob("zombified_excavator_mob",          0.03,  0.02,  0.02,  true);
        registerMob("cave_dweller_mob",           0.03,  0.02,  0.02,  true);
        registerMob("inner_earth_cannibal_mob",           0.03,  0.02,  0.02,  true);
        registerMob("husked_digger_mob",           0.03,  0.02,  0.02,  true);
    }
}