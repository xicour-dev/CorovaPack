package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ArmorTrims.PlayerTrimProfile;
import com.example.corovaItems.ArmorTrims.TrimCalculator;
import com.example.corovaItems.ArmorTrims.TrimManager;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Bleed implements Mutation, Mutation.BuildUpMutation, Listener {

    private final MutationManager mutationManager;
    private final Map<UUID, Integer> hitCounter = new HashMap<>();
    private static final Map<UUID, Integer> bleedSynergyLevels = new HashMap<>();

    public Bleed(MutationManager mutationManager) {
        this.mutationManager = mutationManager;
    }

    @Override
    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.BURST, MutationCategory.INCREMENTAL);
    }

    @Override
    public String getColor() {
        return "#FF0000";
    }

    @Override
    public String getName() {
        return "Bleed";
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
            desc.add(ChatColor.GRAY + "Cause bleeding after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "Deals 1.5x weapon damage.");
        } else {
            desc.add(ChatColor.GRAY + "Cause bleeding after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "Deals 2x weapon damage.");
        }
        return desc;
    }

    @Override
    public double getSynergyMultiplier(int level) {
        return 2.0 + level;
    }

    @Override
    public MutationType getType() {
        return MutationType.BLEED;
    }

    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }


    private int getRequiredHits(int level, ItemStack item, LivingEntity user, double thresholdReduction) {
        int baseThreshold = (level == 1) ? 12 : 8;
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
        ItemStack weapon = damager.getEquipment() != null ? damager.getEquipment().getItemInMainHand() : null;
        if (weapon == null || weapon.getType().isAir()) {
            if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile) {
                weapon = com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.BowDamageScaling.getBowFromArrow((AbstractArrow) projectile, damager instanceof Player ? (Player) damager : null);
            }
        }
        if (weapon == null || weapon.getType().isAir()) return;

        // Apply bleed multiplier based on level
        // event.getDamage() already includes the full weapon damage with all enchants (Sharpness, Smite, etc.)
        // We use setDamage to avoid recursion risks while still accounting for all modifiers.
        double multiplier = (level == 1) ? 1.5 : 2.0;
        double damage = event.getDamage() * multiplier;

        if (damager instanceof Player player) {
            damage = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, damage, "window");
        }

        event.setDamage(damage);

        if (damager instanceof Player player) {
            player.sendActionBar("§c§lBleed §7has procced! §e(" + (int)multiplier + "x Damage)");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
        }

        victim.getWorld().spawnParticle(
                Particle.BLOCK,
                victim.getLocation().add(0, 1, 0),
                30, 0.3, 0.3, 0.3,
                Material.REDSTONE_BLOCK.createBlockData()
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        Integer synergyLevel = bleedSynergyLevels.get(victim.getUniqueId());
        if (synergyLevel == null) return;

        double multiplier = (synergyLevel == 1) ? 1.25 : 1.5;
        event.setDamage(event.getDamage() * multiplier);
    }

    @Override
    public void onProcSynergy(LivingEntity damager, LivingEntity victim, int level) {
        if (com.example.corovaItems.Enchantments.EnchantBooks.BoomerangBook.IS_BOOMERANG_HIT.get()) return;
        if (damager instanceof Player && victim instanceof Player && com.example.corovateams.CorovaTeams.getInstance() != null) {
            com.example.corovateams.TeamManager tm = com.example.corovateams.CorovaTeams.getInstance().getTeamManager();
            com.example.corovateams.CorovaTeam damagerTeam = tm.getTeamByPlayer(damager.getUniqueId());
            com.example.corovateams.CorovaTeam victimTeam = tm.getTeamByPlayer(victim.getUniqueId());
            if (damagerTeam != null && damagerTeam.equals(victimTeam) && !damagerTeam.hasFriendlyFire()) {
                return;
            }
        }

        // Boomerang Synergy: deal 1.25x or 1.5x weapon damage directly.
        // We use the listener pattern to ensure enchants are factored in.
        double multiplier = (level == 1) ? 1.25 : 1.5;
        double baseDamage = EnchantmentBook.getWeaponDamage(damager);
        bleedSynergyLevels.put(victim.getUniqueId(), level);
        victim.damage(baseDamage, damager);
        bleedSynergyLevels.remove(victim.getUniqueId());

        if (damager instanceof Player player) {
            player.sendActionBar("§c§lBleed §7has procced! §e(" + (int)multiplier + "x Damage)");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
        }

        victim.getWorld().spawnParticle(
                Particle.BLOCK,
                victim.getLocation().add(0, 1, 0),
                30, 0.3, 0.3, 0.3,
                Material.REDSTONE_BLOCK.createBlockData()
        );
    }
}
