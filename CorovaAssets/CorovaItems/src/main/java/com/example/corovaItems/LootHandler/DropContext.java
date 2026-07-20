package com.example.corovaItems.LootHandler;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Context object containing all information about a mob death and drop calculation.
 *
 * Used by both DropHandler and LootListener systems.
 */
public class DropContext {
    private final LivingEntity mob;
    private final Player killer;
    private final ItemStack weapon;
    private final int looting;
    private final int luck;
    private final int tier;

    /**
     * Create a new drop context.
     *
     * @param mob The entity that died
     * @param killer The player who killed it (can be null)
     * @param weapon The weapon used (can be null)
     * @param looting Looting enchantment level
     * @param luck Luck potion effect level
     * @param tier Player tier from TierSystem
     */
    public DropContext(LivingEntity mob, Player killer, ItemStack weapon, int looting, int luck, int tier) {
        this.mob = mob;
        this.killer = killer;
        this.weapon = weapon;
        this.looting = looting;
        this.luck = luck;
        this.tier = tier;
    }

    public LivingEntity getMob() {
        return mob;
    }

    public Player getKiller() {
        return killer;
    }

    public ItemStack getWeapon() {
        return weapon;
    }

    public int getLooting() {
        return looting;
    }

    public int getLuck() {
        return luck;
    }

    public int getTier() {
        return tier;
    }

    /**
     * Roll for a drop chance.
     *
     * @param chance Probability between 0.0 and 1.0
     * @return true if the roll succeeds
     */
    public boolean roll(double chance) {
        return ThreadLocalRandom.current().nextDouble() < chance;
    }
}