package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.BowDamageScaling;
import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.EventHandler;

import java.util.*;

public class Shatter implements Mutation, Mutation.BuildUpMutation {

    private final Map<UUID, Integer> hitCounter = new HashMap<>();

    public Shatter(MutationManager manager) {}

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.BURST, MutationCategory.INCREMENTAL);
    }

    @Override
    public String getColor() {
        return "#E0FFFF";
    }

    @Override
    public String getName() {
        return "Shatter";
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
            desc.add(ChatColor.GRAY + "Shatter your blade after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "Deals AOE damage at the cost of durability.");
            desc.add(ChatColor.DARK_GRAY + "Diamond weapons only.");
        } else {
            desc.add(ChatColor.GRAY + "Shatter your blade after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "Deals higher AOE damage at the cost of durability.");
            desc.add(ChatColor.DARK_GRAY + "Diamond weapons only.");
        }
        return desc;
    }

    @Override
    public double getSynergyMultiplier(int level) {
        return 0.5 * level;
    }

    @Override
    public MutationType getType() {
        return MutationType.SHATTER;
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
        double mainBonus = (level == 1) ? 2.0 : 3.5;
        double aoeDamage = (level == 1) ? 1.0 : 2.0;
        int durabilityCost = (level == 1) ? 10 : 20;

        if (damager instanceof Player player) {
            mainBonus = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, mainBonus, "window");
            aoeDamage = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, aoeDamage, "window");
        }
        event.setDamage(event.getDamage() + mainBonus);

        // AOE
        for (Entity entity : victim.getNearbyEntities(3, 3, 3)) {
            if (entity instanceof LivingEntity && !entity.equals(damager) && !entity.equals(victim)) {
                double finalAoe = aoeDamage;
                ItemStack weapon = (damager.getEquipment() != null) ? damager.getEquipment().getItemInMainHand() : null;

                if (weapon != null) {
                    // Include Sharpness
                    int sharp = weapon.getEnchantmentLevel(Enchantment.SHARPNESS);
                    if (sharp > 0) finalAoe += (0.5 * sharp + 0.5);

                    // Include Smite/Bane for AOE
                    if (BowDamageScaling.isUndead((LivingEntity)entity)) {
                        int smite = weapon.getEnchantmentLevel(Enchantment.SMITE);
                        if (smite > 0) finalAoe += 2.5 * smite;
                    } else if (BowDamageScaling.isArthropod((LivingEntity)entity)) {
                        int bane = weapon.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS);
                        if (bane > 0) finalAoe += 2.5 * bane;
                    }
                }

                ((LivingEntity) entity).damage(finalAoe, damager);
                entity.getWorld().spawnParticle(Particle.CRIT, entity.getLocation(), 5);
            }
        }

        // Durability cost
        ItemStack item = damager.getEquipment() != null ? damager.getEquipment().getItemInMainHand() : null;
        if (item != null && item.hasItemMeta() && item.getItemMeta() instanceof Damageable) {
            Damageable meta = (Damageable) item.getItemMeta();
            meta.setDamage(meta.getDamage() + durabilityCost);
            item.setItemMeta(meta);
        }

        victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 0.8f);
        victim.getWorld().spawnParticle(Particle.BLOCK, victim.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, org.bukkit.Material.DIAMOND_BLOCK.createBlockData());

        if (damager instanceof Player) {
            ((Player) damager).sendActionBar(ChatColor.AQUA + "Your blade shatters, dealing massive damage!");
        }
    }

    @Override
    public void onProcSynergy(LivingEntity damager, LivingEntity victim, int level) {
        // Reduced synergy: smaller AOE, no durability cost
        double aoeDamage = (level == 1) ? 1.0 : 2.0;
        for (Entity entity : victim.getNearbyEntities(2, 2, 2)) {
            if (entity instanceof LivingEntity && !entity.equals(damager) && !entity.equals(victim)) {
                ((LivingEntity) entity).damage(aoeDamage, damager);
                entity.getWorld().spawnParticle(Particle.CRIT, entity.getLocation(), 3);
            }
        }
        victim.getWorld().spawnParticle(Particle.BLOCK, victim.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, org.bukkit.Material.DIAMOND_BLOCK.createBlockData());
    }
}
