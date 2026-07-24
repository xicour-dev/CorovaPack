package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.List;
import java.util.Set;

/**
 * Mana Conservation — passive weapon mutation for wands and rods.
 *
 * Reduces the mana cost of every spell cast on the item:
 *   Level I  → -7.5%
 *   Level II → -15%
 *
 * The reduction is applied automatically inside
 * {@link com.example.corovaItems.MageSystem.ManaManager#tryConsumeMana},
 * which reads this mutation from the player's held item.
 * No {@link #onProc} is needed — the effect is entirely passive.
 */
public class ManaConservation implements Mutation {

    public ManaConservation(MutationManager mutationManager) {}

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.ENCHANT_SYNERGY);
    }

    @Override
    public String getColor() {
        return "#00FFFF";
    }

    @Override
    public String getName() { return "Mana Conservation"; }

    @Override
    public int getMaxLevel() { return 2; }

    @Override
    public MutationType getType() { return MutationType.MANA_CONSERVATION; }

    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }

    @Override
    public List<String> getLore(int level) {
        List<String> lore = new java.util.ArrayList<>();
        ChatColor color = ChatColor.of(getColor());
        lore.add(color + getName() + " " + MutationUtils.toRoman(level));
        return lore;
    }

    @Override
    public List<String> getDescription(int level) {
        List<String> desc = new java.util.ArrayList<>();
        if (level >= 2) {
            desc.add(ChatColor.GRAY + "All spells on this weapon cost 10% less mana.");
        } else {
            desc.add(ChatColor.GRAY + "All spells on this weapon cost 5% less mana.");
        }
        return desc;
    }

    /** Passive — no proc. */

    @Override
    public void onProc(LivingEntity damager, LivingEntity victim, int level,
                       EntityDamageByEntityEvent event) {
        // intentionally empty — effect is handled by ManaManager
    }
}