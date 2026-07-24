package com.example.corovaItems.LootHandler.LootRules.ItemLootRules;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;

/**
 * Loot rule for the Enchanted Glistering Melon trinket.
 *
 * Default Drop Chance Configuration:
 * - Base Chance:    1%  (0.01)
 * - Looting Bonus: +1% per level (0.01)
 * - Luck Bonus:    +1% per level (0.01)
 *
 * Per-mob overrides and DropLimiter flags are listed inline.
 * DROPLIMITER ON  → item competes in the single "limited" drop slot per death.
 *                   If multiple ON-items roll successfully, only ONE is kept.
 * DROPLIMITER OFF → item always drops when its roll succeeds, regardless of
 *                   what other items also dropped (does not consume the slot).
 */
public class EnchantedGlisteringMelonLootRule extends AbstractItemLootRule {

    public EnchantedGlisteringMelonLootRule() {
        super(
                "enchantedglisteringmelon",   // Item ID (must match ItemManager registration)
                0.01,                         // default 1% base chance
                0.01,                         // default +1% per Looting level
                0.01                          // default +1% per Luck level
        );

        //                   mob identifier                    base   loot   luck   DROPLIMITER
        registerMob("corovacore_cyclops",                    0.01,  0.01,  0.01,  false);   // DROPLIMITER: ON
        registerMob("ENDER_ARCHER",                          0.01,  0.01,  0.01,  true);   // DROPLIMITER: ON
        registerMob("corovamobs:resentful_enchanted",        0.01,  0.01,  0.01,  true);   // DROPLIMITER: ON  (PDC key)
        registerMob("VJ_Skeleton",                           0.01,  0.01,  0.01,  false);   // DROPLIMITER: ON
        registerMob("reaper",                                0.01,  0.01,  0.01,  true);   // DROPLIMITER: ON
        registerMob("corovacore_witherwizard",               0.01,  0.01,  0.01,  false);   // DROPLIMITER: ON
        registerMob("nuclear_creeper_mob",                        0.01,  0.01,  0.01,  true);   // DROPLIMITER: ON
        registerMob("sparringskeleton",                      0.01,  0.01,  0.01,  true);   // DROPLIMITER: ON
        registerMob("EVJ_Skeleton",                          0.01,  0.01,  0.01,  false);  // DROPLIMITER: OFF
        registerMob("dead_thor",                              0.01,  0.01,  0.01,  false);  // DROPLIMITER: OFF
        registerMob("lost_merchant_boss",                          0.01,  0.01,  0.01,  false);  // DROPLIMITER: OFF
        registerMob("mutant_copper_golem_boss",                   0.01,  0.01,  0.01,  false);  // DROPLIMITER: OFF
        registerMob("neptune_mob",                               0.01,  0.01,  0.01,  false);  // DROPLIMITER: OFF
        registerMob("bull_mob",                                  0.01,  0.01,  0.01,  false);   // DROPLIMITER: ON
        registerMob("cluster_ghast_mob",                       0.01,  0.01,  0.01,  false);   // DROPLIMITER: ON
        registerMob("kamikaze_phantom",                    0.01,  0.01,  0.01,  true);   // DROPLIMITER: ON
        registerMob("supernova_creeper_mob",                      0.01,  0.01,  0.01,  true);   // DROPLIMITER: ON
        registerMob("soul_hound_mob",                             0.01,  0.01,  0.01,  true);   // DROPLIMITER: ON
        registerMob("soul_blaze",                             0.01,  0.01,  0.01,  true);   // DROPLIMITER: ON
        registerMob("whirlwind_fury_base",                             0.01,  0.01,  0.01,  true);   // DROPLIMITER: ON
        registerMob("corovacore_unknownknight",                             0.01,  0.01,  0.01,  false);   // DROPLIMITER: ON
    }
}