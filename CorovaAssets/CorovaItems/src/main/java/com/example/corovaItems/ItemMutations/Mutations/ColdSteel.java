package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ColdSteel implements Mutation, Mutation.BuildUpMutation {

    private final Map<UUID, Integer> hitCounter = new HashMap<>();

    public ColdSteel(MutationManager manager) {}

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.DEBUFF, MutationCategory.INCREMENTAL);
    }

    @Override
    public String getColor() {
        return "#B0C4DE";
    }

    @Override
    public String getName() {
        return "Cold Steel";
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
            desc.add(ChatColor.GRAY + "Pierce armor and shields after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "Ignores 10% of the target's armor.");
            desc.add(ChatColor.GRAY + "Deals damage through blocking shields.");
            desc.add(ChatColor.GRAY + "Adds +655 Durability.");
            desc.add(ChatColor.DARK_GRAY + "Iron weapons only.");
        } else {
            desc.add(ChatColor.GRAY + "Pierce armor and shields after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "Ignores 20% of the target's armor.");
            desc.add(ChatColor.GRAY + "Deals damage through blocking shields.");
            desc.add(ChatColor.GRAY + "Adds +1311 Durability.");
            desc.add(ChatColor.DARK_GRAY + "Iron weapons only.");
        }
        return desc;
    }

    @Override
    public List<String> getLore(int level, ItemStack item) {
        return getLore(level);
    }

    @Override
    public int getDurabilityBonus(int level) {
        if (level == 1) return 655;
        if (level == 2) return 1311;
        return 0;
    }

    @Override
    public double getSynergyMultiplier(int level) {
        return 0.5 * level;
    }

    @Override
    public MutationType getType() {
        return MutationType.COLD_STEEL;
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
        double piercePercent = (level == 1) ? 0.10 : 0.20;
        if (damager instanceof Player player) {
            piercePercent = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, piercePercent, "duration");
        }

        // The ARMOR modifier is a negative value representing damage absorbed by armor.
        // We reduce (i.e. make less negative) that reduction by the pierce percentage,
        // so a fraction of what armor would have blocked passes through instead.
        if (event.isApplicable(DamageModifier.ARMOR)) {
            double armorReduction = event.getDamage(DamageModifier.ARMOR); // negative or zero
            double pierced = armorReduction * piercePercent; // positive fraction to add back
            event.setDamage(DamageModifier.ARMOR, armorReduction - pierced);
        }

        // Nullify the BLOCKING modifier entirely so damage passes through a raised shield.
        // Also put the shield on cooldown so the victim cannot immediately re-block.
        if (victim instanceof Player victimPlayer && victimPlayer.isBlocking()) {
            if (event.isApplicable(DamageModifier.BLOCKING)) {
                event.setDamage(DamageModifier.BLOCKING, 0.0);
            }
            victimPlayer.setCooldown(Material.SHIELD, (level == 1) ? 40 : 60);
            if (damager instanceof Player) {
                ((Player) damager).sendActionBar(ChatColor.AQUA + "Your blade cuts through " + victimPlayer.getName() + "'s shield!");
            }
        } else if (damager instanceof Player) {
            ((Player) damager).sendActionBar(ChatColor.AQUA + "Your blade cuts through the target's armor!");
        }
    }

    @Override
    public void onProcSynergy(LivingEntity damager, LivingEntity victim, int level) {
        // Synergy: apply a smaller armor pierce (half of normal) as bonus damage
        if (!com.example.corovaItems.Enchantments.EnchantBooks.BoomerangBook.IS_BOOMERANG_HIT.get()) {
            double synergyPierce = (level == 1) ? 0.05 : 0.10;
            // Approximate the synergy pierce as flat bonus damage scaled by synergy multiplier
            double bonusDamage = getSynergyMultiplier(level) * synergyPierce * 10.0;
            victim.damage(bonusDamage, damager);
        }
    }
}