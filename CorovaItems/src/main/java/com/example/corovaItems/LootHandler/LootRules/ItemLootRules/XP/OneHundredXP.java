package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.XP;

public class OneHundredXP extends AbstractXPLootRule {

    public OneHundredXP() {
        super(100);
        registerMob("lost_merchant_boss");
        registerMob("BJ_Boss");

    }
}