package com.example.corovaItems.Enchantments.GreaterEnchantmentSystem;

import com.example.corovaItems.ItemManager;
import com.example.corovaItems.ItemMutations.MutationManager;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;

import java.util.*;
import java.util.function.BiPredicate;

/**
 * Enchantment handling for scythe weapons at both the enchanting table and anvil.
 *
 * All enchantment level reads use {@link VanillaEnchantDisplay#getTrueLevel} so that
 * PDC-stored over-cap levels are respected in all logic paths.  The deferred enchant
 * application task calls {@link VanillaEnchantDisplay#applyWithDisplay} for every
 * enchantment so that levels > 10 are properly stored and shown in lore.
 */
public class ScytheAnvilEnchantments implements Listener {

    public static void setTierChecker(BiPredicate<Player, Integer> checker) {
        EnchantmentTierManager.setTierChecker(checker);
    }

    private static final List<Material> SCYTHE_MATERIALS;
    static {
        List<Material> materials = new ArrayList<>();
        materials.add(Material.WOODEN_HOE);
        materials.add(Material.STONE_HOE);
        materials.add(Material.IRON_HOE);
        materials.add(Material.DIAMOND_HOE);
        materials.add(Material.NETHERITE_HOE);
        Material copperHoe = Material.matchMaterial("COPPER_HOE");
        if (copperHoe != null) materials.add(copperHoe);
        SCYTHE_MATERIALS = List.copyOf(materials);
    }

    private static final List<Enchantment> SWORD_ENCHANTMENTS = List.of(
            Enchantment.SHARPNESS,
            Enchantment.SMITE,
            Enchantment.BANE_OF_ARTHROPODS,
            Enchantment.KNOCKBACK,
            Enchantment.FIRE_ASPECT,
            Enchantment.LOOTING,
            Enchantment.UNBREAKING,
            Enchantment.MENDING,
            Enchantment.VANISHING_CURSE
    );

    /**
     * player UUID → slot index (0/1/2) → list of offers to apply on click.
     * Populated in step 1, consumed + removed in step 2.
     */
    private final Map<UUID, Map<Integer, List<EnchantmentOffer>>> pendingOffers = new HashMap<>();

    // Store prepared anvil results
    private final Map<UUID, ItemStack> anvilResults = new HashMap<>();

    // Store anvil costs for each player
    private final Map<UUID, Integer> anvilCosts = new HashMap<>();

    /**
     * Check if an item is a custom scythe weapon
     */
    private boolean isScythe(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        if (!SCYTHE_MATERIALS.contains(item.getType())) {
            return false;
        }

        return ItemManager.getInstance().isCorovaItem(item);
    }

