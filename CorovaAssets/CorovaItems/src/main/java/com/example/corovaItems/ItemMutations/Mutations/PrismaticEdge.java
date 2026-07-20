package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ArmorTrims.PlayerTrimProfile;
import com.example.corovaItems.ArmorTrims.TrimCalculator;
import com.example.corovaItems.ArmorTrims.TrimManager;
import com.example.corovaItems.ItemMutations.ItemMutations;
import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Prismatic Edge — Diamond armor mutation.
 *
 * Every hit you land without taking damage increments a hit counter (shared across
 * all equipped mutation pieces). When the counter reaches HITS_REQUIRED * pieceCount,
 * a Prismatic Charge is built. Your next attack consumes the charge and deals a burst:
 *   Level I : +40% bonus damage and blinds the target for 0.5 seconds.
 *   Level II : +40% bonus damage, blinds for 0.5 seconds, AND applies Glowing for 3
 *              seconds to the target (making them visible through walls).
 *
 * Taking any damage resets the hit counter (but does NOT lose a built charge).
 *
 * Diamond Trim Synergy:
 *   Each diamond trim piece adds +10% bonus damage to the Prismatic burst.
 *   (0 trims = +40%, 4 trims = +80%)
 *   The Quartz intersection bonus (BURST + CONDITIONAL) also applies multiplicatively.
 *
 * Diamond armor only.
 */
public class PrismaticEdge implements Mutation {

    private final MutationManager mutationManager;

    // Per-player hit counter — increments on every hit landed without taking damage.
    // Resets to 0 when the player takes any damage or when a charge is consumed.
    private final Map<UUID, Integer> hitCounter = new HashMap<>();

    // Per-player: true when a Prismatic Charge is ready to be consumed on next hit.
    private final Map<UUID, Boolean> chargeReady = new HashMap<>();

    // ── Tuning constants ──────────────────────────────────────────────────────

    /** Hits required to build a Prismatic Charge. */
    private static final int HITS_REQUIRED = 3;

    /** Base burst bonus added on top of 1.0x damage (i.e. +20%). */
    private static final double BASE_BURST_BONUS = 0.20;

    /** Additional burst bonus per diamond trim piece worn. */
    private static final double BURST_BONUS_PER_TRIM = 0.05;

    /** Blindness duration in ticks (0.5 seconds = 10 ticks). */
    private static final int BLINDNESS_TICKS = 10;

    /** Glowing duration in ticks for Level II (3 seconds = 60 ticks). */
    private static final int GLOWING_TICKS = 60;

    public PrismaticEdge(MutationManager mutationManager) {
        this.mutationManager = mutationManager;
    }

