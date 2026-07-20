package com.example.corovaItems.Enchantments.GreaterEnchantmentSystem;

import com.example.corovaItems.ItemMutations.MutationManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Repairable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;

/**
 * Anvil handler for all non-scythe items.  Extends vanilla enchantment level caps
 * according to the player's tier and routes over-cap enchantments through
 * {@link VanillaEnchantDisplay} so they display correctly in lore.
 *
 * <p>All enchantment level reads use {@link VanillaEnchantDisplay#getTrueLevel}
 * rather than {@code ItemMeta#getEnchantLevel} so that PDC-stored over-cap
 * levels on the <em>input</em> items are correctly compared and combined.
 * When the result is handed to the player, {@link VanillaEnchantDisplay#applyWithDisplay}
 * is called for every enchantment so that any level > 10 is clamped for display
 * purposes, stored in PDC, and shown as a lore line.
 */
public class GeneralEnchantmentListener implements Listener {

    public static void setTierChecker(BiPredicate<Player, Integer> checker) {
        EnchantmentTierManager.setTierChecker(checker);
    }

    // Store prepared anvil results and costs
    private final Map<UUID, ItemStack> anvilResults = new HashMap<>();
    private final Map<UUID, Integer> anvilCosts = new HashMap<>();

    /**
     * Handle anvil preparation - calculate extended enchantment levels.
     *
     * Current levels on the first item are read with
     * {@link VanillaEnchantDisplay#getTrueLevel} so over-cap PDC values are used
     * when deciding whether combining two same-level enchants creates a higher one.
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

        // Skip scythes to avoid conflict with ScytheAnvilEnchantments
        if (isScythe(first)) {
            return;
        }

        // Get enchantments from both items.
        // Use getTrueEnchantments so over-cap PDC values are reflected from BOTH items.
        Map<Enchantment, Integer> firstEnchants = getTrueEnchantments(first);
        Map<Enchantment, Integer> secondEnchants = getTrueEnchantments(second);

        if (secondEnchants.isEmpty()) {
            return;
        }

        // Determine result item type
        // Always clone the first item to preserve all PDC, custom enchants, and metadata.
        // If both are books, cloning the first book is safer than creating a new one.
        ItemStack result = first.clone();

        ItemMeta resultMeta = result.getItemMeta();
        if (resultMeta == null) {
            return;
        }

        int totalCost = 0;
        boolean hasValidEnchant = false;
        Map<Enchantment, Integer> finalEnchants = new HashMap<>(firstEnchants);

        // Get prior work penalty from first item
        int priorWorkPenalty = 0;
        ItemMeta firstMeta = first.getItemMeta();
        if (firstMeta instanceof Repairable) {
            Repairable repairable = (Repairable) firstMeta;
            if (repairable.hasRepairCost()) {
                priorWorkPenalty = repairable.getRepairCost();
            }
        }

        // Process each enchantment from the second item
        for (Map.Entry<Enchantment, Integer> entry : secondEnchants.entrySet()) {
            Enchantment ench = entry.getKey();
            int secondLevel = entry.getValue();
            int maxAllowed = getMaxAllowedLevel(ench, player);

            // Skip if not compatible with the item type (unless it's an exception)
            if (!isCompatible(ench, result.getType())) {
                continue;
            }

            // Protection limit check: increased to 3 protection types to match TieredEnchantTables
            if (EnchantmentTierManager.isProtectionEnchant(ench)) {
                int protectionCount = 0;
                for (Enchantment e : finalEnchants.keySet()) {
                    if (EnchantmentTierManager.isProtectionEnchant(e)) {
                        protectionCount++;
                    }
                }

                // If we're adding a NEW protection enchant and already have 3, skip it
                if (!finalEnchants.containsKey(ench) && protectionCount >= 3) {
                    continue;
                }
            }

            int currentLevel = finalEnchants.getOrDefault(ench, 0);
            int finalLevel = 0;
            boolean shouldApply = false;

            if (currentLevel > 0 && currentLevel == secondLevel) {
                // Same level - combine to next level
                finalLevel = currentLevel + 1;
                if (finalLevel <= maxAllowed) {
                    shouldApply = true;
                } else {
                    // Capped at maxAllowed
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
            }

            if (shouldApply) {
                finalEnchants.put(ench, finalLevel);
                int enchantCost = getEnchantmentCost(ench, finalLevel);
                totalCost += enchantCost;
                hasValidEnchant = true;
            }
        }

        if (hasValidEnchant) {
            // Add prior work penalty (Linear instead of exponential for lower prices)
            int workPenaltyCost = priorWorkPenalty * 2;
            totalCost += workPenaltyCost;

            // Update repair cost
            if (resultMeta instanceof Repairable) {
                Repairable repairable = (Repairable) resultMeta;
                repairable.setRepairCost(priorWorkPenalty + 1);
            }

            // Cap total cost at 39 to avoid "Too Expensive" and keep prices low
            totalCost = Math.min(totalCost, 39);

            result.setItemMeta(resultMeta);

            // Apply VanillaEnchantDisplay overrides on the preview item so lore is
            // visible before the player clicks to confirm.
            // Using applyBatch with our calculated finalEnchants ensures PDC and lore are in sync.
            VanillaEnchantDisplay.applyBatch(result, finalEnchants);

            // CRITICAL: applyBatch only manages vanilla enchant lore lines — it has no
            // knowledge of CorovaEnchantments custom enchant lines or mutation lines.
            // `result` is a clone of the player's first item, which may already carry a
            // custom enchant + its lore line. Without this call, the custom enchant line
            // is left wherever it happened to be relative to the freshly-injected vanilla
            // lines, and gets lost/scrambled on subsequent lore rebuilds elsewhere.
            // refreshLore re-asserts the canonical order: base -> vanilla -> custom -> mutation.
            // Using updateLore with player context ensures trim bonuses/amplifications are correct.
            MutationManager.getInstance().updateLore(result, player);

            event.setResult(result);

            // Set the repair cost and handle renaming in anvil GUI
            if (event.getView() instanceof org.bukkit.inventory.view.AnvilView) {
                org.bukkit.inventory.view.AnvilView anvilView = (org.bukkit.inventory.view.AnvilView) event.getView();
                anvilView.setRepairCost(totalCost);

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

            // Store result and cost
            anvilResults.put(playerId, result.clone());
            anvilCosts.put(playerId, totalCost);
        }
    }

    /**
     * Handle anvil click - apply the extended enchantments.
     *
     * Calls {@link VanillaEnchantDisplay#applyWithDisplay} on the delivered item so
     * all over-cap enchantments are correctly stored in PDC and shown in lore.
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

        UUID playerId = event.getWhoClicked().getUniqueId();
        ItemStack storedResult = anvilResults.get(playerId);
        Integer cost = anvilCosts.get(playerId);

        if (storedResult == null || cost == null) {
            return; // Not our custom enchantment
        }

        Player player = (Player) event.getWhoClicked();

        // Check XP requirements
        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
            if (player.getLevel() < cost) {
                event.setCancelled(true);
                player.sendMessage("§cYou need " + cost + " levels to use this anvil combination!");
                return;
            }
        }

        // Cancel default behavior
        event.setCancelled(true);

        // Deduct XP
        if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
            player.setLevel(player.getLevel() - cost);
        }

        // Apply VanillaEnchantDisplay overrides on the final item before handing to player.
        // This ensures PDC levels and lore lines are correct on the item the player receives.
        ItemStack deliveredItem = storedResult.clone();
        VanillaEnchantDisplay.applyBatch(deliveredItem, VanillaEnchantDisplay.getAllTrueLevels(deliveredItem));

        // CRITICAL: same reasoning as the preview path above — applyBatch does not
        // know about CorovaEnchantments custom enchant lines or mutation lines, so the
        // canonical lore order (base -> vanilla -> custom -> mutation) must be re-asserted
        // here. Without this, custom enchants applied to this item earlier (or carried
        // over from `first`) would have their lore line dropped/displaced the moment it
        // touches the anvil, even though the PDC entries (KEY_ENCHANT_ID / _LVL) are untouched.
        // Using updateLore with player context ensures trim bonuses/amplifications are correct.
        MutationManager.getInstance().updateLore(deliveredItem, player);

        // Give item to player
        if (event.isShiftClick()) {
            Map<Integer, ItemStack> remaining = player.getInventory().addItem(deliveredItem);
            for (ItemStack left : remaining.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), left);
            }
        } else {
            player.setItemOnCursor(deliveredItem);
        }

        // Clear anvil
        anvil.setFirstItem(null);
        anvil.setSecondItem(null);
        anvil.setResult(null);

        // Update inventory
        player.updateInventory();

        // Cleanup
        anvilResults.remove(playerId);
        anvilCosts.remove(playerId);

        // Play sound
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
    }

    /**
     * Cleanup when inventory closes
     */
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        anvilResults.remove(event.getPlayer().getUniqueId());
        anvilCosts.remove(event.getPlayer().getUniqueId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns the true enchantment levels for {@code item} using
     * {@link VanillaEnchantDisplay#getTrueLevel} for each enchant so PDC-stored
     * over-cap values are included.
     */
    private Map<Enchantment, Integer> getTrueEnchantments(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return new HashMap<>();
        Map<Enchantment, Integer> raw = getEnchantments(item);
        Map<Enchantment, Integer> result = new HashMap<>();
        for (Enchantment ench : raw.keySet()) {
            result.put(ench, VanillaEnchantDisplay.getTrueLevel(item, ench));
        }
        return result;
    }

    /**
     * Get enchantments from an item or book (raw meta levels, not PDC-corrected).
     * Use {@link #getTrueEnchantments} when you need the correct over-cap values.
     */
    private Map<Enchantment, Integer> getEnchantments(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return new HashMap<>();
        }
        return getEnchantments(item, item.getItemMeta());
    }

    private Map<Enchantment, Integer> getEnchantments(ItemStack item, ItemMeta meta) {
        Map<Enchantment, Integer> enchants = new HashMap<>();
        if (meta instanceof EnchantmentStorageMeta) {
            enchants.putAll(((EnchantmentStorageMeta) meta).getStoredEnchants());
        } else if (meta.hasEnchants()) {
            enchants.putAll(meta.getEnchants());
        }
        return enchants;
    }


    /**
     * Get the maximum allowed level for an enchantment
     */
    private int getMaxAllowedLevel(Enchantment ench, Player player) {
        return EnchantmentTierManager.getMaxAllowedLevel(ench, player);
    }

    /**
     * Calculate the XP cost for an enchantment level
     */
    private int getEnchantmentCost(Enchantment ench, int level) {
        int multiplier = getEnchantmentMultiplier(ench);
        // Direct price calculation for better balance at high levels
        return Math.max(1, multiplier * level);
    }

    /**
     * Check if an enchantment is compatible with an item material.
     * Extends vanilla compatibility to allow SMITE and BANE_OF_ARTHROPODS on bows.
     */
    private boolean isCompatible(Enchantment ench, Material material) {
        if (material == Material.ENCHANTED_BOOK) {
            return true;
        }

        // Allow damage and looting enchants on bows
        if (material == Material.BOW || material == Material.CROSSBOW) {
            if (ench == Enchantment.POWER || ench == Enchantment.SMITE || ench == Enchantment.BANE_OF_ARTHROPODS || ench == Enchantment.LOOTING) {
                return true;
            }
        }

        // Allow wand enchantments on spear materials
        if (WandEnchantListener.isWand(material)) {
            if (ench == Enchantment.POWER || ench == Enchantment.SMITE || ench == Enchantment.BANE_OF_ARTHROPODS || ench == Enchantment.UNBREAKING) {
                return true;
            }
        }

        ItemStack item = new ItemStack(material);
        if (ench.canEnchantItem(item)) {
            return true;
        }

        // Allow damage enchants on swords and axes and hoes (scythes)
        String name = material.name();
        boolean isWeapon = name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_HOE");
        if (isWeapon && (ench == Enchantment.SHARPNESS || ench == Enchantment.SMITE || ench == Enchantment.BANE_OF_ARTHROPODS)) {
            return true;
        }

        return false;
    }

    /**
     * Check if an item is a custom scythe weapon
     */
    private boolean isScythe(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }

        java.util.List<Material> scytheMaterials = new java.util.ArrayList<>();
        scytheMaterials.add(Material.WOODEN_HOE);
        scytheMaterials.add(Material.STONE_HOE);
        scytheMaterials.add(Material.IRON_HOE);
        scytheMaterials.add(Material.DIAMOND_HOE);
        scytheMaterials.add(Material.NETHERITE_HOE);
        Material copperHoe = Material.matchMaterial("COPPER_HOE");
        if (copperHoe != null) scytheMaterials.add(copperHoe);

        if (!scytheMaterials.contains(item.getType())) {
            return false;
        }

        return com.example.corovaItems.ItemManager.getInstance().isCorovaItem(item);
    }

    /**
     * Get the rarity multiplier for each enchantment type
     */
    private int getEnchantmentMultiplier(Enchantment enchantment) {
        // Very rare enchantments (8x multiplier)
        if (enchantment == Enchantment.MENDING ||
                enchantment == Enchantment.VANISHING_CURSE ||
                enchantment == Enchantment.BINDING_CURSE) {
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
        return 1;
    }
}