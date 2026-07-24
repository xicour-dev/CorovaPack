package com.example.corovaItems.Enchantments.GreaterEnchantmentSystem;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Central authority for item lore ordering.
 *
 * Guaranteed order:
 *   1. Base / vanilla lore  (any line NOT owned by a managed block)
 *   2. Vanilla enchant lines  (injected by VanillaEnchantDisplay, e.g. "§7Sharpness XIII")
 *   3. Custom enchant lines (bracketed by ENCHANT_START / ENCHANT_END sentinels)
 *   4. Mutation lines       (bracketed by MUTATION_START / MUTATION_END sentinels)
 *
 * ── Sentinel bracket design ──────────────────────────────────────────────────
 * Instead of trying to identify managed lines by their visible content
 * (which breaks the moment a mutation description, name, color, or category
 * tag changes between plugin versions), each managed block is wrapped in a
 * pair of invisible sentinel markers:
 *
 *   §r§0§e§r   ← block-start marker (custom enchants)
 *   §r§0§7§r   ← block-end marker   (custom enchants)
 *   §r§0§m§r   ← block-start marker (mutations)
 *   §r§0§n§r   ← block-end marker   (mutations)
 *
 * IMPORTANT: these markers are never written as their own stand-alone lore
 * line. A lore line that contains nothing but color codes still occupies a
 * blank ROW in the client's tooltip — Minecraft doesn't collapse "invisible"
 * lines, it just renders an empty line where they sit. The old implementation
 * wrote each sentinel as its own line (plus an extra "" spacer between the
 * enchant and mutation blocks), which is exactly what produced the stack of
 * blank rows pushing the custom-enchant/mutation lore down and apart.
 *
 * The fix: the start marker is PREPENDED onto the first real content line of
 * the block, and the end marker is APPENDED onto the last real content line
 * of the block (same line if the block is a single line). Since both markers
 * are pure color-code sequences terminated by §r, gluing them onto a real
 * line adds zero visible characters and does NOT create a new row. On
 * rebuild, {@link #stripManagedBlocks} finds block boundaries with
 * startsWith/endsWith instead of exact-line equality.
 *
 * On rebuild, rebuildLore() deletes every line that falls inside (and
 * including) a sentinel-marked pair, then re-inserts the freshly-generated
 * block. This means stale lore from ANY previous version — changed
 * descriptions, removed category tags, renamed mutations, rephrased cooldown
 * lines, etc. — is always completely replaced, regardless of what those
 * lines say.
 *
 * Both {@link CorovaEnchantments#refreshLore(ItemMeta)} and
 * {@link MutationManager#updateLore(ItemStack, org.bukkit.entity.Player)}
 * delegate here so the order is enforced regardless of which system writes last.
 *
 * Vanilla enchantment lines from {@link VanillaEnchantDisplay} are stripped here
 * so that {@link VanillaEnchantDisplay#refreshDisplay(ItemStack)} can re-inject
 * them at the correct position after the lore is rebuilt.
 */
public final class CustomEnchantMutationFormatting {

    private CustomEnchantMutationFormatting() {}

    // ── Sentinel constants ────────────────────────────────────────────────────
    // Each sentinel is a short string that:
    //   • starts with §r so it resets any prior color (invisible lead-in),
    //   • uses §0 (black) and a unique digit/letter so it never collides with
    //     gradient-colored custom enchant lines or VanillaEnchantDisplay's own
    //     §f§0§f§0§r signature,
    //   • ends with §r so trailing characters are also reset (net: invisible).
    //
    // Two pairs — one for the custom-enchant block, one for the mutation block.
    // They must be different from each other AND from VanillaEnchantDisplay's
    // SIGNATURE ("§f§0§f§0§r") and PREVIEW_SIGNATURE ("§f§0§f§1§r").
    //
    // These are GLUED onto real content lines (see class javadoc) — never
    // written as their own lore line.

    /** Invisible marker glued to the START of the custom-enchant block's first line. */
    public static final String ENCHANT_START   = "\u00a7r\u00a70\u00a7e\u00a7r"; // §r§0§e§r
    /** Invisible marker glued to the END of the custom-enchant block's last line. */
    public static final String ENCHANT_END     = "\u00a7r\u00a70\u00a77\u00a7r"; // §r§0§7§r
    /** Invisible marker glued to the START of the mutation block's first line. */
    public static final String MUTATION_START  = "\u00a7r\u00a70\u00a7m\u00a7r"; // §r§0§m§r
    /** Invisible marker glued to the END of the mutation block's last line. */
    public static final String MUTATION_END    = "\u00a7r\u00a70\u00a7n\u00a7r"; // §r§0§n§r

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Rebuilds the lore on {@code meta} in the canonical order.
     *
     * The item's PDC must already contain the up-to-date enchant and mutation
     * data before this is called — it only reads PDC, never writes it.
     *
     * After this method returns, the caller must call {@code item.setItemMeta(meta)},
     * then immediately call {@link VanillaEnchantDisplay#refreshDisplay(ItemStack)}
     * so that vanilla enchantment lore lines are re-injected at the correct position.
     *
     * @param item  the live ItemStack (needed so MutationManager can read its PDC)
     * @param meta  the ItemMeta to mutate (caller must call item.setItemMeta after)
     */
    public static void rebuildLore(ItemStack item, ItemMeta meta) {
        if (item == null || meta == null) return;

        List<String> existing = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // ── Strip all managed blocks ──────────────────────────────────────────
        // Remove any lines bracketed by sentinel markers (inclusive of the
        // marked lines themselves). This reliably removes ALL old managed content
        // regardless of what those lines say, fixing the core bug where changed
        // mutation descriptions / removed lore lines / renamed mutations survived
        // the rebuild pass and accumulated as ghost lore.
        //
        // Also strip VanillaEnchantDisplay lines so refreshDisplay() can
        // re-inject them cleanly after we set the new lore.
        existing = stripManagedBlocks(existing);
        existing.removeIf(VanillaEnchantDisplay::isOverLevelLoreLine);

        // Also strip legacy lines that predate the sentinel system. These are
        // lines that pass isEnchantLine() or the old isMutationLine() check.
        // This one-time cleanup ensures items mutated before this refactor
        // don't carry forward leftover unbracketed content.
        existing.removeIf(line -> isLegacyEnchantLine(line) || isLegacyMutationLine(line, item));

        // Strip ALL previously-written trim/synergy lines and empty lines so they
        // are not trapped in 'baseLore' and pushed above our managed blocks.
        MutationUtils.stripLoreComponents(existing);

        // 'existing' now contains ONLY true base / user-written lore.
        List<String> baseLore = new ArrayList<>(existing);

        // ── Build custom enchant block ────────────────────────────────────────
        List<String> enchantLines = withMarkers(buildEnchantLines(meta), ENCHANT_START, ENCHANT_END);

        // ── Build mutation block ──────────────────────────────────────────────
        List<String> mutationLines = withMarkers(buildMutationLines(item), MUTATION_START, MUTATION_END);

        // ── Assemble ─────────────────────────────────────────────────────────
        // NOTE: no "" spacer lines are inserted between baseLore / enchant
        // block / mutation block. Vanilla enchants, the custom enchant block,
        // and the mutation block are meant to read as one contiguous group
        // (e.g. "Sharpness X" / "Smite III" / "Web Slinger III" / "Bleed II"
        // back-to-back with no gaps). Every blank row that used to appear
        // between sections came from either a standalone sentinel line or
        // this spacer — both are gone now.
        List<String> finalLore = new ArrayList<>(baseLore);

        if (!enchantLines.isEmpty()) {
            finalLore.addAll(enchantLines);
        }

        if (!mutationLines.isEmpty()) {
            finalLore.addAll(mutationLines);
        }

        // Re-inject basic per-piece trim lore (not viewer-dependent).
        // This is a visually distinct sub-system (armor trims), so it still
        // gets its own spacer before it when something precedes it.
        List<String> pieceLore = com.example.corovaItems.ArmorTrims.TrimCalculator.getTrimPieceLore(item);
        if (!pieceLore.isEmpty()) {
            if (!finalLore.isEmpty() && !finalLore.get(finalLore.size() - 1).trim().isEmpty()) {
                finalLore.add("");
            }
            finalLore.addAll(pieceLore);
        }

        meta.setLore(finalLore);

        // NOTE: Callers must call item.setItemMeta(meta) and then
        // VanillaEnchantDisplay.refreshDisplay(item) so over-cap lines are
        // re-injected at the top of baseLore in the canonical position.
    }

    // ── Marker gluing ─────────────────────────────────────────────────────────

    /**
     * Returns a copy of {@code contentLines} with {@code startMarker} glued to
     * the front of the first line and {@code endMarker} glued to the end of
     * the last line (same line, both ends, if there's only one line).
     *
     * Both markers are pure color-code sequences ending in §r, so gluing them
     * onto real text adds no visible characters and creates no new row —
     * unlike the old approach of writing them as their own lore lines.
     */
    private static List<String> withMarkers(List<String> contentLines, String startMarker, String endMarker) {
        if (contentLines.isEmpty()) return contentLines;
        List<String> result = new ArrayList<>(contentLines);
        result.set(0, startMarker + result.get(0));
        int lastIdx = result.size() - 1;
        result.set(lastIdx, result.get(lastIdx) + endMarker);
        return result;
    }

    // ── Sentinel-based strip ──────────────────────────────────────────────────

    /**
     * Returns a new list with every marker-bracketed block removed (the line
     * carrying the start marker, every line up to and including the line
     * carrying the matching end marker, are all deleted). Lines outside any
     * bracket are kept verbatim.
     *
     * Detection uses startsWith (for the start marker) and endsWith (for the
     * end marker) rather than exact-line equality, since the markers are now
     * glued onto real content lines rather than standing alone.
     *
     * Handles the case where a block was opened (start marker present) but
     * the matching end marker is missing (e.g. corrupted lore, or a
     * single-line block where the same line closes it immediately): in the
     * "missing end" case, everything from the start marker to the end of the
     * list is removed, which is the safest recovery behaviour.
     *
     * Public so other systems that need to strip managed blocks without
     * calling rebuildLore (e.g. {@link GrindstoneEnchantCleaner}) can reuse
     * this exact logic instead of maintaining a second copy that can drift
     * out of sync with the marker scheme.
     */
    public static List<String> stripManagedBlocks(List<String> lines) {
        List<String> result = new ArrayList<>();
        boolean inBlock = false;
        String awaitedEnd = null;

        for (String line : lines) {
            if (!inBlock) {
                if (line.startsWith(ENCHANT_START)) {
                    inBlock = true;
                    awaitedEnd = ENCHANT_END;
                } else if (line.startsWith(MUTATION_START)) {
                    inBlock = true;
                    awaitedEnd = MUTATION_END;
                } else {
                    result.add(line);
                    continue;
                }
                // Single-line block: the same line carries both the start
                // marker and the end marker.
                if (line.endsWith(awaitedEnd)) {
                    inBlock = false;
                    awaitedEnd = null;
                }
            } else {
                if (line.endsWith(awaitedEnd)) {
                    inBlock = false;
                    awaitedEnd = null;
                }
                // All lines from the start marker to (and including) the end
                // marker are dropped — that's the whole point.
            }
        }

        return result;
    }

    // ── Sentinel detection helpers (used by GrindstoneEnchantCleaner etc.) ───

    /**
     * Returns true if {@code line} carries any marker written by this class —
     * either as a prefix (block start) or a suffix (block end).
     *
     * Note this now means "carries a marker", not "is entirely a marker" —
     * since markers are glued onto real content lines, a line matching this
     * check may still have visible text on it.
     */
    public static boolean isSentinelLine(String line) {
        if (line == null) return false;
        return line.startsWith(ENCHANT_START) || line.endsWith(ENCHANT_END)
                || line.startsWith(MUTATION_START) || line.endsWith(MUTATION_END);
    }

    // ── Legacy detection (one-time cleanup for pre-sentinel items) ────────────

    /**
     * Returns true if {@code line} is a custom-enchant display line written by
     * the old (pre-sentinel) system. Used exclusively for one-time migration
     * cleanup inside rebuildLore; new code never relies on this to identify
     * managed lines at runtime.
     *
     * Robust against color/gradient changes by stripping colors before comparing.
     */
    private static boolean isLegacyEnchantLine(String line) {
        if (line == null) return false;
        String stripped = ChatColor.stripColor(line).trim();
        for (String displayName : CorovaEnchantments.DISPLAY_NAME.values()) {
            if (stripped.startsWith(displayName)) {
                String potentialRoman = stripped.substring(displayName.length()).trim();
                if (potentialRoman.isEmpty() || isRoman(potentialRoman)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isRoman(String s) {
        return s.matches("^[IVXLCDM]+$");
    }

    /**
     * Returns true if {@code line} appears in any mutation's lore for any level
     * under the old (pre-sentinel) system. Used exclusively for one-time
     * migration cleanup inside rebuildLore.
     *
     * Robust against color changes by stripping colors before comparing.
     */
    private static boolean isLegacyMutationLine(String line, ItemStack item) {
        if (line == null) return false;
        String strippedLine = ChatColor.stripColor(line);
        MutationManager mm = MutationManager.getInstance();
        if (mm == null) return false;
        for (MutationType type : MutationType.values()) {
            Mutation m = mm.getMutation(type);
            if (m == null) continue;
            for (int lvl = 1; lvl <= m.getMaxLevel(); lvl++) {
                for (String mutationLoreLine : m.getLore(lvl)) {
                    if (ChatColor.stripColor(mutationLoreLine).equals(strippedLine)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ── Public compatibility shims ────────────────────────────────────────────
    // Kept so external callers (CorovaEnchantments, etc.) that called the old
    // static helpers don't need to change their call sites.

    /**
     * @deprecated Use {@link #isSentinelLine(String)} for the new marker
     *             system, or let rebuildLore() handle all stripping internally.
     *             This method is retained for backward compatibility only and
     *             delegates to the legacy check.
     */
    @Deprecated
    public static boolean isEnchantLine(String line) {
        return isLegacyEnchantLine(line);
    }

    /**
     * @deprecated Mutation line detection is now handled entirely through
     *             marker brackets inside rebuildLore(). This method is
     *             retained for backward compatibility only.
     */
    @Deprecated
    public static boolean isMutationLine(String line, ItemStack item) {
        return isLegacyMutationLine(line, item);
    }

    // ── Private builders ──────────────────────────────────────────────────────

    /** Builds the enchant lines from the item's PDC with gradients applied. */
    private static List<String> buildEnchantLines(ItemMeta meta) {
        List<String> lines = new ArrayList<>();
        if (meta == null) return lines;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String id1 = pdc.get(CorovaEnchantments.KEY_ENCHANT_ID, PersistentDataType.STRING);
        if (id1 != null) {
            int lvl1 = pdc.getOrDefault(CorovaEnchantments.KEY_ENCHANT_LVL, PersistentDataType.INTEGER, 1);
            String label = CorovaEnchantments.DISPLAY_NAME.getOrDefault(id1, id1);
            String roman = CorovaEnchantments.toRoman(lvl1);
            lines.add(EnchantmentBook.applyEnchantmentGradient(id1, label + " " + roman));
        }

        String id2 = pdc.get(CorovaEnchantments.KEY_ENCHANT_2_ID, PersistentDataType.STRING);
        if (id2 != null) {
            int lvl2 = pdc.getOrDefault(CorovaEnchantments.KEY_ENCHANT_2_LVL, PersistentDataType.INTEGER, 1);
            String label = CorovaEnchantments.DISPLAY_NAME.getOrDefault(id2, id2);
            String roman = CorovaEnchantments.toRoman(lvl2);
            lines.add(EnchantmentBook.applyEnchantmentGradient(id2, label + " " + roman));
        }

        return lines;
    }

    /** Builds mutation lore lines from the MutationManager for the given item. */
    private static List<String> buildMutationLines(ItemStack item) {
        List<String> lines = new ArrayList<>();
        MutationManager mm = MutationManager.getInstance();
        if (mm == null || item == null) return lines;

        Map<MutationType, Integer> currentMutations = mm.getMutations(item);
        for (Map.Entry<MutationType, Integer> entry : currentMutations.entrySet()) {
            Mutation m = mm.getMutation(entry.getKey());
            if (m != null) {
                lines.addAll(m.getLore(entry.getValue()));
            }
        }
        return lines;
    }
}