    /**
     * Handle smithing table preparation - maintain mutations when upgrading
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ItemStack base = event.getInventory().getInputEquipment();
        ItemStack result = event.getResult();

        if (base != null && result != null && isScythe(base) && isScythe(result)) {
            MutationManager.getInstance().transferMutations(base, result);
            event.setResult(result);
        }
    }

    /**
     * Handle anvil preparation - calculate and display the result.
     *
     * Reads current enchantment levels via {@link VanillaEnchantDisplay#getTrueLevel}
     * so that over-cap levels already on the item are correctly compared and upgraded.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        Player player = (Player) event.getView().getPlayer();
        UUID playerId = player.getUniqueId();
        anvilResults.remove(playerId);
        anvilCosts.remove(playerId);

        ItemStack first = event.getInventory().getFirstItem();
        ItemStack second = event.getInventory().getSecondItem();

        if (first == null || second == null) {
            return;
        }

        if (!isScythe(first)) {
            return;
        }

        // Get enchantments from both items.
        Map<Enchantment, Integer> firstEnchants = VanillaEnchantDisplay.getAllTrueLevels(first);
        Map<Enchantment, Integer> secondEnchants;

        if (second.getType() == Material.ENCHANTED_BOOK) {
            secondEnchants = VanillaEnchantDisplay.getAllTrueLevels(second);
        } else if (isScythe(second)) {
            secondEnchants = VanillaEnchantDisplay.getAllTrueLevels(second);
        } else {
            return;
        }

        if (secondEnchants.isEmpty()) {
            return;
        }

        // Clone the scythe
        ItemStack result = first.clone();
        ItemMeta resultMeta = result.getItemMeta();
        ItemMeta firstMeta = first.getItemMeta();

        boolean hasValidEnchant = false;
        int totalCost = 0;
        Map<Enchantment, Integer> finalEnchants = new HashMap<>(firstEnchants);

        // Get prior work penalty (stored in repair cost)
        int priorWorkPenalty = 0;
        if (firstMeta instanceof Repairable) {
            Repairable repairable = (Repairable) firstMeta;
            if (repairable.hasRepairCost()) {
                priorWorkPenalty = repairable.getRepairCost();
            }
        }

        // Calculate cost for each enchantment.
        for (Map.Entry<Enchantment, Integer> entry : secondEnchants.entrySet()) {
            Enchantment ench = entry.getKey();
            int secondLevel = entry.getValue();

            if (!SWORD_ENCHANTMENTS.contains(ench)) {
                continue;
            }

            int enchantCost = 0;
            int maxAllowed = getMaxAllowedLevel(ench, player);

            // Read the true current level from PDC (handles over-cap items correctly).
            int currentLevel = finalEnchants.getOrDefault(ench, 0);
            int finalLevel = 0;
            boolean shouldApply = false;

            if (currentLevel > 0 && currentLevel == secondLevel) {
                // Same level - combine to next level
                finalLevel = currentLevel + 1;
                if (finalLevel <= maxAllowed) {
                    shouldApply = true;
                } else {
                    finalLevel = maxAllowed;
                    if (finalLevel > currentLevel) {
                        shouldApply = true;
                    }
                }
            } else if (secondLevel > currentLevel) {
                // Higher level - apply it (cap at maxAllowed)
                finalLevel = Math.min(secondLevel, maxAllowed);
                if (finalLevel > currentLevel) {
                    shouldApply = true;
                }
            } else if (currentLevel > 0) {
                // Same or lower level - still costs something
                enchantCost = getEnchantmentCost(ench, currentLevel);
                hasValidEnchant = true;
            }

            if (shouldApply) {
                finalEnchants.put(ench, finalLevel);
                enchantCost = getEnchantmentCost(ench, finalLevel);
                hasValidEnchant = true;
            }

            totalCost += enchantCost;
        }

        if (hasValidEnchant) {
            // Add prior work penalty (Linear instead of exponential for lower prices)
            int workPenaltyCost = priorWorkPenalty * 2;
            totalCost += workPenaltyCost;

            // Increment repair cost for next time
            if (resultMeta instanceof Repairable) {
                Repairable repairable = (Repairable) resultMeta;
                repairable.setRepairCost(priorWorkPenalty + 1);
            }

            // Cap at 39 levels (vanilla anvil limit)
            totalCost = Math.min(totalCost, 39);

            result.setItemMeta(resultMeta);

            // Apply VanillaEnchantDisplay for all enchants on the result
            VanillaEnchantDisplay.applyBatch(result, finalEnchants);

            // CRITICAL: applyBatch only manages vanilla enchant lore lines — it has no
            // knowledge of CorovaEnchantments custom enchant lines or mutation lines.
            // `result` is a clone of the player's first item (the scythe), which may
            // already carry a custom Corova enchant + its lore line. Without rebuilding
            // here, that custom enchant line is left wherever it happened to land relative
            // to the freshly-injected vanilla lines, and `storedResult.clone()` delivered
            // in onAnvilClick would hand the player a scythe with scrambled/missing lore.
            // Using updateLore with player context ensures trim bonuses/amplifications are correct.
            MutationManager.getInstance().updateLore(result, resultMeta, player);

            event.setResult(result);

            // Set the repair cost and handle renaming in the anvil GUI using AnvilView (Paper 1.21+)
            if (event.getView() instanceof org.bukkit.inventory.view.AnvilView) {
                org.bukkit.inventory.view.AnvilView anvilView = (org.bukkit.inventory.view.AnvilView) event.getView();
                if (totalCost > 0) anvilView.setRepairCost(totalCost);

                // Handle renaming if present (using non-deprecated Paper API)
                String renameText = anvilView.getRenameText();
                if (renameText != null && !renameText.trim().isEmpty()) {
                    ItemMeta meta = result.getItemMeta();
                    if (meta != null) {
                        meta.setDisplayName(renameText);
                        result.setItemMeta(meta);
                        event.setResult(result);
                    }
                }
            }

            // Store this result and cost for when player clicks
            anvilResults.put(playerId, result.clone());
            anvilCosts.put(playerId, totalCost);
        }
    }

    /**
     * Handle anvil click - bypass Minecraft's validation and give the item directly
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAnvilClick(InventoryClickEvent event) {
        if (!(event.getInventory() instanceof AnvilInventory)) {
            return;
        }

        AnvilInventory anvil = (AnvilInventory) event.getInventory();

        // Check if clicking the result slot
        if (event.getRawSlot() != 2) {
            return;
        }

        ItemStack first = anvil.getFirstItem();
        ItemStack second = anvil.getSecondItem();
        ItemStack result = anvil.getResult();

        if (first == null || second == null || result == null) {
            return;
        }

        if (!isScythe(first)) {
            return;
        }

        // Only handle enchanted book or scythe combinations
        if (second.getType() != Material.ENCHANTED_BOOK && !isScythe(second)) {
            return;
        }

        UUID playerId = event.getWhoClicked().getUniqueId();
        ItemStack storedResult = anvilResults.get(playerId);
        Integer cost = anvilCosts.get(playerId);

        if (storedResult == null || cost == null) {
            return;
        }

        // Get the player
        org.bukkit.entity.Player player = (org.bukkit.entity.Player) event.getWhoClicked();

        // Check if player has enough XP (in survival/adventure mode)
        if (player.getGameMode() == org.bukkit.GameMode.SURVIVAL ||
                player.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
            if (player.getLevel() < cost) {
                // Not enough levels - don't allow the transaction
                event.setCancelled(true);
                player.sendMessage("§cYou need " + cost + " levels to use this anvil combination!");
                return;
            }
        }

        // Cancel the default behavior
        event.setCancelled(true);

        // Deduct XP cost
        if (player.getGameMode() == org.bukkit.GameMode.SURVIVAL ||
                player.getGameMode() == org.bukkit.GameMode.ADVENTURE) {
            player.setLevel(player.getLevel() - cost);
        }

        // Check if player is shift-clicking or regular clicking
        boolean isShiftClick = event.isShiftClick();

        // Give the item to the player
        if (isShiftClick) {
            // Shift click - add to inventory
            Map<Integer, ItemStack> remaining = player.getInventory().addItem(storedResult.clone());
            for (ItemStack left : remaining.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        } else {
            // Regular click - put on cursor
            player.setItemOnCursor(storedResult.clone());
        }

        // Clear the anvil inputs
        anvil.setFirstItem(null);
        anvil.setSecondItem(null);
        anvil.setResult(null);

        // Update the inventory
        player.updateInventory();

        // Clean up
        anvilResults.remove(playerId);
        anvilCosts.remove(playerId);

        // Play anvil use sound
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
    }

    /**
     * Cleanup when inventory closes
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID uid = event.getPlayer().getUniqueId();
        anvilResults.remove(uid);
        anvilCosts.remove(uid);
        pendingOffers.remove(uid);

        if (event.getInventory().getType() == org.bukkit.event.inventory.InventoryType.ENCHANTING) {
            ItemStack item = event.getInventory().getItem(0);
            if (item != null) {
                VanillaEnchantDisplay.removePreviewLore(item);
            }
        }
    }

    /**
     * Handle enchantment table preparation - show sword enchantments
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        ItemStack item = event.getItem();

        if (!isScythe(item)) {
            return;
        }

        Player player = event.getEnchanter();
        UUID playerId = player.getUniqueId();
        Random random = new Random((long) player.getEnchantmentSeed() ^ 0x5DEECE66DL);
        EnchantmentOffer[] offers = event.getOffers();

        Map<Integer, List<EnchantmentOffer>> sessionOffers = new HashMap<>();

        int tierMax = EnchantmentTierManager.getMaxTableLevel(player);
        int bookshelves = Math.min(15, event.getEnchantmentBonus());
        int maxTableLevel = (int) (tierMax * (bookshelves / 15.0));

        double[] fractions = { 0.25, 0.55, 0.90 };

        for (int i = 0; i < offers.length; i++) {
            int scaledCost;
            if (maxTableLevel <= 0) {
                scaledCost = 1;
            } else {
                int base = (int)(maxTableLevel * fractions[i]);
                int jitter = random.nextInt(7) - 3; // -3 to +3
                scaledCost = Math.max(1, Math.min(maxTableLevel, base + jitter));
            }

            List<Enchantment> pool = getAvailableEnchantsForLevel(scaledCost, item.getType(), random);

            if (!pool.isEmpty()) {
                Collections.shuffle(pool, random);
                Enchantment primary = pool.get(0);
                int primaryLevel = EnchantmentTierManager.getEnchantLevelForCost(scaledCost, primary, player);

                // Note: Standard Minecraft client displays levels 11+ as digits (11, 12, 13)
                // in the table GUI. Roman numeral display is handled via lore on the item itself.
                offers[i] = new EnchantmentOffer(primary, primaryLevel, scaledCost);
                event.getExpLevelCostsOffered()[i] = scaledCost;

                List<EnchantmentOffer> slotOffers = new ArrayList<>();
                slotOffers.add(offers[i]);

                // Massive variety logic
                if (maxTableLevel > 0) {
                    int extraCount = EnchantmentTierManager.getBonusEnchantCount(i, scaledCost);

                    if (extraCount > 0) {
                        Set<Enchantment> used = new HashSet<>();
                        used.add(primary);

                        for (int j = 1; j < pool.size() && extraCount > 0; j++) {
                            Enchantment candidate = pool.get(j);
                            if (used.contains(candidate)) continue;

                            int extraLevel = EnchantmentTierManager.getEnchantLevelForCost(scaledCost, candidate, player);
                            slotOffers.add(new EnchantmentOffer(candidate, extraLevel, scaledCost));
                            used.add(candidate);
                            extraCount--;
                        }
                    }
                }
                sessionOffers.put(i, slotOffers);
            }
        }

        pendingOffers.put(playerId, sessionOffers);

        // Preview high-level enchants (levels > 10) in the table tooltip.
        VanillaEnchantDisplay.addPreviewLore(item, sessionOffers);
    }

    /**
     * Handle the actual enchantment application - apply ONLY what was shown.
     *
     * The deferred task calls {@link VanillaEnchantDisplay#applyWithDisplay} for every
     * enchantment, which clamps the live value to 10 and stores the true level in PDC
     * for any level > 10, then injects the readable lore line.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEnchantItem(EnchantItemEvent event) {
        ItemStack item = event.getItem();

        if (!isScythe(item)) {
            return;
        }

        UUID playerId = event.getEnchanter().getUniqueId();

        Map<Integer, List<EnchantmentOffer>> sessionOffers = pendingOffers.remove(playerId);

        if (sessionOffers == null) {
            event.setCancelled(true);
            return;
        }

        List<EnchantmentOffer> offers = sessionOffers.get(event.whichButton());
        if (offers == null || offers.isEmpty()) {
            event.setCancelled(true);
            return;
        }

        // CRITICAL: Clear ALL enchantments that Minecraft is trying to add
        Map<Enchantment, Integer> enchantsToAdd = event.getEnchantsToAdd();
        enchantsToAdd.clear();

        for (EnchantmentOffer offer : offers) {
            enchantsToAdd.put(offer.getEnchantment(), offer.getEnchantmentLevel());
        }

        // Ensure the enchantments actually get applied to the item.
        // VanillaEnchantDisplay.applyBatch handles addEnchant, PDC storage
        // and injects the lore lines correctly for any level.
        final Map<Enchantment, Integer> finalToAdd = new HashMap<>(enchantsToAdd);
        org.bukkit.Bukkit.getScheduler().runTask(
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                () -> {
                    ItemMeta meta = item.getItemMeta();
                    if (meta == null) return;
                    for (Map.Entry<Enchantment, Integer> e : finalToAdd.entrySet()) {
                        VanillaEnchantDisplay.storeTrueLevel(meta, e.getKey(), e.getValue());
                        int clamped = Math.min(e.getValue(), VanillaEnchantDisplay.DISPLAY_CAP);
                        meta.addEnchant(e.getKey(), clamped, true);
                    }
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                    item.setItemMeta(meta);

                    // CRITICAL: see GeneralEnchantmentListener / TieredEnchantTables —
                    // applyBatch only manages vanilla enchant lore lines and has no
                    // knowledge of CorovaEnchantments custom enchant lines or mutation
                    // lines. Scythes can carry custom Corova enchants, so we must rebuild
                    // canonical lore order after applying vanilla enchants here too.
                    // Re-assert canonical lore order and refresh viewer-dependent trims.
                    MutationManager.getInstance().updateLore(item, meta, event.getEnchanter());
                    item.setItemMeta(meta);
                }
        );
    }

    /**
     * Get enchantments available at a specific cost level
     */
    private List<Enchantment> getAvailableEnchantsForLevel(int cost, Material material, Random random) {
        List<Enchantment> available = new ArrayList<>();

        // All sword enchants available even at lowest costs for better variety
        available.add(Enchantment.SHARPNESS);
        if (random.nextDouble() < 0.20) available.add(Enchantment.KNOCKBACK);
        available.add(Enchantment.UNBREAKING);
        available.add(Enchantment.SMITE);
        available.add(Enchantment.BANE_OF_ARTHROPODS);
        available.add(Enchantment.FIRE_ASPECT);
        available.add(Enchantment.LOOTING);

        return available;
    }

