package com.example.corovaItems.ArmorTrims;

import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.Mutations.GoldenAegis;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;

public class TrimEventListener implements Listener {

    private final TrimManager trimManager = TrimManager.getInstance();

    // ── Armor change detection ─────────────────────────────────────────────────

    /**
     * Fired whenever the player clicks inside any inventory.
     * We schedule a 1-tick delayed refresh so the item has been moved
     * before we read the armor slots.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (isArmorSlotType(event.getSlotType())
                || isArmorSlot(event.getSlot())
                || event.isShiftClick()) {
            scheduleRefresh(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        scheduleRefresh(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlots().stream().anyMatch(this::isRawArmorSlot)) {
            scheduleRefresh(player);
        }
    }

    /**
     * Catches right-click-to-equip armor from the hotbar or hand.
     * This is the most common way players equip armor OUTSIDE of opening
     * their inventory — previously this path was completely unhandled,
     * causing trims to stay inactive until the player opened their inventory.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // Only care about the main hand to avoid double-firing
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) return;

        // Check if the item in hand is armor (trimmed or not — player may be
        // equipping new trimmed armor, or unequipping existing trimmed armor
        // back into the inventory by overwriting the slot).
        if (isArmorItem(item)) {
            scheduleRefresh(event.getPlayer());
        }
    }

    /**
     * Catches hotbar slot changes. If the player switches to/from a slot
     * holding armor while the off-hand or world interaction could change
     * what's worn, we refresh.  More importantly this fires when armor is
     * dispensed onto the player or picked up via inventory manipulation that
     * doesn't trigger InventoryClickEvent (e.g. plugin-driven equip).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        // Refresh if the previously-held OR newly-held slot has armor
        ItemStack prev = player.getInventory().getItem(event.getPreviousSlot());
        ItemStack next = player.getInventory().getItem(event.getNewSlot());
        if (isArmorItem(prev) || isArmorItem(next)) {
            scheduleRefresh(player);
        }
    }

    // Player dies and drops armor
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        trimManager.invalidate(event.getEntity());
    }

    // Player logs in — build profile immediately (20-tick delay, same as MysticArcanum)
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(
                MutationManager.getInstance().getPlugin(),
                () -> trimManager.refresh(event.getPlayer()), 20L);
    }

    // Player logs out — clean up
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        trimManager.evict(event.getPlayer().getUniqueId());
    }

    // ── Passive: Netherite Fire Immunity ──────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FIRE
                && event.getCause() != EntityDamageEvent.DamageCause.FIRE_TICK
                && event.getCause() != EntityDamageEvent.DamageCause.LAVA) return;

        PlayerTrimProfile profile = trimManager.getProfile(player);
        if (profile.netheriteCount == 0) return;

        // Pure set: complete immunity
        if (TrimCalculator.hasPureSet(TrimMaterialType.NETHERITE, profile)) {
            event.setCancelled(true);
            return;
        }

        // Spectrum: 20% cancel chance per piece (1=20%, 2=40%, 3=60%)
        double cancelChance = profile.netheriteCount * 0.20;
        if (Math.random() < cancelChance) {
            event.setCancelled(true);
        }
    }

    // ── Combat Passive: Redstone ──────────────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerHitEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        PlayerTrimProfile profile = trimManager.getProfile(player);

        // ── Redstone: Bloodied damage bonus ──
        double redstoneStrength = TrimCalculator.getSetBonusStrength(TrimMaterialType.REDSTONE, profile);
        if (redstoneStrength > 0) {
            double halfHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() * 0.5;
            if (player.getHealth() <= halfHealth) {
                event.setDamage(event.getDamage() * (1.0 + redstoneStrength * 0.50));
            }
        }
    }

    // ── Combat Passive: Quartz ────────────────────────────────────────────────
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerCritHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        PlayerTrimProfile profile = trimManager.getProfile(player);

        double quartzBonus = TrimCalculator.getQuartzCritBonus(profile);
        if (quartzBonus > 0 && event.isCritical()) {
            org.bukkit.attribute.AttributeInstance attr =
                    player.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
            double trueWeaponBase = (attr != null) ? attr.getBaseValue() : 1.0;
            event.setDamage(event.getDamage() + (trueWeaponBase * quartzBonus));
        }
    }

    // ── Redstone: Bloodied defense bonus ─────────────────────────────────────
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTakeDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        PlayerTrimProfile profile = trimManager.getProfile(player);
        double redstoneStrength = TrimCalculator.getSetBonusStrength(TrimMaterialType.REDSTONE, profile);
        if (redstoneStrength > 0) {
            double halfHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() * 0.5;
            if (player.getHealth() <= halfHealth) {
                event.setDamage(event.getDamage() * (1.0 - redstoneStrength * 0.30));
            }
        }
    }

    // ── Set Bonus: Lapis — Mana Regen bonus ──────────────────────────────────
    public static double getLapisRegenBonus(Player player) {
        PlayerTrimProfile profile = TrimManager.getInstance().getProfile(player);
        return TrimCalculator.getSetBonusStrength(TrimMaterialType.LAPIS, profile) * 0.50;
    }

    // ── Set Bonus: Permanent Effects ─────────────────────────────────────────
    public static void applyPermanentSetEffects(Player player, PlayerTrimProfile profile) {
        GoldenAegis.checkPlayer(player);
    }

    // ── SmithingTable: refresh lore with viewer context ───────────────────────
    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ItemStack result = event.getResult();
        if (result == null || result.getType() == org.bukkit.Material.AIR) return;

        if (result.hasItemMeta() && result.getItemMeta() instanceof org.bukkit.inventory.meta.ArmorMeta am) {
            if (am.hasTrim()) {
                if (event.getView().getPlayer() instanceof Player player) {
                    ItemStack clone = result.clone();
                    MutationManager.getInstance().updateLore(clone, player);
                    event.setResult(clone);
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void scheduleRefresh(Player player) {
        Bukkit.getScheduler().runTaskLater(
                MutationManager.getInstance().getPlugin(),
                () -> trimManager.refresh(player), 1L);
    }

    private boolean isArmorSlotType(InventoryType.SlotType slotType) {
        return slotType == InventoryType.SlotType.ARMOR;
    }

    private boolean isArmorSlot(int slot) {
        return slot >= 36 && slot <= 39;
    }

    private boolean isRawArmorSlot(int rawSlot) {
        return rawSlot >= 5 && rawSlot <= 8;
    }

    /** Returns true if the item is an armor piece (with or without a trim). */
    private boolean isArmorItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getItemMeta() instanceof ArmorMeta;
    }
}