package com.example.corovaItems.WeaponProperties;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Module class that adds 1.7-style sword blocking visuals & mechanics using 1.21+ features.
 * Based on the Vanilla Sword Blocking datapack/plugin approach.
 *
 * IMPORTANT: Uses Paper's Data Component API to set CONSUMABLE component with animation="block"
 * which triggers the blocking animation when right-clicking. Requires client 1.21.4+
 *
 * Blocking is disabled in two scenarios, handled by toggling the CONSUMABLE component
 * on the sword item itself — because the animation is client-driven, event cancellation
 * alone is too late to suppress it:
 *
 *   1. A shield is in the off-hand  → vanilla shield takes priority.
 *   2. A dual-wield weapon is in the off-hand → DualWielding takes priority.
 *
 * When either condition becomes true the CONSUMABLE/FOOD components are removed from every
 * sword in the player's inventory, so the client never treats it as a blocking item.
 * They are restored as soon as the condition clears.
 */
public final class SwordBlocking implements Listener {

    private static JavaPlugin plugin;

    private static final Set<UUID> blockingPlayers = new HashSet<>();
    private static final Map<UUID, BukkitTask> blockingTasks = new HashMap<>();
    private static final Map<UUID, Long> lastHandRaised = new HashMap<>();

    private static final Set<Material> SWORDS = EnumSet.of(
            Material.WOODEN_SWORD,
            Material.STONE_SWORD,
            Material.IRON_SWORD,
            Material.GOLDEN_SWORD,
            Material.DIAMOND_SWORD,
            Material.NETHERITE_SWORD
    );

    static {
        Material copper = Material.matchMaterial("COPPER_SWORD");
        if (copper != null) SWORDS.add(copper);
    }

    private static final Set<Material> OFFHAND_INTERACTABLES = EnumSet.of(
            Material.BOW, Material.CROSSBOW, Material.TRIDENT,
            Material.SPLASH_POTION, Material.LINGERING_POTION, Material.POTION,
            Material.SNOWBALL, Material.EGG, Material.ENDER_PEARL,
            Material.EXPERIENCE_BOTTLE, Material.WIND_CHARGE, Material.FIREWORK_ROCKET,
            Material.GOAT_HORN, Material.BRUSH, Material.SPYGLASS
    );

    /* ------------------------------------------------------ */
    /* INIT                                                   */
    /* ------------------------------------------------------ */

    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;

        Bukkit.getPluginManager().registerEvents(new SwordBlocking(), plugin);

