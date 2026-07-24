package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.*;
import com.example.corovateams.CorovaTeam;
import com.example.corovateams.CorovaTeams;
import com.example.corovateams.TeamManager;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.EventHandler;
import java.util.*;

public class Fear implements Mutation, Mutation.BuildUpMutation {

    private final Map<UUID, Integer> hitCounter = new HashMap<>();

    public Fear(MutationManager manager) {}

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.DEBUFF, MutationCategory.INCREMENTAL);
    }

    @Override
    public String getColor() {
        return "#4B0082";
    }

    public String getName() {
        return "Fear";
    }

    public int getMaxLevel() {
        return 2;
    }

    @Override
    public boolean isCompatible(ItemStack item) {
        return true;
    }

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
            desc.add(ChatColor.GRAY + "Strike fear into targets after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "Applies Mining Fatigue II for 2.5s.");
        } else {
            desc.add(ChatColor.GRAY + "Strike fear into targets after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "Applies Mining Fatigue III for 5s.");
        }
        return desc;
    }

    public MutationType getType() {
        return MutationType.FEAR;
    }

    public double getWeight() {
        return ItemMutations.FEAR_WEIGHT;
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
    public void onPlayerQuit(PlayerQuitEvent event) {
        hitCounter.remove(event.getPlayer().getUniqueId());
    }

    public void onProc(LivingEntity damager, LivingEntity victim, int level, EntityDamageByEntityEvent event) {
        int duration = (level == 1) ? 50 : 100;
        if (damager instanceof Player player) {
            duration = (int) MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, duration, "duration");
        }
        int amplifier = (level == 1) ? 1 : 2;

        victim.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, duration, amplifier));

        victim.getWorld().spawnParticle(Particle.SOUL, victim.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_GHAST_AMBIENT, 1.0f, 0.5f);

        if (damager instanceof Player) {
            ((Player) damager).sendActionBar(ChatColor.DARK_PURPLE + "You struck fear into " + victim.getName() + "!");
        }
    }

    public void onProcSynergy(LivingEntity damager, LivingEntity victim, int level) {
        onProc(damager, victim, level, null);
    }
}
