package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Set;

import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Clobber implements Mutation, Mutation.BuildUpMutation {

    private final Map<UUID, Integer> hitCounter = new HashMap<>();

    public Clobber(MutationManager manager) {}


    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.DEBUFF, MutationCategory.INCREMENTAL);
    }

    @Override
    public String getColor() {
        return "#708090";
    }

    @Override
    public String getName() {
        return "Clobber";
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
        int hits = getRequiredHits(level, null, null, 0.0);
        if (level == 1) {
            desc.add(ChatColor.GRAY + "Concuss the target after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "Applies Slowness II and Blindness.");
            desc.add(ChatColor.GRAY + "Adds +715 Durability.");
            desc.add(ChatColor.DARK_GRAY + "Stone weapons only.");
        } else {
            desc.add(ChatColor.GRAY + "Concuss the target after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "Applies Slowness III and Blindness.");
            desc.add(ChatColor.GRAY + "Adds +1430 Durability.");
            desc.add(ChatColor.DARK_GRAY + "Stone weapons only.");
        }
        return desc;
    }

    @Override
    public List<String> getLore(int level, ItemStack item) {
        return getLore(level);
    }

    @Override
    public int getDurabilityBonus(int level) {
        if (level == 1) return 715;
        if (level == 2) return 1430;
        return 0;
    }

    @Override
    public MutationType getType() {
        return MutationType.CLOBBER;
    }

    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }


    private int getRequiredHits(int level, ItemStack item, LivingEntity user, double thresholdReduction) {
        int baseThreshold = (level == 1) ? 8 : 6;
        return Math.max(1, (int) Math.round(baseThreshold * (1.0 - thresholdReduction)));
    }

    @Override
    public boolean incrementAndCheck(UUID damagerId, LivingEntity victim, int level, ItemStack item, LivingEntity user, boolean canProc, double thresholdReduction) {
        if (MutationUtils.isFriendly(user, victim)) return false;
        int required = getRequiredHits(level, item, user, thresholdReduction);
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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hitCounter.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onProc(LivingEntity damager, LivingEntity victim, int level, EntityDamageByEntityEvent event) {
        int slownessAmp = (level == 1) ? 1 : 2; // Slowness II or III
        int duration = (level == 1) ? 40 : 60;
        if (damager instanceof Player player) {
            duration = (int) MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, duration, "duration");
        } // 2s or 3s

        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, slownessAmp));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));

        if (damager instanceof Player) {
            ((Player) damager).sendActionBar(ChatColor.DARK_GRAY + "You clobbered " + victim.getName() + "!");
        }
    }

    @Override
    public void onProcSynergy(LivingEntity damager, LivingEntity victim, int level) {
        // Reduced synergy: 2s Slowness I and Blindness
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
    }
}
