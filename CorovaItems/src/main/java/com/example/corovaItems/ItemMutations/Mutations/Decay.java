package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Decay implements Mutation, Mutation.BuildUpMutation {

    private final JavaPlugin plugin;
    private final Map<UUID, Integer> hitCounter = new HashMap<>();

    public Decay(MutationManager manager, JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.DEBUFF, MutationCategory.SUSTAINED, MutationCategory.HEALTH_SCALE, MutationCategory.INCREMENTAL);
    }

    @Override
    public String getColor() {
        return "#4B0082";
    }

    @Override
    public String getName() {
        return "Decay";
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
            desc.add(ChatColor.GRAY + "Reduce target's max health after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "Reduces max health by 5% for 8s.");
        } else {
            desc.add(ChatColor.GRAY + "Reduce target's max health after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "Reduces max health by 10% for 8s.");
        }
        return desc;
    }

    @Override
    public List<String> getLore(int level, ItemStack item) {
        return getLore(level);
    }

    @Override
    public double getSynergyMultiplier(int level) {
        return 0.5 * level;
    }

    @Override
    public MutationType getType() {
        return MutationType.DECAY;
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
        AttributeInstance maxHealth = victim.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;

        double reduction = (level == 1) ? 0.05 : 0.10;
        if (damager instanceof Player player) {
            com.example.corovaItems.ArmorTrims.PlayerTrimProfile profile = com.example.corovaItems.ArmorTrims.TrimManager.getInstance().getProfile(player);
            reduction += com.example.corovaItems.ArmorTrims.TrimCalculator.getAmplification(getCategories(), profile, "threshold");
        }
        double amount = maxHealth.getBaseValue() * -reduction;

        NamespacedKey key = new NamespacedKey(plugin, "decay_health_reduction_" + UUID.randomUUID());
        AttributeModifier modifier = new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.ANY);
        maxHealth.addModifier(modifier);

        if (damager instanceof Player) {
            ((Player) damager).sendActionBar(ChatColor.DARK_RED + "The target's vitality begins to decay!");
        }

        ItemStack weapon = (damager.getEquipment() != null) ? damager.getEquipment().getItemInMainHand() : null;
        MutationManager.getInstance().getSynergyHandler().handleMutationSynergy(getType(), damager, victim, weapon, level);

        new BukkitRunnable() {
            @Override
            public void run() {
                maxHealth.removeModifier(modifier);
                // The current health will stay at its absolute value when the max health increases back.
                // This satisfies the "not regen health automatically" requirement.
            }
        }.runTaskLater(plugin, (long) MutationManager.getInstance().getSynergyHandler().applyTrimAmplification((Player)damager, this, 160.0, "duration")); // 8 seconds
    }

    @Override
    public void onProcSynergy(LivingEntity damager, LivingEntity victim, int level) {
        AttributeInstance maxHealth = victim.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) return;

        double reduction = (level == 1) ? 0.05 : 0.10;
        double amount = maxHealth.getBaseValue() * -reduction;

        NamespacedKey key = new NamespacedKey(plugin, "decay_synergy_" + UUID.randomUUID());
        AttributeModifier modifier = new AttributeModifier(key, amount, AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.ANY);
        maxHealth.addModifier(modifier);

        new BukkitRunnable() {
            @Override
            public void run() {
                maxHealth.removeModifier(modifier);
            }
        }.runTaskLater(plugin, (long) MutationManager.getInstance().getSynergyHandler().applyTrimAmplification((Player)damager, this, 160.0, "duration")); // 8 seconds
    }
}
