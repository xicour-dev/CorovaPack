package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Weapons;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Eggsploding Egg weapon.
 *
 * Default Drop Chance Configuration:
 * - Base Chance:    1%    (0.01)
 * - Looting Bonus: +0.5% per level (0.005)
 * - Luck Bonus:    +0.5% per level (0.005)
 *
 * DROPLIMITER ON  → competes in the single "limited" drop slot per death.
 * DROPLIMITER OFF → always drops when its roll succeeds.
 */
public class EggsplodingEggLootRule extends AbstractItemLootRule {

    public EggsplodingEggLootRule() {
        super(
                "eggsplodingegg",
                0.01,
                0.005,
                0.005
        );

        //              mob identifier   base   loot    luck    DROPLIMITER
        registerMob("cluster_ghast_mob",          0.01,  0.005,  0.005,  false);  // DROPLIMITER: ON
        registerMob("nuclear_creeper_mob",          0.01,  0.005,  0.005,  false);  // DROPLIMITER: ON
        registerMob("em_charged_creeper_mob",          0.01,  0.005,  0.005,  false);  // DROPLIMITER: ON
        registerMob("grenade_creeper_mob",          0.01,  0.005,  0.005,  false);  // DROPLIMITER: ON
        registerMob("incendiary_creeper_mob",          0.01,  0.005,  0.005,  false);  // DROPLIMITER: ON
        registerMob("undead_pvper_mob",          0.01,  0.005,  0.005,  false);  // DROPLIMITER: ON
        registerMob("corovacore_unknownknight",          0.01,  0.005,  0.005,  false);  // DROPLIMITER: ON
        registerMob("withering_inferno",          0.01,  0.005,  0.005,  false);  // DROPLIMITER: ON
    }
}