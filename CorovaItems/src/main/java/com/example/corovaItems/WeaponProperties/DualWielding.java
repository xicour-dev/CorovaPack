package com.example.corovaItems.WeaponProperties;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Module class that adds dual wielding for axes and swords.
 * When a weapon is held in the off-hand, right-clicking triggers an attack.
 */
public final class DualWielding implements Listener {

    /*
     * DualWielding — How it works
     * ───────────────────────────────────────────────────────────────────────────
     * This class enables dual-wielding combat for swords and axes in both hands.
     * Normally Minecraft only processes attacks from the main hand; this class
     * intercepts right-click events (PlayerInteractEvent and
     * PlayerInteractEntityEvent) to simulate an off-hand attack whenever the
     * player has a sword or axe in their off-hand slot.
     *
     * Attack simulation (performOffHandAttack):
     *   - A raycast finds the target entity within the player's interaction range.
     *   - The items in each hand are temporarily swapped so that Bukkit's
     *     player.attack() call reads the correct weapon attributes (damage,
     *     attack speed, enchantments, etc.) from the "main hand" slot.
     *   - After the attack resolves, the items are swapped back immediately.
     *   - Durability is consumed manually (respecting the Unbreaking enchantment),
     *     and the item is removed if it breaks during the attack.
     *
     * Cooldown system:
     *   - An independent off-hand cooldown (offhandCooldown) is derived from the
     *     weapon's attack speed and displayed via the vanilla item-cooldown overlay.
     *   - A shared cooldown (SHARED_COOLDOWN_MS) prevents both hands from firing
     *     within 200 ms of each other, keeping combat balanced.
     *   - A hold-detection threshold (HOLD_THRESHOLD_MS) blocks automatic
     *     repeated swings from holding right-click.
     *   - Player#setCooldown(Material, ticks) is keyed by item Material, not by
     *     slot/hand — it greys out the cooldown swipe on every hotbar slot that
     *     holds that Material. If both hands wield the same weapon type, showing
     *     the off-hand's cooldown this way also (incorrectly) greys out the
     *     main-hand slot. To avoid this, the visual overlay is only shown when
     *     the off-hand weapon's Material differs from whatever is in the main
     *     hand; when they match, the overlay is skipped entirely rather than
     *     shown inaccurately — the actual off-hand cooldown is still enforced
     *     via the offhandCooldown map regardless of whether the overlay renders.
     *   - Separately, the vanilla crosshair "hit strength" indicator (the fill-up
     *     icon, distinct from the hotbar swipe above) is driven by an internal,
     *     unhanded attack-strength ticker that player.attack() resets on every
     *     attack regardless of which hand triggered it. Since there's no public
     *     API for this and the crosshair's fill math always reads the main-hand
     *     weapon's attack speed (so it could never accurately represent the
     *     off-hand's cooldown even if we tried), it's forced back to "fully
     *     charged" via reflection right after each off-hand swing so it doesn't
     *     falsely show the main hand as being on cooldown.
     *
     * Edge-case handling:
     *   - Axe right-click interactions (stripping logs, scraping copper, etc.) are
     *     cancelled when an axe is in the off-hand so they don't suppress the swing.
     *   - Main-hand interactable items (bows, potions, shields, fishing rods, etc.)
     *     take priority and will suppress the off-hand attack.
     *   - I-frames (NoDamageTicks) on the target are cleared before each off-hand
     *     hit so the attack always registers, regardless of recent damage taken.
     *   - All per-player state is cleaned up on disconnect (PlayerQuitEvent).
     * ───────────────────────────────────────────────────────────────────────────
     */

    private static JavaPlugin plugin;
    private static NamespacedKey attackSpeedOverrideKey;
    private static final Map<UUID, Long> offhandCooldown = new HashMap<>();
    public static final Set<UUID> offHandAttackInProgress = new HashSet<>();

    private static final Map<UUID, Long> lastMainHandSwing = new HashMap<>();
    private static final Map<UUID, Long> lastOffHandSwing = new HashMap<>();
    private static final Map<UUID, Long> lastRightClickTime = new HashMap<>();

    private static final long SHARED_COOLDOWN_MS = 200;
    private static final long HOLD_THRESHOLD_MS = 250;

