package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import com.example.corovaItems.MageSystem.ManaManager;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.List;
import java.util.Set;

/**
 * Soul Siphon — weapon mutation for wands and rods.
 *
 * On a successful hit against a mob (non-player), restores a percentage
 * of the attacker's maximum mana:
 *   Level I  → 10% of max mana
 *   Level II → 20% of max mana
 *
 * Does NOT trigger on player targets.
 */
public class SoulSiphon implements Mutation {

    public SoulSiphon(MutationManager mutationManager) {}

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.ENCHANT_SYNERGY, MutationCategory.RECOVERY);
    }

    @Override
    public String getColor() {
        return "#8A2BE2";
    }

    @Override
    public String getName() { return "Soul Siphon"; }

    @Override
    public int getMaxLevel() { return 2; }

    @Override
    public MutationType getType() { return MutationType.SOUL_SIPHON; }

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
            desc.add(ChatColor.GRAY + "Restore 20% mana on mob hit.");
        } else {
            desc.add(ChatColor.GRAY + "Restore 10% mana on mob hit.");
        }
        return desc;
    }

    /** Always procs when a valid hit lands (filtered in onProc). */

    @Override
    public void onProc(LivingEntity damager, LivingEntity victim, int level,
                       EntityDamageByEntityEvent event) {
        // Only trigger on mob hits, not players
        if (victim instanceof Player) return;
        if (!(damager instanceof Player attacker)) return;

        ManaManager mana = ManaManager.getInstance();
        if (mana == null) return;

        double siphonPercent = (level >= 2) ? 0.10 : 0.05;
        double amount = mana.getMaxMana(attacker) * siphonPercent;
        amount = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(attacker, this, amount, "recovery");
        mana.restoreMana(attacker, amount);

        // Visual feedback — soul particles around the attacker
        attacker.getWorld().spawnParticle(
                Particle.SOUL,
                attacker.getLocation().add(0, 1, 0),
                5, 0.3, 0.3, 0.3, 0.02);
        attacker.playSound(attacker.getLocation(),
                Sound.ENTITY_BLAZE_HURT, 0.4f, 1.8f);
    }
}