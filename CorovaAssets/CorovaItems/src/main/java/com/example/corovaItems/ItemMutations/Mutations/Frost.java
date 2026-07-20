package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import com.example.corovateams.CorovaTeam;
import com.example.corovateams.CorovaTeams;
import com.example.corovateams.TeamManager;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.EventHandler;

import java.util.*;

public class Frost implements Mutation, Mutation.BuildUpMutation {

    private final Map<UUID, Integer> hitCounter = new HashMap<>();

    public Frost(MutationManager manager) {}

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.DEBUFF, MutationCategory.INCREMENTAL);
    }

    @Override
    public String getColor() {
        return "#00BFFF";
    }

    @Override
    public String getName() {
        return "Frost";
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
            desc.add(ChatColor.GRAY + "Freeze the target after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "Applies Slowness III and 1.5x weapon damage.");
        } else {
            desc.add(ChatColor.GRAY + "Freeze the target after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "Applies Slowness III and 1.5x weapon damage.");
        }
        return desc;
    }

    @Override
    public List<String> getLore(int level, ItemStack item) {
        return getLore(level);
    }

    @Override
    public double getSynergyMultiplier(int level) {
        return 1.5;
    }

    @Override
    public MutationType getType() {
        return MutationType.FROST;
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
    public void onPlayerQuit(PlayerQuitEvent event) {
        hitCounter.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public void onProc(LivingEntity damager, LivingEntity victim, int level, EntityDamageByEntityEvent event) {

        // Slowness III for 3 seconds (60 ticks)
        int duration = 60;
        double multiplier = 1.5;

        if (damager instanceof Player player) {
            duration = (int) MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, duration, "duration");
            multiplier = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, multiplier, "window");
        }

        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 2));

        // triple weapons damage
        event.setDamage(event.getDamage() * multiplier);

        // Powdered snow effect visual
        victim.setFreezeTicks(100);

        // Ice block breaking particles
        victim.getWorld().spawnParticle(Particle.BLOCK, victim.getLocation().add(0, 1, 0), 40, 0.5, 0.5, 0.5, Material.ICE.createBlockData());
        victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5);

        if (damager instanceof Player) {
            ((Player) damager).sendActionBar(ChatColor.AQUA + "You froze " + victim.getName() + "!");
        }
    }

    @Override
    public void onProcSynergy(LivingEntity damager, LivingEntity victim, int level) {
        if (MutationUtils.isFriendly(damager, victim)) return;

        // Reduced duration for synergy: 2 seconds Slowness II
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1));
        victim.setFreezeTicks(50);

        victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5);
    }
}
