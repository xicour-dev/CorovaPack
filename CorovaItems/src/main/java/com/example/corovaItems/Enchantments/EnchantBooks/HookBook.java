package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaGuard.CorovaGuard;
import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovateams.CorovaTeam;
import com.example.corovateams.CorovaTeams;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

/**
 * Hook enchant — applies 1.8-style fishing-rod PvP behaviour to the enchanted rod.
 *
 * On bobber contact with a living entity:
 *   - Fires a pseudo-damage event (near-zero HP) which interrupts the target's
 *     sprint and triggers iframes — the classic 1.8 "stun" feel.
 *   - No pull, no cooldown, no sounds.
 *
 * Reel-in is suppressed entirely so no velocity pull occurs.
 * Unenchanted rods are completely unaffected.
 */
public class HookBook extends EnchantmentBook implements Listener {

    // -------------------------------------------------------------------------
    // Constants
    // -------------------------------------------------------------------------

    /**
     * Pseudo-damage on bobber contact.
     * Must be > 0 so Bukkit fires a real EntityDamageByEntityEvent
     * (which triggers iframes + sprint-cancel). Imperceptible to the player.
     */
    private static final double CONTACT_DAMAGE = 0.0001;

    /**
     * Maps hookUUID → casterUUID for every FishHook cast from an enchanted rod.
     * ProjectileHitEvent does not expose the caster directly, so we store it here.
     */
    private static final Map<UUID, UUID> trackedHooks = new HashMap<>();

    // -------------------------------------------------------------------------
    // Constructor
    // -------------------------------------------------------------------------

    public HookBook() {
        this(1);
    }

    public HookBook(int level) {
        super(
                "Book of Hook",
                CorovaEnchantments.HOOK_ID,
                level,
                "book_hook",
                Set.of(Material.FISHING_ROD)
        );
    }

    // -------------------------------------------------------------------------
    // Event — ProjectileLaunchEvent
    //
    // Tag FishHooks cast from enchanted rods, storing hookUUID → casterUUID.
    // -------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof FishHook hook)) return;
        if (!(hook.getShooter() instanceof Player caster)) return;

        ItemStack rod = caster.getInventory().getItemInMainHand();
        if (!CorovaEnchantments.hasEnchant(rod, CorovaEnchantments.HOOK_ID)) {
            rod = caster.getInventory().getItemInOffHand();
            if (!CorovaEnchantments.hasEnchant(rod, CorovaEnchantments.HOOK_ID)) return;
        }

        trackedHooks.put(hook.getUniqueId(), caster.getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Event — ProjectileHitEvent  [Bobber Contact — Stun only]
    //
    // Fires the moment the bobber touches an entity. Applies pseudo-damage to
    // interrupt sprint and stagger the target (the 1.8 stun). No pull, no sound.
    // -------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof FishHook hook)) return;

        UUID casterUuid = trackedHooks.get(hook.getUniqueId());
        if (casterUuid == null) return;

        Entity hitEntity = event.getHitEntity();
        if (!(hitEntity instanceof LivingEntity target)) return;

        Player caster = hook.getServer().getPlayer(casterUuid);
        if (caster == null) return;

        // ── Safe zone check ──────────────────────────────────────────────────
        if (target instanceof Player victim) {
            if (CorovaGuard.getInstance().isPlayerInSafeZone(victim)) {
                CorovaGuard.sendSafeZoneMessage(caster);
                hook.remove();
                trackedHooks.remove(hook.getUniqueId());
                return;
            }

            // ── Team / friendly fire check ───────────────────────────────────
            CorovaTeams teamsInstance = CorovaTeams.getInstance();
            if (teamsInstance != null) {
                CorovaTeam attackerTeam = teamsInstance.getTeamManager().getTeamByPlayer(casterUuid);
                CorovaTeam victimTeam   = teamsInstance.getTeamManager().getTeamByPlayer(victim.getUniqueId());
                if (attackerTeam != null
                        && attackerTeam.equals(victimTeam)
                        && !attackerTeam.hasFriendlyFire()) {
                    hook.remove();
                    trackedHooks.remove(hook.getUniqueId());
                    return;
                }
            }
        }

        // ── Pseudo-damage: triggers sprint-cancel + iframes (the 1.8 stun) ──
        target.damage(CONTACT_DAMAGE, caster);
    }

    // -------------------------------------------------------------------------
    // Event — PlayerFishEvent  [Reel-in — suppressed]
    //
    // Cancel the reel-in entirely so no velocity pull is applied to the target.
    // -------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) return;

        FishHook hook = event.getHook();

        UUID casterUuid = trackedHooks.remove(hook.getUniqueId());
        if (casterUuid == null) return;

        // Cancel the event so Bukkit does not apply its built-in pull velocity.
        event.setCancelled(true);
        hook.remove();
    }

    // -------------------------------------------------------------------------
    // Cleanup — block hits / natural bobber expiry
    // -------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProjectileHitCleanup(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof FishHook hook)) return;
        if (event.getHitEntity() != null) return; // handled by onProjectileHit
        trackedHooks.remove(hook.getUniqueId());
    }

    // -------------------------------------------------------------------------
    // Cleanup — player disconnect
    // -------------------------------------------------------------------------
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        trackedHooks.values().removeIf(casterUuid -> casterUuid.equals(uuid));
    }
}