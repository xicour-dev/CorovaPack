package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SkullCrush implements Mutation {

    // Mirrors CriticalBook's formula: recover weapon base (BASE / 1.5),
    // then add a flat % of that base. L1 = +2%, L2 = +4%.
    private static final double BONUS_PER_LEVEL = 0.02;

    private final MutationManager mutationManager;

    public SkullCrush(MutationManager mutationManager) {
        this.mutationManager = mutationManager;
    }

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.BURST);
    }

    @Override
    public String getColor() {
        return "#FF4500";
    }

    public String getName() {
        return "Skull Crush";
    }

    public int getMaxLevel() {
        return 2;
    }

    public List<String> getLore(int level) {
        List<String> lore = new ArrayList<>();
        ChatColor color = ChatColor.of(getColor());
        lore.add(color + getName() + " " + MutationUtils.toRoman(level));
        return lore;
    }

    @Override
    public List<String> getDescription(int level) {
        List<String> desc = new ArrayList<>();
        if (level == 1) {
            desc.add(ChatColor.GRAY + "Axe critical hits deal +2% weapon base damage.");
        } else {
            desc.add(ChatColor.GRAY + "Axe critical hits deal +4% weapon base damage.");
        }
        return desc;
    }

    public MutationType getType() {
        return MutationType.SKULL_CRUSH;
    }

    @Override
    public boolean isCompatible(ItemStack item) {
        return true;
    }


    public void onProc(LivingEntity damager, LivingEntity victim, int level, EntityDamageByEntityEvent event) {
        if (!(damager instanceof Player player)) return;

        if (isCriticalHit(player)) {
            // Same approach as CriticalBook: BASE already has vanilla 1.5x crit applied,
            // so divide back out to get the raw weapon base, then add a flat % of that.
            // L1 = +4% of weapon base, L2 = +8% of weapon base.
            double weaponBase = event.getDamage(DamageModifier.BASE) / 1.5;
            double bonusPct   = level * BONUS_PER_LEVEL;

            // Apply trim amplification if any (e.g. Quartz intersection via BURST category)
            bonusPct = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, bonusPct, "window");

            double flatBonus = weaponBase * bonusPct;
            event.setDamage(DamageModifier.BASE, event.getDamage(DamageModifier.BASE) + flatBonus);
        }
    }

    private boolean isCriticalHit(Player player) {
        return player.getFallDistance() > 0
                && !player.isOnGround()
                && !player.hasPotionEffect(PotionEffectType.BLINDNESS)
                && player.getAttackCooldown() >= 1.0f;
    }

    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.SKULL_CRUSH_WEIGHT;
    }
}