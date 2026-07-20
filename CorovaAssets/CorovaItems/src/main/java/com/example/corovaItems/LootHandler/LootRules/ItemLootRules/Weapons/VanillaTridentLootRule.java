package com.example.corovaItems.LootHandler.LootRules.ItemLootRules.Weapons;

import com.example.corovaItems.LootHandler.AbstractItemLootRule;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Drops a vanilla Trident from Drowned.
 *
 * Default Drop Chance Configuration:
 * - Base Chance:    3%    (0.03)
 * - Looting Bonus: +1%   per level (0.01)
 * - Luck Bonus:    +1%   per level (0.01)
 *
 * DROPLIMITER ON  → competes in the single "limited" drop slot per death.
 * DROPLIMITER OFF → always drops when its roll succeeds.
 */
public class VanillaTridentLootRule extends AbstractItemLootRule {

    public VanillaTridentLootRule() {
        super(
                null,   // No ItemManager item — buildItemStack() is overridden below
                0.03,
                0.01,
                0.01
        );

        //              mob identifier   base   loot   luck   DROPLIMITER
        registerMob("drowned",           0.01,  0.01,  0.01,  false);  // DROPLIMITER: ON
    }

    @Override
    public ItemStack buildItemStack() {
        return new ItemStack(Material.TRIDENT);
    }
}