package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ArmorTrims.PlayerTrimProfile;
import com.example.corovaItems.ArmorTrims.TrimCalculator;
import com.example.corovaItems.ArmorTrims.TrimManager;
import com.example.corovaItems.ItemMutations.*;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

import org.bukkit.event.player.PlayerQuitEvent;

public class Dice implements Mutation, Mutation.BuildUpMutation {
    private final JavaPlugin plugin;
    private final Map<UUID, Integer> hitCounter = new HashMap<>();
    private final Map<UUID, BukkitTask> activeTasks = new HashMap<>();
    private final NamespacedKey speedKey;
    private final NamespacedKey knockbackKey;

    public Dice(JavaPlugin plugin) {
        this.plugin = plugin;
        this.speedKey = new NamespacedKey(plugin, "dice_speed_boost");
        this.knockbackKey = new NamespacedKey(plugin, "dice_knockback_reduction");
    }

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.BURST, MutationCategory.INCREMENTAL);
    }

    @Override
    public String getColor() {
        return "#FFD700";
    }

    public String getName() {
        return "Dice";
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
            desc.add(ChatColor.GRAY + "Boost attack speed to 10 for 1s after " + hits + " hits");
            desc.add(ChatColor.GRAY + "but reduces knockback dealt by 90%.");
        } else {
            desc.add(ChatColor.GRAY + "Boost attack speed to 10 for 1.5s after " + hits + " hits");
            desc.add(ChatColor.GRAY + "but reduces knockback dealt by 90%.");
        }
        return desc;
    }

    public MutationType getType() {
        return MutationType.DICE;
    }


    private int getRequiredHits(int level, ItemStack item, LivingEntity user, double thresholdReduction) {
        int baseThreshold = (level == 1) ? 15 : 10;
        return Math.max(1, (int) Math.round(baseThreshold * (1.0 - thresholdReduction)));
    }

    @Override
    public boolean incrementAndCheck(UUID damagerId, LivingEntity victim, int level, ItemStack item, LivingEntity user, boolean canProc, double thresholdReduction) {
        if (MutationUtils.isFriendly(user, victim)) return false;
        int required = getRequiredHits(level, item, user, thresholdReduction);
        int count = hitCounter.getOrDefault(damagerId, 0) + 1;
        if (count >= required) {
            if (canProc && !activeTasks.containsKey(user.getUniqueId())) {
                hitCounter.put(damagerId, 0);
                return true;
            }
            hitCounter.put(damagerId, required);
            return false;
        }
        hitCounter.put(damagerId, count);
        return false;
    }

    public void onProc(LivingEntity damager, LivingEntity victim, int level, EntityDamageByEntityEvent event) {
        if (!(damager instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        if (activeTasks.containsKey(uuid)) {
            return;
        }

        applyBoost(player);
        player.sendActionBar(ChatColor.GOLD + "The dice roll in your favor! Your strikes quicken.");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.5f);
        victim.getWorld().spawnParticle(Particle.ENCHANT, victim.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);

        long duration = level == 1 ? 20L : 30L;
        if (damager instanceof Player) {
            duration = (long) MutationManager.getInstance().getSynergyHandler().applyTrimAmplification((Player) damager, this, (double) duration, "window");
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            removeBoost(player);
            activeTasks.remove(uuid);
        }, duration);

        activeTasks.put(uuid, task);
    }

    public void onProcSynergy(LivingEntity damager, LivingEntity victim, int level) {
        onProc(damager, victim, level, null);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        hitCounter.remove(event.getPlayer().getUniqueId());
        cleanup(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        cleanup(event.getEntity());
    }

    private void cleanup(Player player) {
        UUID uuid = player.getUniqueId();
        if (activeTasks.containsKey(uuid)) {
            activeTasks.get(uuid).cancel();
            activeTasks.remove(uuid);
            removeBoost(player);
        }
    }

    private void applyBoost(Player player) {
        AttributeInstance speedAttr = player.getAttribute(Attribute.ATTACK_SPEED);
        if (speedAttr != null) {
            // Scythes have around -2.4 to -2.8 attack speed modifiers (base is 4.0, result ~1.2-1.6)
            // If we add 8.5, we reach ~10.0.
            speedAttr.addModifier(new AttributeModifier(speedKey, 8.5, AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
        }

        AttributeInstance kbAttr = player.getAttribute(Attribute.ATTACK_KNOCKBACK);
        if (kbAttr != null) {
            kbAttr.addModifier(new AttributeModifier(knockbackKey, -0.9, AttributeModifier.Operation.MULTIPLY_SCALAR_1, org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
        }
    }

    private void removeBoost(Player player) {
        AttributeInstance speedAttr = player.getAttribute(Attribute.ATTACK_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(speedKey);
        }

        AttributeInstance kbAttr = player.getAttribute(Attribute.ATTACK_KNOCKBACK);
        if (kbAttr != null) {
            kbAttr.removeModifier(knockbackKey);
        }
    }

    public double getWeight() {
        return ItemMutations.DICE_WEIGHT;
    }
}
