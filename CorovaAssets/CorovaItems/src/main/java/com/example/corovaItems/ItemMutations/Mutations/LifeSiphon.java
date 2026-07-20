package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;


import java.util.*;

public class LifeSiphon implements Mutation, Mutation.BuildUpMutation {

    private final Map<UUID, Integer> hitCounter = new HashMap<>();

    public LifeSiphon(MutationManager mutationManager) {}

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.DEFENSIVE, MutationCategory.INCREMENTAL, MutationCategory.RECOVERY);
    }

    @Override
    public String getColor() {
        return "#FF5252";
    }

    @Override
    public String getName() {
        return "Life Siphon";
    }

    @Override
    public int getMaxLevel() {
        return 2;
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
        if (level == 1) {
            desc.add(ChatColor.GRAY + "Heal when attacking.");
            desc.add(ChatColor.GRAY + "Heals 0.25 hearts every 2 hits.");
        } else {
            desc.add(ChatColor.GRAY + "Heal when attacking.");
            desc.add(ChatColor.GRAY + "Heals 0.25 hearts every hit.");
        }
        return desc;
    }

    @Override
    public double getSynergyMultiplier(int level) {
        return 0.5 * level;
    }

    @Override
    public boolean isCompatible(ItemStack item) {
        return MutationUtils.isSword(item)
                || item.getType().name().endsWith("_AXE")
                || item.getType().name().endsWith("_HOE")
                || MutationUtils.isScythe(item)
                || MutationUtils.isBow(item);
    }

    @Override
    public MutationType getType() {
        return MutationType.LIFE_SIPHON;
    }

    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }


    @Override
    public boolean incrementAndCheck(UUID damagerId, LivingEntity victim, int level, ItemStack item, LivingEntity user, boolean canProc, double thresholdReduction) {
        if (MutationUtils.isFriendly(user, victim)) return false;
        int baseRequired = (level == 1) ? 2 : 1;
        int required = Math.max(1, (int) Math.round(baseRequired * (1.0 - thresholdReduction)));
        int count = hitCounter.getOrDefault(damagerId, 0) + 1;
        if (count >= required) {
            if (canProc) {
                hitCounter.put(damagerId, 0);
                return true;
            }
            hitCounter.put(damagerId, required);
            return false;
        }
        hitCounter.put(damagerId, count);
        return false;
    }


    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        hitCounter.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onProc(LivingEntity damager, LivingEntity victim, int level, EntityDamageByEntityEvent event) {
        double heal = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification((Player)damager, this, 0.5, "recovery");
        damager.setHealth(Math.min(damager.getHealth() + heal, damager.getAttribute(Attribute.MAX_HEALTH).getValue()));
    }

    @Override
    public void onProcSynergy(LivingEntity damager, LivingEntity victim, int level) {
        // Reduced synergy: Always heal 0.25 hearts (0.5 hp)
        double heal = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification((Player)damager, this, 0.5, "recovery");
        damager.setHealth(Math.min(damager.getHealth() + heal, damager.getAttribute(Attribute.MAX_HEALTH).getValue()));
    }
}