package com.example.corovaItems.Enchantments.GreaterEnchantmentSystem;

import com.example.corovaItems.ItemMutations.MutationManager;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.function.Predicate;

/**
 * Handles enchant-table offers and application for ALL non-scythe items.
 *
 * Two-step approach:
 *
 *   Step 1 — PrepareItemEnchantEvent (HIGHEST):
 *     Compute tier-scaled costs, pick enchantments for each slot, write
 *     EnchantmentOffer objects for the tooltip, and store the full
 *     slot → offer list in pendingOffers keyed by player UUID.
 *
 *   Step 2 — EnchantItemEvent (HIGHEST):
 *     Discard vanilla's result entirely, substitute our pre-computed offers,
 *     enforce the shown cost, then apply via deferred task so that multiple
 *     damage enchants bypass vanilla's conflict checks.
 *
 * ── Bookshelf scaling ─────────────────────────────────────────────────────
 *   Vanilla grants up to 15 bookshelves, each adding +2 to the effective
 *   table power (max vanilla power = 30).  We honour this: the effective
 *   ceiling for a normal table is:
 *
 *     effectiveCeiling = min(tierMaxLevel, bookshelves * 2)
 *
 *   With 0 bookshelves the ceiling is 0 — all offers become level-1 and
 *   cost 1 XP, exactly like vanilla.  A full 15-shelf setup gives ceiling 30,
 *   and tier upgrades can push that up to 55 for tier-5 players.
 *
 *   Adaptive enchanting tables (registered in MaxTableWithoutBookShelves)
 *   are exempt from this cap — they already handle their own logic and
 *   are designed to operate at full power without bookshelves.
 *
 * ── Tier cost table ───────────────────────────────────────────────────────
 *   Tier 0 → max cost 30   Tier 1 → 35   Tier 2 → 40
 *   Tier 3 → max cost 45   Tier 4 → 50   Tier 5 → 55
 *
 * ── Slot cost fractions (deterministic, no overflow) ─────────────────────
 *   Slot 0 (cheapest) : 25 % of effective ceiling   (e.g. tier 2 full shelves = 10)
 *   Slot 1 (middle)   : 55 % of effective ceiling
 *   Slot 2 (best)     : 90 % of effective ceiling
 *   ±3 random jitter applied after, then hard-clamped to [slotMin, effectiveCeiling].
 *   EXCEPTION: at a full 15 bookshelves, slot 2 skips the 90%/jitter math
 *   entirely and is always exactly the ceiling — this matches vanilla, where
 *   the top slot's cost is max(randomBase, bookshelves*2), and bookshelves*2
 *   always wins once shelves are maxed.
 *
 * ── Enchant level table ───────────────────────────────────────────────────
 *   cost  1–9  → level 1   cost 10–19 → level 2   cost 20–29 → level 3
 *   cost 30–34 → level 4   cost 35–39 → level 5   cost 40–44 → level 6
 *   cost 45–49 → level 7   cost 50–54 → level 8   cost 55–59 → level 9
 *   cost 60–64 → level 10  cost 65–69 → level 11  cost 70+   → level 12
 *   Each result is then clamped to getMaxAllowedLevel(ench, player).
 *
 * ── Multi-enchant ─────────────────────────────────────────────────────────
 *   Slot 2 at cost ≥ 35 gains 1 bonus enchant.
 *   Slot 2 at cost ≥ 45 gains 2 bonus enchants.
 *   Swords carry Sharpness + Smite + Bane in the same pool so all three can
 *   appear together.  Bows carry the same damage enchants so they stay
 *   competitive with swords.
 */
public class TieredEnchantTables implements Listener {

    /**
     * Optional predicate supplied at construction time.
     * Returns true if the given enchanting-table block is an adaptive table
     * (i.e. it should be exempt from the bookshelf cap).
     *
     * NOTE: We deliberately use a Predicate<org.bukkit.block.Block> rather than
     * importing MaxTableWithoutBookShelves directly, because that class lives in
     * corovaFX and this class lives in corovaItems — importing it would create a
     * cross-module dependency in the wrong direction.
     */
    private final Predicate<org.bukkit.block.Block> isAdaptiveTable;

