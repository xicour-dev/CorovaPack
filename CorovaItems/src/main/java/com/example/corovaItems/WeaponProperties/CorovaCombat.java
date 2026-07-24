package com.example.corovaItems.WeaponProperties;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CorovaCombat — Global combat override for PaperMC 1.21.x
 *
 * PURPOSE:
 * The entire purpose of this class is to ensure there is NO hit delay (I-frames)
 * at all in combat for any type of damage (Melee, Arrows, Projectiles, etc.)
 * besides the attacker's own hit cooldown.
 *
 * Arrows shot rapidly will never deflect/bounce off because the target's
 * hit delay is always reset to 0 upon any successful combat hit.
 *
 * Environmental damage (Lava, Fire, etc.) is manually throttled to prevent
 * it from damaging the player every single tick due to the removed I-frames.
 */
public class CorovaCombat implements Listener {

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    /** Default melee cooldown for mobs without a weapon item (ms). */
    private static final long DEFAULT_MOB_MELEE_COOLDOWN_MS = 500L;

    /**
     * Bare-fist / unrecognised weapon cooldown for players (ms).
     * Vanilla bare-hand speed is 2.0 attacks/sec → 500 ms.
     */
    private static final long DEFAULT_PLAYER_MELEE_COOLDOWN_MS = 500L;

    // -------------------------------------------------------------------------
    // State — all accessed on the main thread, so plain HashMap is sufficient.
    // -------------------------------------------------------------------------

    private final JavaPlugin plugin;

    /**
     * NamespacedKey placed on a weapon item's PersistentDataContainer (LONG)
     * to specify a custom attack cooldown in milliseconds.
     */
    private final NamespacedKey attackSpeedKey;

    /** UUID → last successful melee-hit timestamp (ms). */
    private final Map<UUID, Long> meleeCooldownMap = new HashMap<>();

    /** UUID:DamageCause -> last environmental damage timestamp (ms). */
    private final Map<String, Long> environmentalCooldownMap = new HashMap<>();

    /**
     * Attackers currently using special abilities that should bypass melee cooldowns.
     */
    public static final Set<UUID> abilityBypass = new HashSet<>();

    /**
     * Entities currently performing a manual sweep attack.
     */
    public static final Set<UUID> manualSweepActive = new HashSet<>();

    /**
     * Targets that are exempt from i-frame clearing for their NEXT incoming
     * EntityDamageByEntityEvent. Each UUID is consumed (removed) on first check.
     */
    private static final Set<UUID> reflectExemptTargets = new HashSet<>();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public CorovaCombat(JavaPlugin plugin) {
        this.plugin = plugin;
        this.attackSpeedKey = new NamespacedKey(plugin, "attack_speed_ms");

        // Periodic cleanup: remove cooldown entries older than 30 s.
        // Runs every 5 minutes (6 000 ticks) to avoid memory leaks from
        // dead mobs whose UUIDs will never be cleared otherwise.
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::cleanupStaleCooldowns,
                6_000L, 6_000L);
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Exempts {@code target} from i-frame clearing for their very next incoming
     * combat event. One-shot: the exemption is consumed immediately after checking.
     *
     * <p>Call this BEFORE you fire reflected, chained, or recursive damage so that
     * removing i-frames on the target does not cause an infinite damage loop.</p>
     */
    public static void exemptFromIFrameClear(LivingEntity target) {
        reflectExemptTargets.add(target.getUniqueId());
    }

    /**
     * Immediately clears the melee cooldown for {@code attackerUUID}.
     * Use this after special abilities or skill resets so the next swing lands
     * without waiting for the normal cooldown window.
     */
    public void resetCooldown(UUID attackerUUID) {
        meleeCooldownMap.remove(attackerUUID);
    }

    /**
     * Returns the {@link NamespacedKey} that custom weapon classes should set
     * on their item's {@link org.bukkit.persistence.PersistentDataContainer}
     * (as {@link PersistentDataType#LONG}) to specify an attack cooldown in ms.
     */
    public NamespacedKey getAttackSpeedKey() {
        return attackSpeedKey;
    }

    // =========================================================================
    // Phase 1 — Melee Cooldown Validation  (LOW, before all other damage logic)
    // =========================================================================

