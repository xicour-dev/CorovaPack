package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.XP;

public class FiftyXP extends AbstractXPLootRule {

    public FiftyXP() {
        super(50);
        registerMob("corovacore_endergiant");
        registerMob("the_scorched_mob");

    }
}