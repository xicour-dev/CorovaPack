package com.example.corovaItems.LootHandler.LootRules.ItemLootRules;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Drops a Lucky Mushroom from hostile mobs.
 *
 * Default Drop Chance Configuration:
 * - Base Chance:    0.25%  (0.0025)
 * - Looting Bonus: +0.1%  per level (0.001)
 * - Luck Bonus:    +0.1%  per level (0.001)
 *
 * DROPLIMITER OFF → always drops when its roll succeeds (unlimited).
 */
public class LuckyMushroomLootRule extends AbstractItemLootRule {

    public LuckyMushroomLootRule() {
        super(
                "LuckyMushroom",
                0.0025,
                0.001,
                0.001
        );

        //              mob identifier      base     loot    luck    DROPLIMITER
        registerMob("zombie",               0.0025,  0.001,  0.001,  false);
        registerMob("skeleton",             0.0025,  0.001,  0.001,  false);
        registerMob("creeper",              0.0025,  0.001,  0.001,  false);
        registerMob("spider",               0.0025,  0.001,  0.001,  false);
        registerMob("cave_spider",          0.0025,  0.001,  0.001,  false);
        registerMob("enderman",             0.0025,  0.001,  0.001,  false);
        registerMob("blaze",                0.0025,  0.001,  0.001,  false);
        registerMob("witch",                0.0025,  0.001,  0.001,  false);
        registerMob("phantom",              0.0025,  0.001,  0.001,  false);
        registerMob("slime",                0.0025,  0.001,  0.001,  false);
        registerMob("magma_cube",           0.0025,  0.001,  0.001,  false);
        registerMob("ghast",                0.0025,  0.001,  0.001,  false);
        registerMob("wither_skeleton",      0.0025,  0.001,  0.001,  false);
        registerMob("piglin_brute",         0.0025,  0.001,  0.001,  false);
        registerMob("zombified_piglin",     0.0025,  0.001,  0.001,  false);
        registerMob("hoglin",               0.0025,  0.001,  0.001,  false);
        registerMob("zoglin",               0.0025,  0.001,  0.001,  false);
        registerMob("drowned",              0.0025,  0.001,  0.001,  false);
        registerMob("husk",                 0.0025,  0.001,  0.001,  false);
        registerMob("stray",                0.0025,  0.001,  0.001,  false);
        registerMob("vindicator",           0.0025,  0.001,  0.001,  false);
        registerMob("evoker",               0.0025,  0.001,  0.001,  false);
        registerMob("pillager",             0.0025,  0.001,  0.001,  false);
        registerMob("ravager",              0.0025,  0.001,  0.001,  false);
        registerMob("vex",                  0.0025,  0.001,  0.001,  false);
        registerMob("guardian",             0.0025,  0.001,  0.001,  false);
        registerMob("elder_guardian",       0.0025,  0.001,  0.001,  false);
        registerMob("shulker",              0.0025,  0.001,  0.001,  false);
        registerMob("silverfish",           0.0025,  0.001,  0.001,  false);
        registerMob("endermite",            0.0025,  0.001,  0.001,  false);
    }
}