    // Reflection handles for forcing the vanilla attack-strength ticker (crosshair
    // "hit strength" indicator) back to "fully charged" after an off-hand swing.
    // Resolved lazily and cached; if resolution ever fails (e.g. an incompatible
    // server version/mapping), nmsReflectionUnavailable is latched so we stop
    // retrying and just accept the crosshair may briefly show a false cooldown.
    private static Method getHandleMethod;
    private static Field attackStrengthTickerField;
    private static boolean nmsReflectionUnavailable = false;

    private static boolean isDualWieldWeaponMaterial(Material mat) {
        if (mat == null) return false;
        String name = mat.name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE");
    }

    private static final Set<Material> MAIN_HAND_INTERACTABLES = EnumSet.of(
            Material.BOW, Material.CROSSBOW, Material.TRIDENT,
            Material.SPLASH_POTION, Material.LINGERING_POTION, Material.POTION,
            Material.SNOWBALL, Material.EGG, Material.ENDER_PEARL,
            Material.EXPERIENCE_BOTTLE, Material.WIND_CHARGE, Material.FIREWORK_ROCKET,
            Material.SHIELD, Material.GOAT_HORN, Material.BRUSH, Material.SPYGLASS,
            Material.FISHING_ROD,
            // Breeding & Animal Interaction
            Material.WHEAT, Material.WHEAT_SEEDS, Material.PUMPKIN_SEEDS, Material.MELON_SEEDS,
            Material.BEETROOT_SEEDS, Material.TORCHFLOWER_SEEDS, Material.PITCHER_POD,
            Material.BAMBOO, Material.SEAGRASS, Material.CRIMSON_FUNGUS, Material.WARPED_FUNGUS,
            Material.HAY_BLOCK,
            // Utility Interactions
            Material.LEAD, Material.NAME_TAG, Material.SADDLE, Material.SHEARS,
            Material.BUCKET, Material.WATER_BUCKET, Material.LAVA_BUCKET, Material.MILK_BUCKET,
            Material.POWDER_SNOW_BUCKET, Material.TADPOLE_BUCKET, Material.AXOLOTL_BUCKET,
            Material.TROPICAL_FISH_BUCKET, Material.COD_BUCKET, Material.SALMON_BUCKET,
            Material.PUFFERFISH_BUCKET
    );

