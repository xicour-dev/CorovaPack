package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ArmorTrims.PlayerTrimProfile;
import com.example.corovaItems.ArmorTrims.TrimCalculator;
import com.example.corovaItems.ArmorTrims.TrimManager;
import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Hunter's Instinct — Leather armor mutation.
 *
 * When you hit an enemy below 50% health, gain a stacking Hunt buff (max 3 stacks,
 * each stack decays after 6 seconds independently). Each stack gives:
 *   +4% damage and +3% movement speed.
 *
 * At max stacks (3), your next hit deals a Killing Blow:
 *   Bonus true damage equal to 8% of the target's missing health.
 *
 * Level 2: The Killing Blow also applies Slowness II for 3 seconds to the target
 *           and refreshes all stacks back to max.
 *
 * Leather armor only.
 */
public class HuntersInstinct implements Mutation {

    private final MutationManager mutationManager;
    private final JavaPlugin plugin;

    // Per-player hunt stacks (0–3)
    private final Map<UUID, Integer> huntStacks = new HashMap<>();
    // Per-player: list of scheduled decay tasks (one per stack)
    private final Map<UUID, List<BukkitTask>> decayTasks = new HashMap<>();
    // Per-player: track if Killing Blow is pending (stacks == 3 and not yet consumed)
    private final Map<UUID, Boolean> killingBlowReady = new HashMap<>();

    private static final int MAX_STACKS = 3;
    private static final long STACK_DECAY_TICKS = 120L; // 6 seconds
    private static final double DAMAGE_PER_STACK = 0.02;
    private static final double SPEED_PER_STACK = 0.02;
    private static final double KILLING_BLOW_MISSING_HEALTH_RATIO = 0.04;

    private static final NamespacedKey HUNT_STACKS_KEY =
            new NamespacedKey("corovaitems", "predators_instinct_stacks");

