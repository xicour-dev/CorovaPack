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
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class StaticCharge implements Mutation, Mutation.BuildUpMutation {

    private final JavaPlugin plugin;
    private final Map<UUID, Integer> hitCounter = new HashMap<>();
    private static final String METADATA_PROCCING = "StaticChargeProccing";

    public StaticCharge(MutationManager manager) {
        this.plugin = JavaPlugin.getProvidingPlugin(this.getClass());
    }

    @Override
    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.SUSTAINED, MutationCategory.ENCHANT_SYNERGY, MutationCategory.INCREMENTAL);
    }

    @Override
    public String getColor() {
        return "#B87333";
    }

    @Override
    public String getName() {
        return "Static Charge";
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
            desc.add(ChatColor.GRAY + "Charge the target after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "Strikes lightning immediately (at least once).");
            desc.add(ChatColor.GRAY + "10% chance for lightning each second for 7s.");
            desc.add(ChatColor.GRAY + "Adds +700 Durability.");
            desc.add(ChatColor.DARK_GRAY + "Copper weapons only.");
        } else {
            desc.add(ChatColor.GRAY + "Charge the target after " + hits + " hits.");
            desc.add(ChatColor.GRAY + "Strikes lightning immediately (at least twice).");
            desc.add(ChatColor.GRAY + "10% chance for lightning each second for 7s.");
            desc.add(ChatColor.GRAY + "Adds +1400 Durability.");
            desc.add(ChatColor.DARK_GRAY + "Copper weapons only.");
        }

        desc.add(ChatColor.YELLOW + "Lightning deals 50% weapon damage.");
        return desc;
    }

    @Override
    public int getDurabilityBonus(int level) {
        if (level == 1) return 700;
        if (level == 2) return 1400;
        return 0;
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
        return MutationType.STATIC_CHARGE;
    }

    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }


    private int getRequiredHits(int level, ItemStack item, LivingEntity user, double thresholdReduction) {
        int baseThreshold = (level == 1) ? 12 : 8;
        if (item != null && MutationManager.getInstance().getSynergyHandler().isLightning(item)) {
            baseThreshold = Math.max(1, baseThreshold - 2);
        }
        return Math.max(1, (int) Math.round(baseThreshold * (1.0 - thresholdReduction)));
    }

    @Override
    public boolean incrementAndCheck(UUID damagerId, LivingEntity victim, int level, ItemStack item, LivingEntity user, boolean canProc, double thresholdReduction) {
        if (MutationUtils.isFriendly(user, victim)) return false;
        int required = getRequiredHits(level, item, user, thresholdReduction);
        int count = hitCounter.getOrDefault(damagerId, 0) + 1;
        if (count >= required) {
            if (canProc && !user.hasMetadata(METADATA_PROCCING)) {
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
        double weaponDamage = event.getDamage();
        ItemStack weapon = (damager.getEquipment() != null) ? damager.getEquipment().getItemInMainHand() : null;
        boolean hasSynergy = MutationManager.getInstance().getSynergyHandler().isLightning(weapon);

        if (damager instanceof Player) {
            ((Player) damager).sendActionBar(ChatColor.YELLOW + "Static electricity charges your target!");
        }

        // Guaranteed lightning strikes
        int guaranteedStrikes = (level == 1) ? 1 : 2;
        for (int i = 0; i < guaranteedStrikes; i++) {
            final int strikeIndex = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (victim.isValid() && !victim.isDead()) {
                    victim.getWorld().strikeLightningEffect(victim.getLocation());
                    victim.setNoDamageTicks(0);
                    damager.setMetadata(METADATA_PROCCING, new FixedMetadataValue(plugin, true));
                    victim.damage(weaponDamage * 0.5, damager);
                    damager.removeMetadata(METADATA_PROCCING, plugin);
                }
            }, strikeIndex * 10L); // 0.5s interval between strikes
        }

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                int maxTicks = 7;
                if (damager instanceof Player player) {
                    maxTicks = (int) MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, StaticCharge.this, maxTicks, "duration");
                }
                if (ticks >= maxTicks || !victim.isValid() || victim.isDead()) {
                    this.cancel();
                    return;
                }

                // Blueish dust particles
                victim.getWorld().spawnParticle(Particle.DUST, victim.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, new Particle.DustOptions(Color.fromRGB(0, 191, 255), 1.0f));

                // 10% chance for lightning every second
                double procChance = 0.10;
                if (damager instanceof Player player) {
                    procChance += TrimCalculator.getAmplification(getCategories(), TrimManager.getInstance().getProfile(player), "incremental");
                }
                if (Math.random() < procChance) {
                    victim.getWorld().strikeLightningEffect(victim.getLocation());
                    double strikeDamage = weaponDamage * 0.5; // 50% weapon damage
                    if (damager instanceof Player player) {
                        strikeDamage = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, StaticCharge.this, strikeDamage, "tick_damage");
                    }
                    victim.setNoDamageTicks(0);
                    damager.setMetadata(METADATA_PROCCING, new FixedMetadataValue(plugin, true));
                    victim.damage(strikeDamage, damager);

                    if (hasSynergy) {
                        // Synergy: strikes the main target twice
                        victim.getWorld().strikeLightningEffect(victim.getLocation());
                        victim.setNoDamageTicks(0);
                        victim.damage(strikeDamage, damager);

                        // Arcs to 2 other hostile mobs
                        int arcs = 0;
                        for (Entity nearby : victim.getNearbyEntities(5, 5, 5)) {
                            if (nearby instanceof LivingEntity && !nearby.equals(damager) && !nearby.equals(victim)) {
                                nearby.getWorld().strikeLightningEffect(nearby.getLocation());
                                ((LivingEntity) nearby).setNoDamageTicks(0);
                                ((LivingEntity) nearby).damage(strikeDamage, damager);
                                arcs++;
                                if (arcs >= 2) break;
                            }
                        }
                    }
                    damager.removeMetadata(METADATA_PROCCING, plugin);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @Override
    public void onProcSynergy(LivingEntity damager, LivingEntity victim, int level) {
        // Reduced synergy: Single lightning strike immediately
        victim.getWorld().strikeLightningEffect(victim.getLocation());
        if (!com.example.corovaItems.Enchantments.EnchantBooks.BoomerangBook.IS_BOOMERANG_HIT.get()) {
            double damage = EnchantmentBook.getWeaponDamage(damager) * 0.5;
            victim.setNoDamageTicks(0);
            damager.setMetadata(METADATA_PROCCING, new FixedMetadataValue(plugin, true));
            victim.damage(damage, damager);
            damager.removeMetadata(METADATA_PROCCING, plugin);
        }
    }
}