        // Process all online players' inventories on startup
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                refreshSwordsForPlayer(player);
            }
            plugin.getLogger().info("[SwordBlocking] 1.7-style sword blocking enabled! Processed " +
                    Bukkit.getOnlinePlayers().size() + " player inventories.");
            plugin.getLogger().info("[SwordBlocking] Note: Blocking animation requires client 1.21.4+");
        }, 20L);
    }

    /* ------------------------------------------------------ */
    /* BLOCKING ELIGIBILITY                                   */
    /* ------------------------------------------------------ */

    /**
     * Returns true if the player is currently allowed to sword-block.
     *
     * Blocking is forbidden when:
     *  - A shield is in the off-hand (vanilla shield takes priority), OR
     *  - A dual-wield weapon is in the off-hand (DualWielding takes priority).
     */
    public static boolean canBlock(Player player) {
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand == null || offHand.getType().isAir()) return true;
        if (offHand.getType() == Material.SHIELD) return false;
        if (DualWielding.isDualWieldWeapon(offHand)) return false;
        if (OFFHAND_INTERACTABLES.contains(offHand.getType())) return false;
        return true;
    }

    /* ------------------------------------------------------ */
    /* COMPONENT MANAGEMENT                                   */
    /* ------------------------------------------------------ */

    /**
     * Refreshes every sword in the player's inventory to either have or not have
     * the CONSUMABLE blocking component, depending on whether they can currently block.
     *
     * This is the core fix: the blocking animation is entirely client-side and driven
     * by the item's data component. Event cancellation fires after the client has already
     * started the animation. By removing the CONSUMABLE/FOOD components, the client never
     * treats the sword as a blocking item in the first place.
     */
    public static void refreshSwordsForPlayer(Player player) {
        boolean allowed = canBlock(player);
        boolean anyChanged = false;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (!isSword(item)) continue;

            if (allowed) {
                if (ensureConsumable(item)) {
                    contents[i] = item;
                    anyChanged = true;
                }
            } else {
                if (removeConsumable(item)) {
                    contents[i] = item;
                    anyChanged = true;
                }
            }
        }

        if (anyChanged) {
            player.getInventory().setContents(contents);
        }
    }

    /**
     * Strips the CONSUMABLE and FOOD components from a sword so the client will not
     * trigger a blocking animation when the player right-clicks with it.
     *
     * @return true if the item was actually modified
     */
    private static boolean removeConsumable(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        boolean changed = false;
        try {
            if (item.getData(DataComponentTypes.CONSUMABLE) != null) {
                item.unsetData(DataComponentTypes.CONSUMABLE);
                changed = true;
            }
            if (item.getData(DataComponentTypes.FOOD) != null) {
                item.unsetData(DataComponentTypes.FOOD);
                changed = true;
            }
        } catch (Exception e) {
            if (plugin != null) plugin.getLogger().warning("[SwordBlocking] Failed to remove consumable: " + e.getMessage());
        }
        return changed;
    }

    /* ------------------------------------------------------ */
    /* EVENTS                                                 */
    /* ------------------------------------------------------ */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isSword(item)) return;

        // If blocking is not permitted, ensure the consumable component is absent.
        // IMPORTANT: do NOT call setUseItemInHand(DENY) here when a dual-wield weapon
        // is in the off-hand. DualWielding runs at HIGHEST priority and cancels the
        // event itself. Setting DENY at HIGH contaminates the shared interact context
        // for that tick in Paper 1.21, which suppresses the off-hand attack path for
        // axes (and other non-sword dual-wield weapons) before DualWielding can act.
        if (!canBlock(player)) {
            if (removeConsumable(item)) {
                player.getInventory().setItemInMainHand(item);
            }
            // Only deny item use when a shield is present or another non-DualWielding
            // reason blocks — never when DualWielding is the reason, so axes work.
            if (!DualWielding.isDualWieldWeapon(player.getInventory().getItemInOffHand())) {
                event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            }
            return;
        }

        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {

            // If clicking a block, only block if sneaking or if the block is not interactable
            if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                if (event.getClickedBlock().getType().isInteractable() && !player.isSneaking()) {
                    return;
                }
            }

            // Ensure sword is consumable so it triggers the 'using' animation
            if (ensureConsumable(item)) {
                player.getInventory().setItemInMainHand(item);
            }
            startBlocking(player);
        }
    }

    @EventHandler
    public void onAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;

        Player player = event.getPlayer();
        if (isBlocking(player)) {
            // 1.7 Block Hitting: allow attacking while blocking
            performBlockHit(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (isBlocking(player)) {
                // 1.7 blocking reduced damage by 50%
                EntityDamageEvent.DamageCause cause = event.getCause();
                if (cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK ||
                        cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK ||
                        cause == EntityDamageEvent.DamageCause.PROJECTILE ||
                        cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
                        cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {

                    event.setDamage(event.getDamage() * 0.5);

                    // Visual/Sound feedback for blocking
                    player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.2f);

                    // Handle Parry mutation
                    if (event instanceof org.bukkit.event.entity.EntityDamageByEntityEvent edbe) {
                        handleParry(player, edbe);
                    }
                }
            }
        }
    }

    private static final Map<UUID, Long> parryExpirations = new HashMap<>();

    private void handleParry(Player victim, org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        ItemStack sword = victim.getInventory().getItemInMainHand();
        if (!isSword(sword)) return;

        com.example.corovaItems.ItemMutations.MutationManager mm = com.example.corovaItems.ItemMutations.MutationManager.getInstance();
        if (mm == null) return;

        int level = mm.getMutationLevel(sword, com.example.corovaItems.ItemMutations.MutationType.PARRY);
        if (level <= 0) return;

        double chance = (level == 1) ? 0.04 : 0.08;
        if (Math.random() < chance) {
            if (event.getDamager() instanceof LivingEntity attacker) {
                double reduction = (level == 1) ? -0.25 : -0.50;
                org.bukkit.attribute.AttributeInstance attr = attacker.getAttribute(Attribute.ATTACK_SPEED);
                if (attr != null) {
                    org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "parry_debuff");
                    attr.removeModifier(key);
                    org.bukkit.attribute.AttributeModifier modifier = new org.bukkit.attribute.AttributeModifier(
                            key, reduction, org.bukkit.attribute.AttributeModifier.Operation.MULTIPLY_SCALAR_1, org.bukkit.inventory.EquipmentSlotGroup.ANY
                    );
                    attr.addModifier(modifier);

                    long expiration = System.currentTimeMillis() + 2000;
                    parryExpirations.put(attacker.getUniqueId(), expiration);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (attacker.isValid() && parryExpirations.getOrDefault(attacker.getUniqueId(), 0L) <= System.currentTimeMillis()) {
                            org.bukkit.attribute.AttributeInstance a = attacker.getAttribute(Attribute.ATTACK_SPEED);
                            if (a != null) a.removeModifier(key);
                            parryExpirations.remove(attacker.getUniqueId());
                        }
                    }, 40L); // 2 second duration

                    victim.sendMessage(org.bukkit.ChatColor.GOLD + "§lParry!");
                    victim.playSound(victim.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.5f, 1.8f);
                    attacker.getWorld().spawnParticle(org.bukkit.Particle.CRIT, attacker.getLocation().add(0, 1, 0), 10);
                }
            }
        }
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        stopBlocking(event.getPlayer());

        // Re-evaluate after the slot change is committed
        Bukkit.getScheduler().runTask(plugin, () -> {
            refreshSwordsForPlayer(event.getPlayer());
        });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            refreshSwordsForPlayer(event.getPlayer());
        }, 10L);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isSword(event.getItem().getItemStack())) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!canBlock(player)) stopBlocking(player);
            refreshSwordsForPlayer(player);
        });
    }

    /**
     * Fires when the player presses F to swap main/off-hand items.
     * Always stop blocking, then re-evaluate component state after the swap.
     */
    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        stopBlocking(event.getPlayer());

        Bukkit.getScheduler().runTask(plugin, () -> {
            refreshSwordsForPlayer(event.getPlayer());
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stopBlocking(event.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isSword(event.getItemDrop().getItemStack())) {
            stopBlocking(event.getPlayer());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!canBlock(player)) {
                stopBlocking(player);
            }
            refreshSwordsForPlayer(player);
        });
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        // Prevent actually consuming the sword
        if (isSword(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /* ------------------------------------------------------ */
    /* BLOCKING STATE                                         */
    /* ------------------------------------------------------ */

    private static void startBlocking(Player player) {
        UUID uuid = player.getUniqueId();

        if (blockingPlayers.contains(uuid)) return;

        blockingPlayers.add(uuid);
        lastHandRaised.put(uuid, System.currentTimeMillis());

        // Apply slowness effect like in 1.7
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS,
                Integer.MAX_VALUE,
                0,
                false,
                false,
                false
        ));

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            if (!player.isOnline() || !canBlock(player)) {
                stopBlocking(player);
                return;
            }

            if (player.isHandRaised()) {
                lastHandRaised.put(uuid, System.currentTimeMillis());
            }

            long now = System.currentTimeMillis();
            long lastRaised = lastHandRaised.getOrDefault(uuid, 0L);
            boolean recentlyRaised = (now - lastRaised) < 150; // 3 tick grace period

            // Must still be holding sword + hand must be raised (or recently raised)
            if (!isSword(player.getInventory().getItemInMainHand())
                    || (!player.isHandRaised() && !recentlyRaised)) {
                stopBlocking(player);
            }

        }, 1L, 1L);

        blockingTasks.put(uuid, task);
    }

    private static void stopBlocking(Player player) {
        UUID uuid = player.getUniqueId();

        if (!blockingPlayers.remove(uuid)) return;

        // Remove slowness effect
        player.removePotionEffect(PotionEffectType.SLOWNESS);

        lastHandRaised.remove(uuid);
        BukkitTask task = blockingTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    /* ------------------------------------------------------ */
    /* BLOCK HITTING                                          */
    /* ------------------------------------------------------ */

    private void performBlockHit(Player player) {
        // Use Attribute for reach distance if available (Paper/1.21+)
        double reach = 3.0;
        try {
            Attribute reachAttr = Attribute.ENTITY_INTERACTION_RANGE;
            if (player.getAttribute(reachAttr) != null) {
                reach = player.getAttribute(reachAttr).getValue();
            }
        } catch (Exception e) {
            // Fallback for older versions or non-Paper servers
            if (player.getGameMode().name().contains("CREATIVE")) {
                reach = 5.0;
            }
        }

        Entity target = null;
        try {
            target = player.getTargetEntity((int) Math.ceil(reach), false);
        } catch (Exception e) {
            target = getTargetEntityManual(player, reach);
        }

        if (target instanceof LivingEntity && !target.equals(player)) {
            double distSq = player.getEyeLocation().distanceSquared(target.getLocation());
            double maxDistSq = (reach + 1) * (reach + 1);

            if (distSq <= maxDistSq) {
                UUID uuid = player.getUniqueId();
                CorovaCombat.abilityBypass.add(uuid);
                try {
                    player.attack(target);

                    ItemStack item = player.getInventory().getItemInMainHand();
                    if (item != null && !item.getType().isAir()) {
                        CorovaCombat.spawnManualSweep(player, target, item);
                    }
                } finally {
                    CorovaCombat.abilityBypass.remove(uuid);
                }
            }
        }
    }

    private Entity getTargetEntityManual(Player player, double range) {
        List<Entity> nearbyEntities = player.getNearbyEntities(range, range, range);
        Entity target = null;
        double closestDistSq = Double.MAX_VALUE;

        for (Entity entity : nearbyEntities) {
            if (entity instanceof LivingEntity && !entity.equals(player)) {
                double distSq = player.getEyeLocation().distanceSquared(entity.getLocation());
                if (distSq < closestDistSq && distSq <= range * range) {
                    if (player.hasLineOfSight(entity)) {
                        closestDistSq = distSq;
                        target = entity;
                    }
                }
            }
        }

        return target;
    }

    /* ------------------------------------------------------ */
    /* UTIL                                                   */
    /* ------------------------------------------------------ */

    private static boolean isSword(ItemStack item) {
        return item != null && !item.getType().isAir() && SWORDS.contains(item.getType());
    }

    /**
     * Makes a sword consumable to trigger the blocking animation.
     * Uses Paper's Data Component API to set CONSUMABLE with animation="block".
     *
     * @return true if the item was modified
     */
    private static boolean ensureConsumable(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;

        try {
            boolean changed = false;

            FoodProperties currentFood = item.getData(DataComponentTypes.FOOD);
            if (currentFood == null || currentFood.nutrition() != 0 || currentFood.saturation() != 0f || !currentFood.canAlwaysEat()) {
                FoodProperties food = FoodProperties.food()
                        .nutrition(0)
                        .saturation(0f)
                        .canAlwaysEat(true)
                        .build();
                item.setData(DataComponentTypes.FOOD, food);
                changed = true;
            }

            Consumable currentConsumable = item.getData(DataComponentTypes.CONSUMABLE);
            boolean needsUpdate = currentConsumable == null
                    || currentConsumable.animation() != ItemUseAnimation.BLOCK
                    || Math.abs(currentConsumable.consumeSeconds() - 3600f) > 0.01f
                    || currentConsumable.hasConsumeParticles();

            if (needsUpdate) {
                Consumable consumable = Consumable.consumable()
                        .animation(ItemUseAnimation.BLOCK)
                        .consumeSeconds(3600f)
                        .hasConsumeParticles(false)
                        .build();
                item.setData(DataComponentTypes.CONSUMABLE, consumable);
                changed = true;
            }

            return changed;
        } catch (Exception e) {
            if (plugin != null) {
                plugin.getLogger().warning("Failed to make sword consumable: " + e.getMessage());
                e.printStackTrace();
            }
            return false;
        }
    }

    /* ------------------------------------------------------ */
    /* API                                                    */
    /* ------------------------------------------------------ */

    public static boolean isBlocking(Player player) {
        return blockingPlayers.contains(player.getUniqueId());
    }

    public static void forceStop(Player player) {
        stopBlocking(player);
    }

    /**
     * Manually process a sword to make it consumable.
     * Useful for when items are created programmatically.
     */
    public static void processSword(ItemStack sword) {
        if (isSword(sword)) {
            ensureConsumable(sword);
        }
    }
}