    public HuntersInstinct(MutationManager mutationManager) {
        this.mutationManager = mutationManager;
        this.plugin = JavaPlugin.getProvidingPlugin(this.getClass());
    }

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.CONDITIONAL);
    }

    @Override
    public String getColor() {
        return "#8B4513";
    }

    public String getName() {
        return "Hunter's Instinct";
    }

    public int getMaxLevel() {
        return 2;
    }

    @Override
    public boolean isCompatible(ItemStack item) {
        return true;
    }

    public List<String> getLore(int level) {
        List<String> lore = new ArrayList<>();
        ChatColor color = ChatColor.of(getColor());
        lore.add(color + getName() + " " + MutationUtils.toRoman(level));
        return lore;
    }

    @Override
    public List<String> getDescription(int level) {
        List<String> desc = new ArrayList<>();
        desc.add(ChatColor.GRAY + "Hitting an enemy below 50% health grants a");
        desc.add(ChatColor.GRAY + "Hunt stack (max 3, decays after 6s each).");
        desc.add(ChatColor.GRAY + "Each stack: " + ChatColor.RED + "+2% damage" +
                ChatColor.GRAY + " & " + ChatColor.GREEN + "+2% movement speed" + ChatColor.GRAY + ".");
        if (level == 1) {
            desc.add(ChatColor.GRAY + "Adds +500 Durability.");
        } else if (level == 2) {
            desc.add(ChatColor.GRAY + "Adds +1000 Durability.");
        }
        desc.add(ChatColor.GRAY + "At " + ChatColor.GOLD + "3 stacks" + ChatColor.GRAY +
                ", next hit deals a " + ChatColor.DARK_RED + "Killing Blow" + ChatColor.GRAY + ":");
        desc.add(ChatColor.GRAY + "  +" + (int)(KILLING_BLOW_MISSING_HEALTH_RATIO * 100) +
                "% of target's missing health as true damage.");
        if (level >= 2) {
            desc.add(ChatColor.YELLOW + "Level II: Killing Blow also applies");
            desc.add(ChatColor.YELLOW + "  Slowness II (3s) and refreshes all stacks.");
        }
        desc.add(ChatColor.DARK_GRAY + "Leather armor only.");
        return desc;
    }

    public MutationType getType() {
        return MutationType.HUNTERS_INSTINCT;
    }

    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }

    @Override
    public int getDurabilityBonus(int level) {
        return level * 500;
    }


    @Override
    public void onProc(LivingEntity damager, LivingEntity victim, int level,
                       EntityDamageByEntityEvent event) {
        if (!(damager instanceof Player player)) return;

        // Count how many leather pieces with this mutation the player is wearing
        int mutationPieceCount = countLeatherPiecesWithMutation(player);
        if (mutationPieceCount == 0) return;

        double victimMaxHealth = Objects.requireNonNull(
                victim.getAttribute(Attribute.MAX_HEALTH)).getValue();
        double victimHealth = victim.getHealth();
        boolean victimLowHealth = victimHealth < (victimMaxHealth * 0.50);

        int currentStacks = huntStacks.getOrDefault(player.getUniqueId(), 0);
        boolean isKillingBlowReady = killingBlowReady.getOrDefault(player.getUniqueId(), false);

        // ── Killing Blow ─────────────────────────────────────────────────────
        if (isKillingBlowReady && currentStacks >= MAX_STACKS) {
            double missingHealth = victimMaxHealth - victimHealth;
            double killingBlowDamage = missingHealth * KILLING_BLOW_MISSING_HEALTH_RATIO;

            // Amplify via Quartz intersection bonus (INCREMENTAL + CONDITIONAL)
            if (damager instanceof Player p) {
                PlayerTrimProfile profile = TrimManager.getInstance().getProfile(p);
                killingBlowDamage *= TrimCalculator.getQuartzIntersectionBonus(getCategories(), profile);
            }

            // True damage — dealt directly, bypasses armor
            final double finalDamage = killingBlowDamage;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (victim.isValid() && !victim.isDead()) {
                    victim.damage(finalDamage, player);
                    victim.getWorld().spawnParticle(
                            Particle.CRIT, victim.getLocation().add(0, 1, 0),
                            15, 0.3, 0.5, 0.3, 0.2);
                    player.sendActionBar(ChatColor.DARK_RED + "⚔ Killing Blow! " +
                            ChatColor.RED + String.format("%.1f", finalDamage) + " true damage!");
                }
            }, 1L);

            if (level >= 2) {
                // Slowness II for 3 seconds
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                // Refresh all stacks
                killingBlowReady.put(player.getUniqueId(), false);
                // Cancel existing decay tasks and reschedule all 3 stacks
                cancelDecayTasks(player.getUniqueId());
                huntStacks.put(player.getUniqueId(), MAX_STACKS);
                scheduleDecayForAllStacks(player, MAX_STACKS);
                applyHuntPassives(player, MAX_STACKS);
                player.sendActionBar(ChatColor.GOLD + "⚔ Killing Blow! Stacks refreshed!");
            } else {
                // Consume stacks
                killingBlowReady.put(player.getUniqueId(), false);
                cancelDecayTasks(player.getUniqueId());
                huntStacks.put(player.getUniqueId(), 0);
                applyHuntPassives(player, 0);
            }
            return;
        }

        // ── Stack accumulation ───────────────────────────────────────────────
        if (victimLowHealth && currentStacks < MAX_STACKS) {
            int newStacks = currentStacks + 1;
            huntStacks.put(player.getUniqueId(), newStacks);

            // Schedule decay for this individual stack
            scheduleOneDecay(player, newStacks);

            applyHuntPassives(player, newStacks);

            if (newStacks == MAX_STACKS) {
                killingBlowReady.put(player.getUniqueId(), true);
                player.sendActionBar(ChatColor.GOLD + "★ Killing Blow ready! [" +
                        stackBar(newStacks) + "]");
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.8f);
            } else {
                player.sendActionBar(ChatColor.RED + "Hunt [" + stackBar(newStacks) + "]");
                player.playSound(player.getLocation(), Sound.ENTITY_WOLF_GROWL, 0.6f, 1.2f + (newStacks * 0.1f));
            }
        }
    }

    // ── Passive damage amplification via EntityDamageByEntityEvent ───────────

    /**
     * This separate event handler applies the Hunt stack damage multiplier to
     * hits from any player wearing the mutation — at HIGHEST priority so it's
     * factored before other damage processors.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHuntDamageBonus(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        int stacks = huntStacks.getOrDefault(player.getUniqueId(), 0);
        if (stacks <= 0) return;

        if (countLeatherPiecesWithMutation(player) == 0) return;

        double bonus = stacks * DAMAGE_PER_STACK;
        // Amplify via CONDITIONAL trim (diamond)
        PlayerTrimProfile profile = TrimManager.getInstance().getProfile(player);
        double conditionAmp = TrimCalculator.getAmplification(getCategories(), profile, "condition");
        // conditionAmp is additive bonus to the condition window — scale bonus proportionally
        bonus *= (1.0 + conditionAmp);

        event.setDamage(event.getDamage() * (1.0 + bonus));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyHuntPassives(Player player, int stacks) {
        // Speed: apply via potion effect (persistent, refreshed on every stack change)
        if (stacks > 0) {
            // SPEED potion level 0 = +20% speed. We apply fractional speed via attribute.
            // Use a short-duration potion that we keep refreshing on stack updates.
            // Each stack = +3% speed. We use attribute modifier for precision.
            org.bukkit.attribute.AttributeInstance speedAttr =
                    player.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speedAttr != null) {
                NamespacedKey speedKey = new NamespacedKey("corovaitems", "predators_instinct_speed");
                speedAttr.removeModifier(speedKey);
                if (stacks > 0) {
                    speedAttr.addModifier(new org.bukkit.attribute.AttributeModifier(
                            speedKey,
                            stacks * SPEED_PER_STACK,
                            org.bukkit.attribute.AttributeModifier.Operation.ADD_SCALAR,
                            org.bukkit.inventory.EquipmentSlotGroup.ANY));
                }
            }
        } else {
            // Remove speed bonus
            org.bukkit.attribute.AttributeInstance speedAttr =
                    player.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speedAttr != null) {
                NamespacedKey speedKey = new NamespacedKey("corovaitems", "predators_instinct_speed");
                speedAttr.removeModifier(speedKey);
            }
        }
    }

    /** Schedules decay for one newly added stack. When it fires, remove one stack. */
    private void scheduleOneDecay(Player player, int stackIndexForDisplay) {
        UUID uuid = player.getUniqueId();
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int current = huntStacks.getOrDefault(uuid, 0);
            if (current <= 0) return;
            int newCount = current - 1;
            huntStacks.put(uuid, newCount);
            if (newCount < MAX_STACKS) {
                killingBlowReady.put(uuid, false);
            }
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                applyHuntPassives(online, newCount);
                if (newCount > 0) {
                    online.sendActionBar(ChatColor.GOLD + "Hunt fading [" + stackBar(newCount) + "]");
                }
            }
        }, STACK_DECAY_TICKS);

        decayTasks.computeIfAbsent(uuid, k -> new ArrayList<>()).add(task);
    }

    /** Schedules decay for all stacks at once (used on refresh). */
    private void scheduleDecayForAllStacks(Player player, int count) {
        for (int i = 0; i < count; i++) {
            scheduleOneDecay(player, i + 1);
        }
    }

    private void cancelDecayTasks(UUID uuid) {
        List<BukkitTask> tasks = decayTasks.remove(uuid);
        if (tasks != null) {
            for (BukkitTask t : tasks) {
                t.cancel();
            }
        }
    }

    private int countLeatherPiecesWithMutation(Player player) {
        int count = 0;
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && mutationManager.hasMutation(armor, MutationType.HUNTERS_INSTINCT)) {
                count++;
            }
        }
        return count;
    }

    private boolean isLeatherArmor(Material type) {
        if (type == null) return false;
        return type == Material.LEATHER_HELMET
                || type == Material.LEATHER_CHESTPLATE
                || type == Material.LEATHER_LEGGINGS
                || type == Material.LEATHER_BOOTS;
    }

    private String stackBar(int stacks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MAX_STACKS; i++) {
            sb.append(i < stacks ? ChatColor.RED + "●" : ChatColor.DARK_GRAY + "○");
        }
        return sb.toString();
    }
}