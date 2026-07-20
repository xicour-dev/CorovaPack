package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Trinkets;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Swift Strike trinket.
 */
public class SwiftStrikeLootRule extends AbstractItemLootRule {

    public SwiftStrikeLootRule() {
        super("swiftstrike", 0.01, 0.005, 0.005);

        registerMob("reaper",          0.01, 0.005, 0.005, true);
        registerMob("ice_viking",       0.05, 0.015, 0.015, true);
        registerMob("potent_bogged",   0.04, 0.015, 0.015, true);
        registerMob("sparringskeleton", 0.03, 0.01,  0.01,  true);
        registerMob("risenjuggernaut", 0.06, 0.02,  0.02,  true);
        registerMob("dead_thor",        0.06, 0.02,  0.02,  true);
        registerMob("rabid_wolf_mob",       0.06, 0.02,  0.02,  true);
        registerMob("neptune_mob",         0.06, 0.02,  0.02,  true);
        registerMob("corovacore_unknownknight",   0.06, 0.02,  0.02,  true);
        registerMob("undead_pvper_mob",       0.06, 0.02,  0.02,  true);
    }
}