    /**
     * player UUID → slot index (0/1/2) → list of offers to apply on click.
     * Populated in step 1, consumed + removed in step 2.
     * Also cleared on InventoryCloseEvent to prevent stale-state leaks.
     */
    private final Map<UUID, Map<Integer, List<EnchantmentOffer>>> pendingOffers = new HashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    // Constructors
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Constructor that accepts an adaptive-table predicate.
     * Pass {@code MaxTableWithoutBookShelves::isAdaptiveTable} from the plugin
     * that wires everything together (e.g. CorovaCore) so corovaItems never
     * imports the corovaFX class directly.
     *
     * @param isAdaptiveTable predicate returning true for blocks that are
     *                        registered adaptive enchanting tables
     */
    public TieredEnchantTables(Predicate<org.bukkit.block.Block> isAdaptiveTable) {
        this.isAdaptiveTable = isAdaptiveTable != null ? isAdaptiveTable : block -> false;
    }

    /**
     * No-arg constructor for callers that do not use adaptive tables.
     * All tables will be subject to the bookshelf cap.
     */
    public TieredEnchantTables() {
        this(null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 1 — Build and display offers
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        Player player = event.getEnchanter();
        ItemStack item = event.getItem();
        if (isScythe(item)) return;

        // Adaptive tables handle their own enchant logic — skip them here.
        if (isAdaptiveTable.test(event.getEnchantBlock())) return;

        UUID uid      = player.getUniqueId();
        int tierMax   = EnchantmentTierManager.getMaxTableLevel(player); // e.g. 40 for tier 2

        // ── Bookshelf scaling ────────────────────────────────────────────────
        // bookshelves scale as a fraction of tierMax: Full 15 shelves = full tier ceiling
        int bookshelves     = Math.min(15, event.getEnchantmentBonus()); // 0 – 15
        int maxLevel        = (int) (tierMax * (bookshelves / 15.0));
        // If there are zero bookshelves the effective ceiling is 0 —
        // costs will clamp to 1 and all enchants will be level 1.

        EnchantmentOffer[] offers = event.getOffers();

        // Each slot gets its own Random seeded from a unique mix of:
        //   - player enchant seed (stable per-player)
        //   - current time window (changes on re-open, giving variety)
        //   - slot index (makes each slot independent even within one open)
        // This ensures all 3 slots differ from each other AND differ every time
        // the player re-opens the table.
        Random[] slotRandom = new Random[3];
        for (int s = 0; s < 3; s++) {
            long slotSeed = (long) player.getEnchantmentSeed() ^ ((long) s * 0x9E3779B97F4A7C15L);
            slotRandom[s] = new Random(slotSeed);
        }

        // Slot cost fractions
        double[] fractions = { 0.25, 0.55, 0.90 };

        // Minimums only apply when there are actually bookshelves present.
        // With 0 shelves every slot gets cost 1 (the Math.max below handles this).
        int[] minCosts;
        if (maxLevel > 0) {
            minCosts = new int[]{
                    Math.max(5, maxLevel / 3),
                    Math.max(15, maxLevel / 2),
                    Math.max(25, maxLevel * 3 / 4)
            };
        } else {
            minCosts = new int[]{ 1, 1, 1 };
        }

        Map<Integer, List<EnchantmentOffer>> sessionOffers = new HashMap<>();

        for (int slot = 0; slot < 3; slot++) {
            Random random = slotRandom[slot]; // each slot uses its own independent RNG
            int base;
            int scaledCost;

            if (maxLevel <= 0) {
                // No bookshelves — flat cost of 1 for all slots (still lets the
                // player enchant; they just get the weakest possible result).
                scaledCost = 1;
            } else if (slot == 2 && bookshelves >= 15) {
                // Vanilla's top slot at a full 15 bookshelves is deterministic:
                // it computes max(randomBase, bookshelves*2), and bookshelves*2
                // (=maxLevel here) always wins at max shelves. No jitter, no
                // 90% shave — just the cap, every time.
                scaledCost = maxLevel;
            } else {
                base       = (int)(maxLevel * fractions[slot]);
                int jitter = random.nextInt(7) - 3;           // -3 to +3
                scaledCost = Math.max(minCosts[slot], Math.min(maxLevel, base + jitter));
            }

            // Pick primary enchant — for books, each slot gets its own independent theme
            // so slot 0 might show sword enchants while slot 2 shows armor enchants.
            List<Enchantment> pool = TieredEnchantTables.getAvailableEnchants(item, player, slot, random);
            if (pool.isEmpty()) continue;

            // Remove Silk Touch/Fortune if the item already has the conflicting one
            if (item.containsEnchantment(Enchantment.SILK_TOUCH)) {
                pool.removeIf(TieredEnchantTables::isFortune);
            } else if (item.containsEnchantment(Enchantment.FORTUNE)) {
                pool.removeIf(TieredEnchantTables::isSilkTouch);
            }

            Collections.shuffle(pool, random);

            Enchantment primary      = pool.get(0);
            int         primaryLevel = EnchantmentTierManager.getEnchantLevelForCost(scaledCost, primary, player);

            // Note: Standard Minecraft client displays levels 11+ as digits (11, 12, 13)
            // in the table GUI. Roman numeral display is handled via lore on the item itself.
            offers[slot] = new EnchantmentOffer(primary, primaryLevel, scaledCost);
            event.getExpLevelCostsOffered()[slot] = scaledCost;

            List<EnchantmentOffer> slotOffers = new ArrayList<>();
            slotOffers.add(offers[slot]);

            // Massive variety: bonus enchants on slot 1 and slot 2 when bookshelves are present.
            if (maxLevel > 0) {
                int extraCount = EnchantmentTierManager.getBonusEnchantCount(slot, scaledCost);

                if (extraCount > 0) {
                    Set<Enchantment> used = new HashSet<>();
                    used.add(primary);

                    for (int j = 1; j < pool.size() && extraCount > 0; j++) {
                        Enchantment candidate = pool.get(j);
                        if (used.contains(candidate)) continue;

                        // Protection cap: increased to 3 distinct types per item
                        if (isProtectionEnchant(candidate)) {
                            int protCount = 0;
                            for (Enchantment e : item.getEnchantments().keySet())
                                if (isProtectionEnchant(e)) protCount++;
                            for (EnchantmentOffer o : slotOffers)
                                if (isProtectionEnchant(o.getEnchantment())) protCount++;
                            if (protCount >= 3) continue;
                        }

                        // Silk Touch / Fortune conflict check
                        if (isSilkTouch(candidate) && (hasFortune(used) || item.containsEnchantment(Enchantment.FORTUNE))) continue;
                        if (isFortune(candidate) && (hasSilkTouch(used) || item.containsEnchantment(Enchantment.SILK_TOUCH))) continue;

                        int extraLevel = EnchantmentTierManager.getEnchantLevelForCost(scaledCost, candidate, player);
                        slotOffers.add(new EnchantmentOffer(candidate, extraLevel, scaledCost));
                        used.add(candidate);
                        extraCount--;
                    }
                }
            }

            sessionOffers.put(slot, slotOffers);
        }

        pendingOffers.put(uid, sessionOffers);

        // Preview high-level enchants (levels > 10) in the table tooltip.
        VanillaEnchantDisplay.addPreviewLore(item, sessionOffers);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Step 2 — Apply exactly what was stored
    // ─────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        ItemStack item = event.getItem();
        if (isScythe(item)) return;

        // Adaptive tables handle their own enchant application — skip them here.
        if (isAdaptiveTable.test(event.getEnchantBlock())) return;

        Map<Integer, List<EnchantmentOffer>> sessionOffers =
                pendingOffers.remove(player.getUniqueId());
        if (sessionOffers == null) return;

        List<EnchantmentOffer> offers = sessionOffers.get(event.whichButton());
        if (offers == null || offers.isEmpty()) return;

        // Discard vanilla's enchantment choices entirely.
        Map<Enchantment, Integer> toAdd = event.getEnchantsToAdd();
        toAdd.clear();

        // Enforce the cost shown in the tooltip.
        event.setExpLevelCost(offers.get(0).getCost());

        // Apply all offers, respecting the 3-distinct-protection-types cap.
        int protectionCount = 0;
        for (Enchantment e : item.getEnchantments().keySet())
            if (isProtectionEnchant(e)) protectionCount++;

        for (EnchantmentOffer offer : offers) {
            Enchantment ench  = offer.getEnchantment();
            int         level = offer.getEnchantmentLevel();

            // Silk Touch / Fortune conflict check
            if (isSilkTouch(ench) && (hasFortune(toAdd.keySet()) || item.containsEnchantment(Enchantment.FORTUNE))) continue;
            if (isFortune(ench) && (hasSilkTouch(toAdd.keySet()) || item.containsEnchantment(Enchantment.SILK_TOUCH))) continue;

            if (isProtectionEnchant(ench)) {
                boolean alreadyOnItem = item.getEnchantments().containsKey(ench);
                if (alreadyOnItem) {
                    toAdd.put(ench, level);          // upgrade existing — always OK
                } else if (protectionCount < 3) {
                    toAdd.put(ench, level);
                    protectionCount++;
                }
                // 4th distinct protection type: silently dropped
            } else {
                toAdd.put(ench, level);
            }
        }

        // Deferred task: forces all enchants onto the item bypassing conflict checks,
        // then runs the shared post-application step (see applyFinalEnchants below).
        final Map<Enchantment, Integer> finalToAdd = new HashMap<>(toAdd);
        org.bukkit.Bukkit.getScheduler().runTask(
                org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(getClass()),
                () -> applyFinalEnchants(item, player, finalToAdd));
    }

    /**
     * Shared post-application step: stores the true (possibly over-cap)
     * enchant levels in PDC, clamps the live enchant map to the display cap,
     * hides vanilla's enchant glyphs, and does one unified managed-lore
     * rebuild.
     * <p>
     * This used to be an inline lambda inside {@link #onEnchantItem}. It is
     * now a standalone method so it can also be called from
     * {@code MaxTableWithoutBookShelves.EnchantStrategy#finalizeApplication},
     * which CorovaCore wires up so adaptive (no-bookshelf) enchant tables
     * apply and display enchants identically to normal tiered tables instead
     * of silently losing over-cap levels or leaving stale lore. Normal tables
     * and adaptive tables now share this exact code path — they can never
     * drift out of sync.
     * <p>
     * Not scheduled internally: both current call sites (this class's own
     * {@code onEnchantItem}, and the adaptive-table bridge in CorovaCore)
     * already defer by one tick before calling this, which is required
     * because the item's meta must reflect whatever vanilla/our own click
     * handling just wrote to it.
     *
     * @param item            the enchanted item
     * @param player          the enchanting player (used for lore context,
     *                        e.g. trim bonuses/amplifications)
     * @param appliedEnchants the exact enchantments (and true levels) to
     *                        write to the item
     */
    public static void applyFinalEnchants(ItemStack item, Player player, Map<Enchantment, Integer> appliedEnchants) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        for (Map.Entry<Enchantment, Integer> e : appliedEnchants.entrySet()) {
            VanillaEnchantDisplay.storeTrueLevel(meta, e.getKey(), e.getValue());
            int clamped = Math.min(e.getValue(), VanillaEnchantDisplay.DISPLAY_CAP);
            meta.addEnchant(e.getKey(), clamped, true);
        }
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);

