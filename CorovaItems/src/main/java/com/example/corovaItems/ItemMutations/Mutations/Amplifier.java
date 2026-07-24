package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.EventHandler;

import java.util.*;

public class Amplifier implements Mutation, Mutation.BuildUpMutation {

    private final MutationManager mutationManager;
    private final Map<UUID, Integer> hitCounter = new HashMap<>();
    private final Map<UUID, Double> storedDamageMap = new HashMap<>();

    public Amplifier(MutationManager mutationManager) {
        this.mutationManager = mutationManager;
    }

    @Override
    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.ACCUMULATION, MutationCategory.BURST);
    }

    @Override
    public String getColor() {
        return "#00CED1";
    }

    @Override
    public String getName() {
        return "Amplifier";
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
        desc.add(ChatColor.GRAY + "Store damage for " + (hits - 1) + " hits.");
        desc.add(ChatColor.GRAY + "Release all stored damage on the " + hits + "th hit.");
        return desc;
    }

    @Override
    public MutationType getType() {
        return MutationType.AMPLIFIER;
    }


    private int getRequiredHits(int level, ItemStack item, LivingEntity user, double thresholdReduction) {
        int baseThreshold = (level == 1) ? 3 : 2;
        return Math.max(2, (int) Math.round(baseThreshold * (1.0 - thresholdReduction))); // Must be at least 2: one to store, one to release
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
        UUID uuid = event.getPlayer().getUniqueId();
        hitCounter.remove(uuid);
        storedDamageMap.remove(uuid);
    }

    @Override
    public void onProc(LivingEntity damager, LivingEntity victim, int level, EntityDamageByEntityEvent event) {
        // Proc = release
        double storedDamage = storedDamageMap.getOrDefault(damager.getUniqueId(), 0.0);
        storedDamageMap.remove(damager.getUniqueId());

        if (storedDamage > 0) {
            storedDamage *= 0.5; // Only release 50% of stored damage
            if (damager instanceof Player player) {
                storedDamage = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, storedDamage, "accumulation");
            }
            event.setDamage(event.getDamage() + storedDamage);

            victim.getWorld().spawnParticle(Particle.EXPLOSION, victim.getLocation().add(0, 1, 0), 1);
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
            if (damager instanceof Player player) {
                player.sendActionBar("§b§lAmplifier §7has released! §b(+" + String.format("%.1f", storedDamage) + " Damage)");
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
            }
        }
    }

    @Override
    public void onNoProc(LivingEntity damager, LivingEntity victim, int level, EntityDamageByEntityEvent event) {
        // NoProc = store
        double damageToStore = event.getDamage();
        storedDamageMap.merge(damager.getUniqueId(), damageToStore, Double::sum);

        victim.getWorld().spawnParticle(Particle.WITCH, victim.getLocation().add(0, 1, 0), 5, 0.2, 0.2, 0.2, 0.01);
        if (damager instanceof Player player) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.5f + (float) Math.random() * 0.5f);
        }
    }

    @Override
    public void onProcSynergy(LivingEntity damager, LivingEntity victim, int level) {
        // Proc = release
        double storedDamage = storedDamageMap.getOrDefault(damager.getUniqueId(), 0.0);
        storedDamageMap.remove(damager.getUniqueId());

        if (storedDamage > 0) {
            storedDamage *= 0.5;
            victim.damage(storedDamage, damager);

            victim.getWorld().spawnParticle(Particle.EXPLOSION, victim.getLocation().add(0, 1, 0), 1);
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
            if (damager instanceof Player player) {
                player.sendActionBar("§b§lAmplifier §7has released! §b(+" + String.format("%.1f", storedDamage) + " Damage)");
                player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
            }
        }
    }

    @Override
    public void onNoProcSynergy(LivingEntity damager, LivingEntity victim, int level) {
        // NoProc = store
        // For synergies like Boomerang, we'll store a fixed amount based on weapon damage if possible
        ItemStack weapon = damager.getEquipment() != null ? damager.getEquipment().getItemInMainHand() : null;
        double baseDamage = com.example.corovaItems.ItemMutations.MutationUtils.getWeaponDamage(weapon, damager, victim);

        storedDamageMap.merge(damager.getUniqueId(), baseDamage, Double::sum);
        victim.getWorld().spawnParticle(Particle.WITCH, victim.getLocation().add(0, 1, 0), 5, 0.2, 0.2, 0.2, 0.01);
    }


    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.AMPLIFIER_WEIGHT;
    }
}