    /* ------------------------------------------------------ */
    /* INIT                                                   */
    /* ------------------------------------------------------ */

    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
        attackSpeedOverrideKey = new NamespacedKey(plugin, "dualwielding_offhand_speed_override");
        Bukkit.getPluginManager().registerEvents(new DualWielding(), plugin);
    }

    /* ------------------------------------------------------ */
    /* EVENTS                                                 */
    /* ------------------------------------------------------ */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Action action = event.getAction();

        // Track main hand swings (left clicks)
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            lastMainHandSwing.put(uuid, System.currentTimeMillis());
            return;
        }

        ItemStack offHandItem = player.getInventory().getItemInOffHand();

        if (!isDualWieldWeapon(offHandItem)) return;

        boolean isAxe = offHandItem.getType().name().endsWith("_AXE");

        // Prioritize main-hand interactions for bows, potions, food, etc.
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        if (mainHandItem != null && (mainHandItem.getType().isEdible() || MAIN_HAND_INTERACTABLES.contains(mainHandItem.getType()))) {
            return;
        }

        // When an axe is in the offhand, its strip/scrape/wax interaction fires under
        // EquipmentSlot.HAND and completely suppresses the offhand swing — even with
        // nothing in the main hand, and even when dual-axing. Cancel that HAND-slot
        // right-click unconditionally so the offhand axe can swing instead.
        if (isAxe && event.getHand() == EquipmentSlot.HAND
                && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            return;
        }

        // For swords, only process the OFF_HAND firing to avoid double-triggering.
        if (!isAxe && event.getHand() != EquipmentSlot.OFF_HAND) return;

        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            long now = System.currentTimeMillis();

            // Prevent holding right click for automatic swinging
            long lastClick = lastRightClickTime.getOrDefault(uuid, 0L);
            lastRightClickTime.put(uuid, now);
            if (now - lastClick < HOLD_THRESHOLD_MS) {
                return;
            }

            // Slight cooldown between main-hand and off-hand swings
            long lastMain = lastMainHandSwing.getOrDefault(uuid, 0L);
            if (now - lastMain < SHARED_COOLDOWN_MS) {
                return;
            }

            // If clicking a block, only trigger if sneaking or if the block is not interactable
            if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                if (event.getClickedBlock().getType().isInteractable() && !player.isSneaking()) {
                    return;
                }
            }

            if (isOnCooldown(player)) return;

            performOffHandAttack(player);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        ItemStack offHandItem = player.getInventory().getItemInOffHand();

        if (!isDualWieldWeapon(offHandItem)) return;
        if (event.getHand() != EquipmentSlot.OFF_HAND) return;

        // Prioritize main-hand interactions for bows, potions, etc.
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        if (mainHandItem != null && MAIN_HAND_INTERACTABLES.contains(mainHandItem.getType())) {
            return;
        }

        long now = System.currentTimeMillis();

        // Prevent holding right click for automatic swinging
        long lastClick = lastRightClickTime.getOrDefault(uuid, 0L);
        lastRightClickTime.put(uuid, now);
        if (now - lastClick < HOLD_THRESHOLD_MS) {
            return;
        }

        // Slight cooldown between main-hand and off-hand swings
        long lastMain = lastMainHandSwing.getOrDefault(uuid, 0L);
        if (now - lastMain < SHARED_COOLDOWN_MS) {
            return;
        }

        if (isOnCooldown(player)) return;

        performOffHandAttack(player);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        if (CorovaCombat.manualSweepActive.contains(uuid)) return;

        long now = System.currentTimeMillis();

        // Suppress vanilla's automatic sweep logic for off-hand attacks to fix "always-sweep" bug.
        // For main-hand attacks, we let vanilla handle it normally.
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            if (offHandAttackInProgress.contains(uuid) || CorovaCombat.abilityBypass.contains(uuid)) {
                event.setCancelled(true);
            }
            return;
        }

        if (offHandAttackInProgress.contains(uuid)) {
            // Off-hand attack (this event is triggered by player.attack(target) in performOffHandAttack)
            lastOffHandSwing.put(uuid, now);
        } else {
            // Main-hand attack
            if (isDualWielding(player)) {
                long lastOff = lastOffHandSwing.getOrDefault(uuid, 0L);
                if (now - lastOff < SHARED_COOLDOWN_MS) {
                    event.setCancelled(true);
                    return;
                }
            }
            lastMainHandSwing.put(uuid, now);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        offhandCooldown.remove(uuid);
        lastMainHandSwing.remove(uuid);
        lastOffHandSwing.remove(uuid);
        lastRightClickTime.remove(uuid);
    }

    /* ------------------------------------------------------ */
    /* ATTACK LOGIC                                           */
    /* ------------------------------------------------------ */

    private boolean applyDurabilityDamage(Player player, ItemStack item) {
        if (player.getGameMode() == GameMode.CREATIVE) return false;

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) return false;

        int unbreakingLevel = item.getEnchantmentLevel(Enchantment.UNBREAKING);

        // Unbreaking formula for tools/weapons: 1 / (level + 1) chance to take damage
        if (Math.random() < (1.0 / (unbreakingLevel + 1))) {
            int newDamage = damageable.getDamage() + 1;
            int maxDurability = item.getType().getMaxDurability();

            if (newDamage >= maxDurability) {
                // Item broke
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                return true;
            } else {
                damageable.setDamage(newDamage);
                item.setItemMeta(damageable);
            }
        }
        return false;
    }

    private void performOffHandAttack(Player player) {
        ItemStack offHandItem = player.getInventory().getItemInOffHand();
        if (offHandItem == null || offHandItem.getType() == Material.AIR) return;

        // Raycast to find target
        double reach = 3.0;
        try {
            Attribute reachAttr = Attribute.ENTITY_INTERACTION_RANGE;
            if (player.getAttribute(reachAttr) != null) {
                reach = player.getAttribute(reachAttr).getValue();
            }
        } catch (Exception e) {
            // Fallback for reach
        }

        Entity target = player.getTargetEntity((int) Math.ceil(reach), false);

        // Visual swing animation
        player.swingOffHand();
        lastOffHandSwing.put(player.getUniqueId(), System.currentTimeMillis());

        // Swap hands briefly to fake a swing with correct properties
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();

        // Compute the offhand weapon's true effective attack speed BEFORE swapping, using
        // baseline weapon speeds rather than the live attribute (see
        // computeSimulatedAttackSpeed's javadoc for why).
        double speed = computeSimulatedAttackSpeed(player, mainHandItem, offHandItem);

        // Player#setCooldown(Material, ticks) is keyed purely by Material and applies
        // to every hotbar/hand slot holding that Material — there's no slot-specific
        // vanilla cooldown overlay. If both hands hold the same weapon type, showing
        // the off-hand's cooldown this way would also grey out the main-hand slot,
        // making it look like the main hand is on cooldown when it isn't. Detect that
        // case here (before the swap) so setCooldown() can skip the visual entirely
        // rather than display something inaccurate.
        boolean sameMaterialInBothHands = mainHandItem != null
                && offHandItem.getType() == mainHandItem.getType();

        player.getInventory().setItemInMainHand(offHandItem);
        player.getInventory().setItemInOffHand(mainHandItem);

        try {
            setCooldown(player, offHandItem, speed, sameMaterialInBothHands);

            if (target instanceof LivingEntity && !target.equals(player)) {
                offHandAttackInProgress.add(player.getUniqueId());

                // Manually clear I-frames to ensure the attack lands immediately.
                // Only zero noDamageTicks for the instant of this hit, then restore
                // maximumNoDamageTicks next tick — leaving it pinned at 0 permanently
                // stalls unrelated systems (e.g. Player#getAttackCooldown(), which
                // mutation procs and vanilla crits/sweeps gate on) for any entity that
                // is simultaneously taking damage while also attacking.
                LivingEntity livingTarget = (LivingEntity) target;
                livingTarget.setNoDamageTicks(0);
                livingTarget.setMaximumNoDamageTicks(0);
                if (plugin != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (livingTarget.isValid()) {
                            livingTarget.setMaximumNoDamageTicks(20);
                        }
                    });
                }

                // ── Force the LIVE ATTACK_SPEED attribute to reflect the off-hand
                //    weapon's simulated speed for the instant of this attack. ──
                //
                // Swapping ItemStacks into the main-hand slot via setItemInMainHand()
                // does NOT synchronously recompute equipment-based attribute modifiers
                // — that diffing (comparing this tick's equipment against last tick's
                // snapshot) only runs once per server tick, inside the entity's own
                // tick() method. Since the swap + attack() call all happen within this
                // single synchronous method, player.attack() would otherwise read the
                // STALE attribute value belonging to whatever is truly in the main hand
                // (or the bare-hand base of 4.0 if empty) — regardless of what weapon
                // we just simulated into that slot. This is the actual cause of the
                // off-hand "inheriting" the main hand's cooldown: the item swap fixes
                // enchantment/material-based logic (which reads the ItemStack directly,
                // no lag), but silently does nothing for attribute-driven logic like the
                // vanilla attack-cooldown/attack-strength scaling used by attack().
                //
                // MULTIPLY_SCALAR_1 is used because it's applied last, multiplicatively,
                // on top of the attribute's fully-resolved current total — so setting
                // amount = (desiredSpeed / currentTotal) - 1 forces the live value to
                // become exactly desiredSpeed while transparently preserving whatever
                // is already contributing to currentTotal (Swift Strike, other gear,
                // potion-like effects, etc.), without needing to know what they are.
                AttributeInstance speedAttr = player.getAttribute(Attribute.ATTACK_SPEED);
                AttributeModifier speedOverride = null;
                if (speedAttr != null) {
                    double liveTotal = speedAttr.getValue();
                    if (liveTotal > 0.0001 && Math.abs(liveTotal - speed) > 0.0001) {
                        double factor = (speed / liveTotal) - 1.0;
                        speedOverride = new AttributeModifier(
                                attackSpeedOverrideKey, factor, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
                        speedAttr.addTransientModifier(speedOverride);
                    }
                }

                try {
                    // Perform attack using main hand (now holding the off-hand item)
                    player.attack(target);
                } finally {
                    // Always strip the override immediately so it can never leak into
                    // a subsequent main-hand swing or a differently-timed off-hand swing.
                    if (speedOverride != null) {
                        speedAttr.removeModifier(speedOverride);
                    }
                }

                // player.attack() above just reset the shared attack-strength ticker as
                // a side effect (it doesn't distinguish which hand triggered it). Force
                // it back to fully charged so the main-hand crosshair indicator doesn't
                // show a false cooldown from a swing it never actually made.
                clearMainHandAttackIndicator(player);

                // Refresh references from inventory to capture updates (e.g. mutations)
                offHandItem = player.getInventory().getItemInMainHand();
                mainHandItem = player.getInventory().getItemInOffHand();

                if (offHandItem != null && applyDurabilityDamage(player, offHandItem)) {
                    offHandItem = null;
                }

                // Manually trigger sweep if applicable (now handles all swords and clears I-frames).
                // Vanilla's player.attack(target) call above already broadcasts the sweep
                // particle/sound on its own whenever the sword + on-ground + not-sprinting
                // conditions are met — independent of the (cancelled) ENTITY_SWEEP_ATTACK
                // damage event — so we only need the damage application here, not a second
                // particle/sound (that duplication was the "double sweep particles" bug).
                if (offHandItem != null) {
                    CorovaCombat.spawnManualSweep(player, target, offHandItem, false);
                }
            }
        } finally {
            offHandAttackInProgress.remove(player.getUniqueId());
            // Swap back immediately
            player.getInventory().setItemInMainHand(mainHandItem);
            player.getInventory().setItemInOffHand(offHandItem);
        }
    }

    /**
     * Computes the ATTACK_SPEED value the player would have if {@code simulatedWeapon}
     * were equipped in the main hand instead of {@code actualMainHand}, based on the
     * player's CURRENT live attribute value (so external multiplicative bonuses like
     * Swift Strike's {@code MULTIPLY_SCALAR_1} modifier are preserved) with the two
     * items' own baseline speeds swapped out proportionally.
     *
     * <p>Uses {@link CorovaCombat#getBaselineAttacksPerSecond(Material)} rather than
     * reading {@code ItemMeta#getAttributeModifiers()} directly — stock vanilla weapons
     * don't carry an explicit attribute modifier in their meta/NBT (their speed comes
     * from the item type itself), so that lookup silently returns nothing for ordinary
     * swords/axes.</p>
     *
     * <p>Vanilla attribute math is {@code (base + flatModifiers) * (1 + scalarMultipliers)}.
     * The baseline table already represents {@code (base + flatModifiers)} for a given
     * weapon with no scalar bonus applied, so {@code liveValue / mainHandBaseline}
     * recovers the scalar multiplier currently in effect (e.g. 1.4 for Swift Strike's
     * +40%) — applying that same factor to the off-hand weapon's own baseline carries
     * the bonus over proportionally instead of as a flat offset, which would otherwise
     * under- or over-shoot depending on the weapon pairing since Swift Strike is a true
     * percentage boost, not a flat add.</p>
     */
    private double computeSimulatedAttackSpeed(Player player, ItemStack actualMainHand, ItemStack simulatedWeapon) {
        AttributeInstance attr = player.getAttribute(Attribute.ATTACK_SPEED);
        double liveValue = (attr != null) ? attr.getValue() : 4.0;

        Material actualMainHandMaterial = (actualMainHand != null) ? actualMainHand.getType() : Material.AIR;
        Material simulatedMaterial = (simulatedWeapon != null) ? simulatedWeapon.getType() : Material.AIR;

        double actualMainHandBaseline = CorovaCombat.getBaselineAttacksPerSecond(actualMainHandMaterial);
        double simulatedBaseline = CorovaCombat.getBaselineAttacksPerSecond(simulatedMaterial);

        double buffMultiplier = (actualMainHandBaseline > 0) ? liveValue / actualMainHandBaseline : 1.0;
        return simulatedBaseline * buffMultiplier;
    }

    /* ------------------------------------------------------ */
    /* CROSSHAIR ATTACK-INDICATOR FIX                          */
    /* ------------------------------------------------------ */

    private static Object getNmsHandle(Player player) throws Exception {
        if (getHandleMethod == null) {
            Method m = player.getClass().getMethod("getHandle");
            m.setAccessible(true);
            getHandleMethod = m;
        }
        return getHandleMethod.invoke(player);
    }

    private static Field resolveAttackStrengthTickerField(Object nmsEntity) throws NoSuchFieldException {
        if (attackStrengthTickerField != null) return attackStrengthTickerField;
        Class<?> c = nmsEntity.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField("attackStrengthTicker");
                f.setAccessible(true);
                attackStrengthTickerField = f;
                return f;
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException("attackStrengthTicker");
    }

    /**
     * Forces the vanilla attack-strength ticker (the crosshair "hit strength" fill
     * indicator) back to a fully-charged state immediately after an off-hand swing.
     *
     * <p>{@code player.attack(target)} resets this ticker to 0 as a side effect of
     * any attack, server-side — it belongs to the entity as a whole, not to a
     * specific hand. Left alone, that reset makes the main hand's crosshair
     * indicator appear to be recharging even though the main hand was never
     * swung. There's no public Bukkit/Paper API to read or write this value, and
     * the crosshair's fill percentage is always computed from the main-hand
     * weapon's attack speed anyway (so it could never accurately represent the
     * off-hand's own cooldown even if we set a "correct" intermediate value) —
     * so rather than attempt a fake accurate fill, this simply clears it.</p>
     *
     * <p>Resolution is cached after the first successful call. If it ever fails
     * (e.g. a server version/mapping where the field has a different name),
     * {@code nmsReflectionUnavailable} is latched so we log once and stop
     * retrying, rather than throwing on every subsequent swing.</p>
     */
    private void clearMainHandAttackIndicator(Player player) {
        if (nmsReflectionUnavailable) return;
        try {
            Object handle = getNmsHandle(player);
            Field field = resolveAttackStrengthTickerField(handle);
            // Any value at or above the main hand's cooldown period (in ticks)
            // saturates the vanilla scale calculation to 1.0 (fully charged).
            // 100 ticks (5s) comfortably exceeds every vanilla weapon's cooldown.
            field.setInt(handle, 100);
        } catch (Exception e) {
            nmsReflectionUnavailable = true;
            if (plugin != null) {
                plugin.getLogger().warning(
                        "[DualWielding] Could not reset the attack-strength indicator via reflection " +
                                "(incompatible server version/mappings) — the main-hand crosshair may briefly " +
                                "show a false cooldown after off-hand swings. Cause: " + e);
            }
        }
    }

    /* ------------------------------------------------------ */
    /* UTIL                                                   */
    /* ------------------------------------------------------ */

    public static boolean isDualWielding(Player player) {
        return isDualWieldWeapon(player.getInventory().getItemInOffHand());
    }

    public static boolean isDualWieldWeapon(ItemStack item) {
        return item != null && isDualWieldWeaponMaterial(item.getType());
    }

    private boolean isOnCooldown(Player player) {
        long now = System.currentTimeMillis();
        long lastAttack = offhandCooldown.getOrDefault(player.getUniqueId(), 0L);
        return now < lastAttack;
    }

    /**
     * Tracks the off-hand's internal cooldown (always) and optionally mirrors it to
     * the vanilla item-cooldown overlay via {@link Player#setCooldown(Material, int)}.
     *
     * <p>That vanilla call is keyed by {@link Material}, not by hand/slot, so it will
     * grey out the cooldown swipe on every slot holding that Material — including the
     * main hand if it happens to hold the same weapon type. {@code suppressVisual}
     * should be {@code true} in that case so we skip the overlay rather than show an
     * inaccurate cooldown on the main hand. The real off-hand cooldown enforcement
     * (via {@code offhandCooldown} / {@link #isOnCooldown(Player)}) is unaffected
     * either way, since it doesn't depend on the vanilla overlay at all.</p>
     */
    private void setCooldown(Player player, ItemStack item, double attackSpeed, boolean suppressVisual) {
        if (item == null || item.getType() == Material.AIR) return;

        long cooldownMs = (long) (1000.0 / attackSpeed);
        offhandCooldown.put(player.getUniqueId(), System.currentTimeMillis() + cooldownMs);

        if (!suppressVisual) {
            // Visual cooldown overlay — only safe to show when the main hand holds a
            // different Material, so it can't be mistaken for a main-hand cooldown.
            player.setCooldown(item.getType(), (int) (cooldownMs / 50));
        }
    }

}