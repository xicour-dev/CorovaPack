package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Enchantments;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Book of Web Slinger enchantment book.
 */
public class WebSlingBookLootRule extends AbstractItemLootRule {

    public WebSlingBookLootRule() {
        super(
                "book_websling",   // Item ID
                0.01,           // default 1% base chance
                0.005,          // default +0.5% per Looting level
                0.005           // default +0.5% per Luck level
        );

        registerMob("web_slinger_mob",          0.05,  0.01,  0.01,  true);
        registerMob("VJ_Spider",          0.02,  0.02,  0.02,  true);
        registerMob("mobid_3",          0.01,  0.005,  0.005,  true);
    }
}