    /**
     * Cancels melee hits from attackers who have not yet finished their cooldown.
     * Projectiles are always allowed through — only direct melee swings are gated.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMeleeCooldownCheck(EntityDamageByEntityEvent event) {
        if (isProjectile(event) || isExcludedCause(event)) return;

        if (event.getDamager() instanceof LivingEntity attacker) {
            UUID attackerUUID = attacker.getUniqueId();

            // Off-hand attacks from DualWielding handle their own cooldowns
            // Gun damage from GunCombat also handles its own cooldowns/bypass
            // Special abilities also bypass this check
            if (DualWielding.offHandAttackInProgress.contains(attackerUUID) || GunCombat.firingGuns.contains(attackerUUID)
                    || abilityBypass.contains(attackerUUID) || manualSweepActive.contains(attackerUUID)
                    || event.getCause() == DamageCause.ENTITY_SWEEP_ATTACK) {
                return;
            }

            long now = System.currentTimeMillis();
            long lastHit = meleeCooldownMap.getOrDefault(attackerUUID, 0L);
            long cooldown = resolveMeleeCooldown(attacker);

            if (now - lastHit < cooldown) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Throttles environmental and periodic damage causes (Lava, Fire, etc.)
     * to simulate their normal vanilla cadence.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEnvironmentalCooldownCheck(EntityDamageEvent event) {
        // EntityDamageByEntityEvent is handled separately by melee/projectile logic.
        if (event instanceof EntityDamageByEntityEvent) return;

        if (event.getEntity() instanceof LivingEntity target && isEnvironmental(event.getCause())) {
            String key = target.getUniqueId() + ":" + event.getCause().name();
            long now = System.currentTimeMillis();
            long lastHit = environmentalCooldownMap.getOrDefault(key, 0L);
            long cooldown = getEnvironmentalCooldown(event.getCause());

            if (now - lastHit < cooldown) {
                event.setCancelled(true);
            }
        }
    }

    // =========================================================================
    // Phase 2 — Global No-I-Frame Rule  (MONITOR, final word on every hit)
    // =========================================================================

    /**
     * Records the timestamp for environmental damage hits to enforce future cooldowns.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnvironmentalDamageRecord(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent) return;

        if (event.getEntity() instanceof LivingEntity target && isEnvironmental(event.getCause())) {
            String key = target.getUniqueId() + ":" + event.getCause().name();
            environmentalCooldownMap.put(key, System.currentTimeMillis());
        }
    }

    /**
     * After all other handlers have had their say:
     *   1. Records the attacker's melee cooldown timestamp (non-projectile hits).
     *   2. Schedules a 1-tick noDamageTicks reset on the target so that
     *      the very next incoming hit (from any source) can deal full damage.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDirectCombatDamage(EntityDamageByEntityEvent event) {
        if (isExcludedCause(event)) return;

        if (event.getEntity() instanceof LivingEntity target) {
            UUID targetUUID = target.getUniqueId();

            // 1. Record melee cooldown for the attacker (if not a projectile, sweep, off-hand attack, gun fire, or ability bypass)
            if (!isProjectile(event) && event.getDamager() instanceof LivingEntity attacker) {
                UUID attackerUUID = attacker.getUniqueId();
                if (!DualWielding.offHandAttackInProgress.contains(attackerUUID) && !GunCombat.firingGuns.contains(attackerUUID)
                        && !abilityBypass.contains(attackerUUID) && !manualSweepActive.contains(attackerUUID)
                        && event.getCause() != DamageCause.ENTITY_SWEEP_ATTACK) {
                    meleeCooldownMap.put(attackerUUID, System.currentTimeMillis());
                }
            }

            // 2. Clear I-frames for the target immediately
            if (reflectExemptTargets.remove(targetUUID)) return;

            target.setNoDamageTicks(0);
            target.setMaximumNoDamageTicks(0);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Returns {@code true} when the damager is a {@link Projectile}. */
    private boolean isProjectile(EntityDamageByEntityEvent event) {
        return event.getDamager() instanceof Projectile;
    }

    /**
     * Returns {@code true} for environmental or periodic damage causes that
     * should be manually throttled to simulate a normal cadence.
     */
    private boolean isEnvironmental(DamageCause cause) {
        return switch (cause) {
            case LAVA,
                 FIRE,
                 FIRE_TICK,
                 HOT_FLOOR,
                 SUFFOCATION -> true;
            default -> cause.name().contains("CAMPFIRE");
        };
    }

    /**
     * Returns the minimum cooldown (ms) between damage ticks for the given cause.
     */
    private long getEnvironmentalCooldown(DamageCause cause) {
        return switch (cause) {
            case LAVA, HOT_FLOOR -> 450L; // Slightly under 10 ticks (500ms) for responsiveness
            default -> 950L;              // Slightly under 20 ticks (1000ms) for fire/poison/etc.
        };
    }

    /**
     * Returns {@code true} for damage causes that must be excluded from both
     * the cooldown system and i-frame clearing.
     */
    private boolean isExcludedCause(EntityDamageByEntityEvent event) {
        return switch (event.getCause()) {
            case THORNS,
                 MAGIC,
                 POISON,
                 WITHER -> true;
            default -> false;
        };
    }