        // Now do ONE unified lore rebuild: canonical order + vanilla lines.
        // Using updateLore with player context ensures trim bonuses/amplifications are correct.
        MutationManager.getInstance().updateLore(item, meta, player);
        item.setItemMeta(meta);
    }

    // Clear stale state when the player closes the enchanting table without clicking.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getType() == InventoryType.ENCHANTING) {
            UUID uid = event.getPlayer().getUniqueId();
            pendingOffers.remove(uid);

            // Strip preview lore if the player closes the table without enchanting.
            ItemStack item = event.getInventory().getItem(0);
            if (item != null) {
                VanillaEnchantDisplay.removePreviewLore(item);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Enchantment pool — intentionally relaxes vanilla's mutual-exclusion rules
    // ─────────────────────────────────────────────────────────────────────────

    public static List<Enchantment> getAvailableEnchants(ItemStack item) {
        return getAvailableEnchants(item, null, 0, new Random());
    }

    public static List<Enchantment> getAvailableEnchants(ItemStack item, Player player) {
        return getAvailableEnchants(item, player, 0, new Random());
    }

    /**
     * Builds the enchantment pool for the given item, slot, and RNG.
     *
     * For books, the {@code slot} and {@code random} parameters are used to
     * select an independent theme per slot — so slot 0, slot 1, and slot 2 can
     * each show a different category (sword, bow, armor, etc.) in the same open.
     * The theme is derived from a combination of the player's enchant seed,
     * the current time, and the slot index, so it also changes every re-open.
     *
     * @param item   the item being enchanted
     * @param player the enchanting player (may be null for non-table callers)
     * @param slot   which table slot (0/1/2) — only meaningful for books
     * @param random the per-slot RNG — used for pool shuffling
     */
    public static List<Enchantment> getAvailableEnchants(ItemStack item, Player player, int slot, Random random) {
        Material mat = item.getType();
        List<Enchantment> pool = new ArrayList<>();

        if (mat == Material.BOOK && player != null) {
            // Each table open picks a random starting offset across the 8 themes,
            // then assigns consecutive themes to slots 0, 1, 2 — guaranteeing that
            // all three options are always distinct (e.g. sword / bow / helmet).
            // The offset is derived from the player's enchant seed XOR a per-minute
            // time window, so it changes every time the table is opened.
            int playerSeed  = player.getEnchantmentSeed();
            long timeWindow = System.currentTimeMillis() / 60_000L; // changes each minute
            int offset = (int) (((long) playerSeed ^ timeWindow) & 0x7FFFFFFFL) % 8;
            int theme  = (offset + slot) % 8; // slot 0,1,2 get offset, offset+1, offset+2

            org.bukkit.Bukkit.getLogger().info("[EnchantDebug] Book pool | player="
                    + player.getName() + " | seed=" + playerSeed + " | slot=" + slot
                    + " | timeWindow=" + timeWindow + " | offset=" + offset + " | theme=" + theme);

            switch (theme) {
                case 0: PoolHelper.addSwordEnchants(pool, item, random); break;
                case 1: PoolHelper.addBowEnchants(pool, item, random); break;
                case 2: PoolHelper.addToolEnchants(pool, item); break;
                case 3: PoolHelper.addWandEnchants(pool, item); break;
                case 4: PoolHelper.addArmorEnchants(pool, item, Material.DIAMOND_HELMET); break;
                case 5: PoolHelper.addArmorEnchants(pool, item, Material.DIAMOND_CHESTPLATE); break;
                case 6: PoolHelper.addArmorEnchants(pool, item, Material.DIAMOND_LEGGINGS); break;
                case 7: PoolHelper.addArmorEnchants(pool, item, Material.DIAMOND_BOOTS); break;
                default: PoolHelper.addSwordEnchants(pool, item, random); break;
            }
            // UNBREAKING and MENDING are valid on every item a book can target.
            if (!pool.contains(Enchantment.UNBREAKING)) pool.add(Enchantment.UNBREAKING);
            if (!pool.contains(Enchantment.MENDING))    pool.add(Enchantment.MENDING);

            org.bukkit.Bukkit.getLogger().info("[EnchantDebug] Book pool result | slot=" + slot
                    + " | size=" + pool.size() + " | enchants=" + pool.stream()
                    .map(e -> e.getKey().getKey())
                    .collect(java.util.stream.Collectors.joining(", ")));
            Collections.shuffle(pool, random);
            return pool;
        }

        if (mat == Material.BOOK && player == null) {
            // Called without a player — this should not happen for enchant tables.
            // Log a warning so the call site can be identified and fixed.
            org.bukkit.Bukkit.getLogger().warning("[EnchantDebug] getAvailableEnchants called"
                    + " with BOOK but player=null — falling through to UNBREAKING only!"
                    + " Stack: " + java.util.Arrays.toString(Thread.currentThread().getStackTrace()));
        }

        pool.add(Enchantment.UNBREAKING);

        if (isArmor(mat)) {
            PoolHelper.addArmorEnchants(pool, item, mat);
        } else if (isWand(item)) {
            PoolHelper.addWandEnchants(pool, item);
        } else if (isSword(mat)) {
            PoolHelper.addSwordEnchants(pool, item, random);
        } else if (isBow(mat)) {
            PoolHelper.addBowEnchants(pool, item, random);
        } else if (isTool(mat)) {
            PoolHelper.addToolEnchants(pool, item);
        } else if (isFishingRod(mat)) {
            PoolHelper.addFishingRodEnchants(pool, item);
        } else {
            // Safety net: for any item type not explicitly handled above, collect every
            // enchantment vanilla considers valid for it so no item is ever left with
            // only Unbreaking.
            for (Enchantment e : Registry.ENCHANTMENT) {
                if (!pool.contains(e) && !e.isCursed() && e.canEnchantItem(item)) {
                    pool.add(e);
                }
            }
        }

        return pool;
    }

    private static class PoolHelper {
        private static void addArmorEnchants(List<Enchantment> pool, ItemStack item, Material mat) {
            if (!pool.contains(Enchantment.UNBREAKING)) pool.add(Enchantment.UNBREAKING);
            int protCount = 0;
            if (item != null && item.getType() != Material.BOOK) {
                for (Enchantment e : item.getEnchantments().keySet())
                    if (isProtectionEnchant(e)) protCount++;
            }

            if (protCount < 3) {
                addIfMissing(pool, item, Enchantment.PROTECTION);
                addIfMissing(pool, item, Enchantment.FIRE_PROTECTION);
                addIfMissing(pool, item, Enchantment.BLAST_PROTECTION);
                addIfMissing(pool, item, Enchantment.PROJECTILE_PROTECTION);
            } else {
                if (item != null && item.getType() != Material.BOOK) {
                    for (Enchantment e : item.getEnchantments().keySet())
                        if (isProtectionEnchant(e)) pool.add(e);
                }
            }
            if (isBoots(mat)) {
                pool.add(Enchantment.FEATHER_FALLING);
                pool.add(Enchantment.DEPTH_STRIDER);
            }
            if (isHelmet(mat)) {
                pool.add(Enchantment.RESPIRATION);
                pool.add(Enchantment.AQUA_AFFINITY);
            }
            if (mat.name().endsWith("_CHESTPLATE") || mat.name().endsWith("_LEGGINGS")) {
                pool.add(Enchantment.THORNS);
            }
        }

        private static void addSwordEnchants(List<Enchantment> pool, ItemStack item, Random random) {
            if (!pool.contains(Enchantment.UNBREAKING)) pool.add(Enchantment.UNBREAKING);
            pool.add(Enchantment.SHARPNESS);
            pool.add(Enchantment.SMITE);
            pool.add(Enchantment.BANE_OF_ARTHROPODS);
            if (random.nextDouble() < 0.20) pool.add(Enchantment.KNOCKBACK);
            pool.add(Enchantment.FIRE_ASPECT);
            pool.add(Enchantment.LOOTING);
            pool.add(Enchantment.SWEEPING_EDGE);
        }

        private static void addBowEnchants(List<Enchantment> pool, ItemStack item, Random random) {
            if (!pool.contains(Enchantment.UNBREAKING)) pool.add(Enchantment.UNBREAKING);
            pool.add(Enchantment.POWER);
            pool.add(Enchantment.SMITE);
            pool.add(Enchantment.BANE_OF_ARTHROPODS);
            if (random.nextDouble() < 0.20) pool.add(Enchantment.PUNCH);
            pool.add(Enchantment.FLAME);
            pool.add(Enchantment.INFINITY);
            pool.add(Enchantment.LOOTING);
        }

        private static void addToolEnchants(List<Enchantment> pool, ItemStack item) {
            if (!pool.contains(Enchantment.UNBREAKING)) pool.add(Enchantment.UNBREAKING);
            pool.add(Enchantment.EFFICIENCY);
            pool.add(Enchantment.FORTUNE);
            pool.add(Enchantment.SILK_TOUCH);
        }

        private static void addWandEnchants(List<Enchantment> pool, ItemStack item) {
            if (!pool.contains(Enchantment.UNBREAKING)) pool.add(Enchantment.UNBREAKING);
            pool.add(Enchantment.POWER);
            pool.add(Enchantment.SMITE);
            pool.add(Enchantment.BANE_OF_ARTHROPODS);
            pool.add(Enchantment.LOOTING);
        }

        private static void addFishingRodEnchants(List<Enchantment> pool, ItemStack item) {
            if (!pool.contains(Enchantment.UNBREAKING)) pool.add(Enchantment.UNBREAKING);
            pool.add(Enchantment.LUCK_OF_THE_SEA);
            pool.add(Enchantment.LURE);
        }

        private static void addIfMissing(List<Enchantment> list, ItemStack item, Enchantment ench) {
            if (item == null || item.getType() == Material.BOOK) {
                list.add(ench);
            } else if (!item.getEnchantments().containsKey(ench)) {
                list.add(ench);
            }
        }
    }

    private static void addIfMissing(List<Enchantment> list, ItemStack item, Enchantment ench) {
        if (!item.getEnchantments().containsKey(ench)) list.add(ench);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Cost → enchantment level (Delegator for CorovaCore compatibility)
    // ─────────────────────────────────────────────────────────────────────────

    public static int getEnchantLevelForCost(int cost, Enchantment enchantment, Player player) {
        return EnchantmentTierManager.getEnchantLevelForCost(cost, enchantment, player);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Material helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static boolean isArmor(Material m) {
        String n = m.name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
    }

    private static boolean isHelmet(Material m) { return m.name().endsWith("_HELMET"); }

    private static boolean isBoots(Material m) {
        return m.name().endsWith("_BOOTS");
    }

    private static boolean isSword(Material m) { return m.name().endsWith("_SWORD"); }
    private static boolean isBow(Material m)   { return m == Material.BOW || m == Material.CROSSBOW; }
    private static boolean isWand(ItemStack item) {
        return WandEnchantListener.isWand(item);
    }

    private static boolean isTool(Material m) {
        String n = m.name();
        return n.endsWith("_PICKAXE") || n.endsWith("_AXE")
                || n.endsWith("_SHOVEL") || n.endsWith("_HOE");
    }

    private static boolean isFishingRod(Material m) {
        return m == Material.FISHING_ROD;
    }

    private static boolean isProtectionEnchant(Enchantment e) {
        return e == Enchantment.PROTECTION      || e == Enchantment.FIRE_PROTECTION
                || e == Enchantment.BLAST_PROTECTION || e == Enchantment.PROJECTILE_PROTECTION;
    }

    private boolean isScythe(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        List<Material> scytheMaterials = List.of(
                Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE,
                Material.DIAMOND_HOE, Material.NETHERITE_HOE);
        Material copperHoe = Material.matchMaterial("COPPER_HOE");
        if (copperHoe != null && item.getType() == copperHoe)
            return com.example.corovaItems.ItemManager.getInstance().isCorovaItem(item);
        if (!scytheMaterials.contains(item.getType())) return false;
        return com.example.corovaItems.ItemManager.getInstance().isCorovaItem(item);
    }

    private static boolean isSilkTouch(Enchantment e) {
        return e.equals(Enchantment.SILK_TOUCH);
    }

    private static boolean isFortune(Enchantment e) {
        return e.equals(Enchantment.FORTUNE);
    }

    private static boolean hasSilkTouch(Collection<Enchantment> enchants) {
        return enchants.stream().anyMatch(TieredEnchantTables::isSilkTouch);
    }

    private static boolean hasFortune(Collection<Enchantment> enchants) {
        return enchants.stream().anyMatch(TieredEnchantTables::isFortune);
    }
}