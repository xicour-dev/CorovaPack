package com.example.corovaItems.LootHandler;

public class ChanceUtil {
    /**
     * Apply looting and luck bonuses to a base drop chance.
     *
     * @param baseChance   The base probability (0.0–1.0)
     * @param context      The drop context containing pre-calculated looting and luck levels
     * @param lootingBonus The added chance per looting level
     * @param luckBonus    The added chance per luck level
     * @return The final calculated chance
     */
    public static double applyLootingAndLuck(double baseChance, DropContext context, double lootingBonus, double luckBonus) {
        double chance = baseChance;
        // Use the pre-calculated looting and luck from the context.
        // These are calculated in DropHandler at the moment of death,
        // which is safer and more efficient than re-fetching from the player.
        chance += context.getLooting() * lootingBonus;
        chance += context.getLuck() * luckBonus;
        return chance;
    }
}