    /**
     * Manually triggers a Sweeping Edge effect.
     * Replicates vanilla sweeping conditions and damage.
     *
     * @param player the player performing the sweep
     * @param target the primary target of the attack
     * @param weapon the weapon being used (must be a sword)
     */
    public static void spawnManualSweep(Player player, Entity target, ItemStack weapon) {
        spawnManualSweep(player, target, weapon, true);
    }

    /**
     * Overload that lets callers suppress the particle/sound.
     *
     * @param spawnVisuals whether to also play the sweep particle + sound. Pass
     *                     {@code false} when the caller's own attack already triggered
     *                     vanilla's sweep animation — e.g. DualWielding's off-hand
     *                     simulation calls {@code player.attack(target)} directly, which
     *                     independently broadcasts the vanilla sweep particle/sound
     *                     whenever the sword + on-ground + not-sprinting conditions are
     *                     met, regardless of whether the resulting ENTITY_SWEEP_ATTACK
     *                     damage event gets cancelled. Passing {@code true} here on top
     *                     of that would draw the sweep effect twice.
     */
    public static void spawnManualSweep(Player player, Entity target, ItemStack weapon, boolean spawnVisuals) {
        if (weapon == null || !weapon.getType().name().endsWith("_SWORD")) return;

        int sweepLevel = weapon.getEnchantmentLevel(Enchantment.SWEEPING_EDGE);

        // Vanilla sweep conditions: on ground, not sprinting
        if (!player.isOnGround() || player.isSprinting()) return;

        // Visuals
        if (spawnVisuals) {
            target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1.0, 0), 1);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
        }

        // Damage calculation based on vanilla logic: 1.0 + base * (level / (level + 1))
        double baseDamage = 1.0;
        AttributeInstance attr = player.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attr != null) {
            baseDamage = attr.getValue();
        }

        double sweepDamage = 1.0 + baseDamage * (sweepLevel / (sweepLevel + 1.0));

        // Apply damage to nearby entities
        manualSweepActive.add(player.getUniqueId());
        try {
            for (Entity nearby : target.getNearbyEntities(1.0, 0.5, 1.0)) {
                if (nearby instanceof LivingEntity living && !nearby.equals(player) && !nearby.equals(target)) {
                    // Manually clear I-frames to ensure the sweep damage registers immediately
                    living.setNoDamageTicks(0);
                    living.setMaximumNoDamageTicks(0);
                    living.damage(sweepDamage, player);
                }
            }
        } finally {
            manualSweepActive.remove(player.getUniqueId());
        }
    }

    /**
     * Baseline (unbuffed) attacks-per-second for a weapon material — i.e. what a player's
     * live ATTACK_SPEED attribute would read with nothing else affecting it besides that
     * weapon. Stock vanilla weapons don't expose this through
     * {@code ItemMeta#getAttributeModifiers()} (that only returns modifiers explicitly
     * stored in the item's NBT/meta — a weapon's built-in speed comes from the item TYPE
     * itself, not the ItemStack instance), so this table is the reliable source of truth.
     *
     * <p>Used by DualWielding to figure out how much of a player's current attribute value
     * is a genuine bonus (Swift Strike, enchants, etc.) versus just "this weapon's own
     * speed", so it can swap out the weapon-specific part when simulating an off-hand
     * attack without losing any active bonus.</p>
     *
     * <p>Note this intentionally differs from {@link #getMaterialCooldown(Material)}'s own
     * "default" bucket, which serves a different purpose (a generic melee-cooldown
     * fallback for mobs/unrecognised weapons) — the correct unbuffed baseline for a bare
     * hand or any non-weapon item is 4.0 attacks/sec, not the 2.0 that constant implies.</p>
     */
    public static double getBaselineAttacksPerSecond(Material material) {
        if (material == null) return 4.0;
        return switch (material) {
            case WOODEN_SWORD, STONE_SWORD, IRON_SWORD,
                 GOLDEN_SWORD, DIAMOND_SWORD, NETHERITE_SWORD -> 1.6;

            case WOODEN_AXE, STONE_AXE -> 0.8;
            case IRON_AXE -> 0.9;
            case GOLDEN_AXE, DIAMOND_AXE, NETHERITE_AXE -> 1.0;

            case WOODEN_PICKAXE, STONE_PICKAXE, IRON_PICKAXE,
                 GOLDEN_PICKAXE, DIAMOND_PICKAXE, NETHERITE_PICKAXE -> 1.2;

            case WOODEN_SHOVEL, STONE_SHOVEL, IRON_SHOVEL,
                 GOLDEN_SHOVEL, DIAMOND_SHOVEL, NETHERITE_SHOVEL -> 1.0;

            case WOODEN_HOE, STONE_HOE -> 2.0;
            case IRON_HOE -> 3.0;
            case GOLDEN_HOE, DIAMOND_HOE, NETHERITE_HOE -> 4.0;

            case TRIDENT -> 1.1;
            case MACE -> 0.6;

            default -> 4.0; // bare hand / any non-weapon item: no attack-speed modifier
        };
    }

    /**
     * Resolves the melee attack cooldown (ms) for {@code attacker}.
     */
    private long resolveMeleeCooldown(LivingEntity attacker) {
        ItemStack weapon = getHeldWeapon(attacker);

        // 1. Check for custom PDC attack speed on the item — always highest priority.
        if (weapon != null && !weapon.getType().isAir() && weapon.hasItemMeta()) {
            var meta = weapon.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer()
                    .has(attackSpeedKey, PersistentDataType.LONG)) {
                long custom = meta.getPersistentDataContainer()
                        .get(attackSpeedKey, PersistentDataType.LONG);
                // Clamp to a sane range (50 ms – 10 000 ms) to prevent abuse.
                return Math.max(50L, Math.min(10_000L, custom));
            }
        }

        // 2. For players, derive cooldown from their live ATTACK_SPEED attribute.
        if (attacker instanceof Player player) {
            AttributeInstance attackSpeedAttr = player.getAttribute(Attribute.ATTACK_SPEED);
            if (attackSpeedAttr != null) {
                double attackSpeed = attackSpeedAttr.getValue();
                if (attackSpeed > 0) {
                    long cooldownMs = (long) (1000.0 / attackSpeed);
                    return Math.max(50L, Math.min(10_000L, cooldownMs));
                }
            }
        }

        // 3. Material-based fallback for mobs holding a weapon item.
        if (weapon != null && !weapon.getType().isAir()) {
            return getMaterialCooldown(weapon.getType());
        }

        // 4. Entity-type default.
        return (attacker instanceof Player)
                ? DEFAULT_PLAYER_MELEE_COOLDOWN_MS
                : DEFAULT_MOB_MELEE_COOLDOWN_MS;
    }

    /**
     * Returns the ItemStack in the attacker's main hand.
     */
    private ItemStack getHeldWeapon(LivingEntity attacker) {
        if (attacker instanceof Player player) {
            return player.getInventory().getItemInMainHand();
        }
        if (attacker instanceof Mob mob && mob.getEquipment() != null) {
            return mob.getEquipment().getItemInMainHand();
        }
        return null;
    }

    /**
     * Maps a weapon {@link Material} to its approximate attack cooldown in ms.
     */
    private long getMaterialCooldown(Material material) {
        return switch (material) {
            // ── Swords (1.6 att/s → 625 ms) ────────────────────────────────
            case WOODEN_SWORD, STONE_SWORD, IRON_SWORD,
                 GOLDEN_SWORD, DIAMOND_SWORD, NETHERITE_SWORD -> 625L;

            // ── Axes ─────────────────────────────────────────────────────────
            case WOODEN_AXE  -> 1250L; // 0.8 att/s
            case STONE_AXE   -> 1250L; // 0.8 att/s
            case IRON_AXE    -> 1111L; // 0.9 att/s
            case GOLDEN_AXE  -> 1000L; // 1.0 att/s
            case DIAMOND_AXE,
                 NETHERITE_AXE -> 1000L; // 1.0 att/s

            // ── Pickaxes (1.2 att/s → 833 ms) ───────────────────────────────
            case WOODEN_PICKAXE, STONE_PICKAXE, IRON_PICKAXE,
                 GOLDEN_PICKAXE, DIAMOND_PICKAXE, NETHERITE_PICKAXE -> 833L;

            // ── Shovels (1.0 att/s → 1 000 ms) ─────────────────────────────
            case WOODEN_SHOVEL, STONE_SHOVEL, IRON_SHOVEL,
                 GOLDEN_SHOVEL, DIAMOND_SHOVEL, NETHERITE_SHOVEL -> 1_000L;

            // ── Hoes (speed scales with tier) ───────────────────────────────
            case WOODEN_HOE,
                 STONE_HOE   ->  500L; // 2.0 att/s
            case IRON_HOE    ->  333L; // 3.0 att/s
            case GOLDEN_HOE,
                 DIAMOND_HOE,
                 NETHERITE_HOE -> 250L; // 4.0 att/s

            // ── Trident (1.1 att/s → 909 ms) ────────────────────────────────
            case TRIDENT -> 909L;

            // ── Mace (0.6 att/s → 1 667 ms, heavy smash weapon) ─────────────
            case MACE -> 1_667L;

            // ── Everything else / bare fist ──────────────────────────────────
            default -> DEFAULT_PLAYER_MELEE_COOLDOWN_MS;
        };
    }

    /**
     * Removes cooldown entries that are older than 30 seconds.
     */
    private void cleanupStaleCooldowns() {
        long cutoff = System.currentTimeMillis() - 30_000L;
        meleeCooldownMap.values().removeIf(timestamp -> timestamp < cutoff);
    }
}