    /**
     * Get the XP cost for an enchantment based on Minecraft's anvil mechanics
     */
    private int getEnchantmentCost(Enchantment ench, int level) {
        int multiplier = getEnchantmentMultiplier(ench);
        // Direct price calculation for better balance at high levels
        return Math.max(1, multiplier * level);
    }

    /**
     * Get the maximum allowed level for an enchantment
     */
    private int getMaxAllowedLevel(Enchantment ench, Player player) {
        return EnchantmentTierManager.getMaxAllowedLevel(ench, player);
    }

    /**
     * Get the rarity multiplier for each enchantment type
     * Based on vanilla Minecraft's enchantment rarity system
     */
    private int getEnchantmentMultiplier(Enchantment enchantment) {
        // Very rare enchantments (8x multiplier)
        if (enchantment == Enchantment.MENDING ||
                enchantment == Enchantment.VANISHING_CURSE) {
            return 8;
        }

        // Rare enchantments (4x multiplier)
        if (enchantment == Enchantment.LOOTING ||
                enchantment == Enchantment.FORTUNE ||
                enchantment == Enchantment.SILK_TOUCH ||
                enchantment == Enchantment.SWEEPING_EDGE ||
                enchantment == Enchantment.FIRE_ASPECT ||
                enchantment == Enchantment.INFINITY) {
            return 4;
        }

        // Uncommon enchantments (2x multiplier)
        if (enchantment == Enchantment.SHARPNESS ||
                enchantment == Enchantment.SMITE ||
                enchantment == Enchantment.KNOCKBACK ||
                enchantment == Enchantment.BANE_OF_ARTHROPODS ||
                enchantment == Enchantment.PROTECTION ||
                enchantment == Enchantment.FIRE_PROTECTION ||
                enchantment == Enchantment.BLAST_PROTECTION ||
                enchantment == Enchantment.PROJECTILE_PROTECTION ||
                enchantment == Enchantment.FEATHER_FALLING ||
                enchantment == Enchantment.POWER ||
                enchantment == Enchantment.PUNCH ||
                enchantment == Enchantment.EFFICIENCY) {
            return 2;
        }

        // Common enchantments (1x multiplier)
        if (enchantment == Enchantment.UNBREAKING) {
            return 1;
        }

        // Default to 1
        return 1;
    }
}