    // ── Mutation identity ─────────────────────────────────────────────────────

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.CONDITIONAL, MutationCategory.BURST);
    }

    @Override
    public String getColor() {
        return "#00CED1";
    }

    public String getName() {
        return "Prismatic Edge";
    }

    public int getMaxLevel() {
        return 2;
    }

    @Override
    public boolean isCompatible(ItemStack item) {
        return true;
    }

    public MutationType getType() {
        return MutationType.PRISMATIC_EDGE;
    }

    public double getWeight() {
        return ItemMutations.DEFAULT_WEIGHT;
    }

    // ── Lore ──────────────────────────────────────────────────────────────────

    public List<String> getLore(int level) {
        List<String> lore = new ArrayList<>();
        ChatColor color = ChatColor.of(getColor());
        lore.add(color + getName() + " " + MutationUtils.toRoman(level));
        return lore;
    }

    @Override
    public List<String> getDescription(int level) {
        List<String> desc = new ArrayList<>();
        desc.add(ChatColor.GRAY + "Land " + ChatColor.AQUA + HITS_REQUIRED + " hits" +
                ChatColor.GRAY + " without taking damage");
        desc.add(ChatColor.GRAY + "to build a " + ChatColor.AQUA + "Prismatic Charge" +
                ChatColor.GRAY + ".");
        desc.add(ChatColor.GRAY + "Next hit consumes the charge:");
        desc.add(ChatColor.GRAY + "  " + ChatColor.AQUA + "+" + (int)(BASE_BURST_BONUS * 100) +
                "% bonus damage" + ChatColor.GRAY + " & " +
                ChatColor.DARK_GRAY + "Blindness (0.5s)" + ChatColor.GRAY + ".");
        desc.add(ChatColor.DARK_RED + "Taking damage resets your hit counter!");
        if (level >= 2) {
            desc.add(ChatColor.YELLOW + "Level II: Burst also applies");
            desc.add(ChatColor.YELLOW + "  Glowing (3s) to the target.");
        }
        desc.add(ChatColor.AQUA + "Diamond Trim Synergy:");
        desc.add(ChatColor.GRAY + "  Each Diamond Trim: +" +
                (int)(BURST_BONUS_PER_TRIM * 100) + "% burst damage.");
        desc.add(ChatColor.DARK_GRAY + "Diamond armor only.");
        return desc;
    }

    // ── Proc entry-point ──────────────────────────────────────────────────────
    //
    // Return 1.0 so MutationManager always calls onProc — all conditional logic
    // lives inside onProc itself, matching the HuntersInstinct pattern.


    /**
     * Higher-level trigger for the mutation logic.
     * Manually handling the event ensures armor mutations trigger on landing hits,
     * consistent with how weapon mutations are normally handled by MutationManager.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        // Standard attack criteria
        if (player.getAttackCooldown() < 1.0F) return;
        if (com.example.corovaItems.WeaponProperties.DualWielding.offHandAttackInProgress.contains(player.getUniqueId())) return;

        int level = getMaxLevel(player);
        if (level > 0) {
            onProc(player, victim, level, event);
        }
    }

    public void onProc(LivingEntity damager, LivingEntity victim, int level,
                       EntityDamageByEntityEvent event) {
        if (!(damager instanceof Player player)) return;

        UUID uuid = player.getUniqueId();
        boolean charged = chargeReady.getOrDefault(uuid, false);

        if (charged) {
            // ── Consume the charge and deliver the burst ──────────────────────
            chargeReady.put(uuid, false);
            hitCounter.put(uuid, 0);

            // Build burst bonus: base + per-trim flat bonus
            PlayerTrimProfile profile = TrimManager.getInstance().getProfile(player);
            int diamondTrimCount = profile.diamondCount;
            double burstBonus = BASE_BURST_BONUS + (diamondTrimCount * BURST_BONUS_PER_TRIM);

            // Quartz intersection bonus (BURST + CONDITIONAL both present — multiplied in)
            double quartzBonus = TrimCalculator.getQuartzIntersectionBonus(getCategories(), profile);
            double totalMultiplier = (1.0 + burstBonus) * quartzBonus;

            event.setDamage(event.getDamage() * totalMultiplier);

            // Blindness — 0.5 seconds (10 ticks), ambient=false, particles=false so it's subtle
            victim.addPotionEffect(
                    new PotionEffect(PotionEffectType.BLINDNESS, BLINDNESS_TICKS, 0, false, false));

            // Level II: also Glowing
            if (level >= 2) {
                victim.addPotionEffect(
                        new PotionEffect(PotionEffectType.GLOWING, GLOWING_TICKS, 0, false, false));
            }

            // Particles: prismatic burst on the target
            victim.getWorld().spawnParticle(
                    Particle.END_ROD,
                    victim.getLocation().add(0, 1, 0),
                    20, 0.4, 0.6, 0.4, 0.08);
            player.getWorld().spawnParticle(
                    Particle.CRIT,
                    player.getLocation().add(0, 1, 0),
                    10, 0.2, 0.4, 0.2, 0.05);

            // Sounds
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.8f);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);

            int bonusPct = (int)((totalMultiplier - 1.0) * 100);
            player.sendActionBar(ChatColor.AQUA + "◈ Prismatic Burst! " +
                    ChatColor.WHITE + "+" + bonusPct + "% damage!");

        } else {
            // ── Accumulate a hit toward the next charge ───────────────────────
            int current = hitCounter.getOrDefault(uuid, 0) + 1;
            hitCounter.put(uuid, current);

            if (current >= HITS_REQUIRED) {
                // Charge built!
                chargeReady.put(uuid, true);
                hitCounter.put(uuid, 0);

                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.5f);
                player.sendActionBar(ChatColor.AQUA + "◈ Prismatic Charge ready!");
                player.spawnParticle(Particle.END_ROD,
                        player.getLocation().add(0, 1, 0),
                        12, 0.3, 0.5, 0.3, 0.05);
            } else {
                // Show progress on every hit
                int remaining = HITS_REQUIRED - current;
                player.sendActionBar(ChatColor.DARK_AQUA + "Prismatic [" +
                        progressBar(current, HITS_REQUIRED) + ChatColor.DARK_AQUA + "] " +
                        ChatColor.GRAY + remaining + " hit" + (remaining == 1 ? "" : "s") + " left");
            }
        }
    }

    // ── Reset hit counter on incoming damage ──────────────────────────────────

    /**
     * Any damage taken resets the hit counter — but a built charge is preserved.
     * Players are punished for being sloppy mid-combo but keep the charge they earned.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTakeDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (getMaxLevel(player) == 0) return;

        UUID uuid = player.getUniqueId();
        int counter = hitCounter.getOrDefault(uuid, 0);
        if (counter <= 0) return; // nothing to reset

        hitCounter.put(uuid, 0);
        player.sendActionBar(ChatColor.RED + "✗ Prismatic streak broken!");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int getMaxLevel(Player player) {
        int max = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null || piece.getType().isAir()) continue;
            int lvl = mutationManager.getMutationLevel(piece, MutationType.PRISMATIC_EDGE);
            if (lvl > max) max = lvl;
        }
        return max;
    }

    private boolean isDiamondArmor(Material type) {
        return type == Material.DIAMOND_HELMET
                || type == Material.DIAMOND_CHESTPLATE
                || type == Material.DIAMOND_LEGGINGS
                || type == Material.DIAMOND_BOOTS;
    }

    /** 8-cell Unicode progress bar filled proportionally. */
    private String progressBar(int current, int max) {
        int filled = (int) Math.round((double) current / max * 8);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(i < filled
                    ? ChatColor.AQUA + "█"
                    : ChatColor.DARK_GRAY + "░");
        }
        return sb.toString();
    }
}