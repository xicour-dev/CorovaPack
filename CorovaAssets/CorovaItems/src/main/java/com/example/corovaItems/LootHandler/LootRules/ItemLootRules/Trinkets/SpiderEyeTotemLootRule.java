package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Trinkets;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Spider Eye Totem trinket.
 */
public class SpiderEyeTotemLootRule extends AbstractItemLootRule {

    public SpiderEyeTotemLootRule() {
        super("spidereyetotem", 0.015, 0.015, 0.015);

        registerMob("corovamobs_GJ_GuardianJockey", 0.015, 0.015, 0.015, true);
        registerMob("hunter_spider_mob",            0.015, 0.015, 0.015, true);
        registerMob("mother_spider_mob",            0.015, 0.015, 0.015, true);
        registerMob("web_slinger_mob",                   0.015, 0.015, 0.015, true);
    }
}