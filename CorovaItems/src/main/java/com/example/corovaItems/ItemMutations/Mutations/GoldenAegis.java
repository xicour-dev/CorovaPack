package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ArmorTrims.PlayerTrimProfile;
import com.example.corovaItems.ArmorTrims.TrimCalculator;
import com.example.corovaItems.ArmorTrims.TrimManager;
import com.example.corovaItems.ArmorTrims.TrimMaterialType;
import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Golden Aegis — Gold armor mutation.
 *
 * Base absorption per GA piece (no gold trims equipped):
 *   Level 1: +1.5 hearts (3.0 HP) per piece
 *   Level 2: +2.5 hearts (5.0 HP) per piece
 *
 * Gold Trim Synergy:
 *   When at least one GA piece is worn, each gold trim piece grants:
 *     +2.5♥ base (5.0 HP)  — unchanged flat passive
 *     +1.5♥ synergy bonus  (3.0 HP) if average GA level is 1
 *     +2.5♥ synergy bonus  (5.0 HP) if average GA level is 2
 *
 *   The synergy bonus scales with the average level of all GA pieces worn,
 *   so mixed-level sets interpolate smoothly between the two thresholds.
 *
 *   Full 4× GA II + 4× gold trims:
 *     Mutation HP  = 4 × 5.0  = 20 HP (10♥)
 *     Trim base HP = 4 × 5.0  = 20 HP (10♥)
 *     Synergy HP   = 4 × 5.0  = 20 HP (10♥)
 *     Total        = 60 HP    = 30♥  = 3 rows ✓
 *
 *   Full 4× GA I + 4× gold trims:
 *     Mutation HP  = 4 × 3.0  = 12 HP (6♥)
 *     Trim base HP = 4 × 5.0  = 20 HP (10♥)
 *     Synergy HP   = 4 × 3.0  = 12 HP (6♥)
 *     Total        = 44 HP    = 22♥  = 2 rows + 2♥
 *
 * Regeneration:
 *   When absorption drops below cap it regenerates at 0.5 HP every 3 seconds
 *   (roughly half the speed of natural health regen).
 *
 *   Full Gold Trim Set Bonus (4/4 pure gold trims — see TrimCalculator.hasPureSet):
 *     Regeneration rate doubles to 1.0 HP every 3 seconds. Independent of whether
 *     any Golden Aegis mutation pieces are worn — this is purely a trim set bonus.
 *
 * Emerald / Recovery amplification:
 *   Only the base mutation HP (GA pieces) is amplified by the Emerald recovery
 *   multiplier. Trim base HP and synergy HP are flat passives and are NOT scaled,
 *   preventing inflation beyond the piece-count-based hard cap.
 *
 *   Hard cap = (mutationPieces × 5.0) + (effectiveGoldTrims × 5.0)
 *            + (effectiveGoldTrims × synergyHPPerTrim)  ← uses actual avg level, not always LVL2
 *   Applied AFTER emerald amplification to prevent over-inflation.
 *
 * Gold armor only.
 */
public class GoldenAegis implements Mutation, Listener {

    private final MutationManager mutationManager;
    private final JavaPlugin plugin;

    // Per-player: the target absorption value this mutation is responsible for.
    private final Map<UUID, Double> targetAbsorption = new HashMap<>();
    // Per-player: last regen time (currently unused — regen is tick-based via scheduler)
    private final Map<UUID, Long> lastRegenTime = new HashMap<>();

    // Regen: restore 0.5 HP every 3 seconds (vanilla is ~1 HP/4s at full food)
    private static final double REGEN_AMOUNT_PER_TICK = 0.5;

    // Full Gold Trim Set bonus (4/4 pure gold trims): doubles the regen rate above.
    private static final double FULL_GOLD_SET_REGEN_MULTIPLIER = 2.0;

    // Synergy bonus HP per gold trim piece based on average GA level
    // avgLevel == 1 → +3.0 HP (+1.5♥),  avgLevel == 2 → +5.0 HP (+2.5♥)
    private static final double SYNERGY_HP_LVL1 = 3.0;
    private static final double SYNERGY_HP_LVL2 = 5.0;

    public GoldenAegis(MutationManager mutationManager) {
        this.mutationManager = mutationManager;
        this.plugin = JavaPlugin.getProvidingPlugin(this.getClass());
        startRegenTask();
    }

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.DEFENSIVE, MutationCategory.RECOVERY);
    }

    @Override
    public String getColor() {
        return "#FFD700";
    }

    public String getName() {
        return "Golden Aegis";
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
        String hearts = (level == 1) ? "1.5" : "2.5";
        desc.add(ChatColor.GRAY + "Each gold piece: +" + ChatColor.YELLOW + hearts + " absorption hearts" + ChatColor.GRAY + ".");
        if (level == 1) {
            desc.add(ChatColor.GRAY + "Adds +500 Durability.");
        } else if (level == 2) {
            desc.add(ChatColor.GRAY + "Adds +1000 Durability.");
        }
        desc.add(ChatColor.GRAY + "Absorption slowly regenerates when lost.");
        desc.add(ChatColor.GRAY + "(Regen: " + ChatColor.YELLOW + "0.5HP every 3s" + ChatColor.GRAY + " — half of normal)");
        desc.add(ChatColor.GOLD + "Gold Trim Synergy:");
        desc.add(ChatColor.GRAY + "  Gold Trims grant " + ChatColor.YELLOW + "+2.5♥ base" + ChatColor.GRAY + " per piece.");
        if (level == 1) {
            desc.add(ChatColor.GRAY + "  Synergy bonus: " + ChatColor.YELLOW + "+1.5♥" + ChatColor.GRAY + " per gold trim piece.");
        } else {
            desc.add(ChatColor.GRAY + "  Synergy bonus: " + ChatColor.YELLOW + "+2.5♥" + ChatColor.GRAY + " per gold trim piece.");
        }
        desc.add(ChatColor.GRAY + "  Full Set " + ChatColor.YELLOW + "(4/4 Gold Trims)" + ChatColor.GRAY + ": "
                + ChatColor.YELLOW + "2x" + ChatColor.GRAY + " absorption regen speed.");
        desc.add(ChatColor.DARK_GRAY + "Gold armor only.");
        return desc;
    }

    public MutationType getType() {
        return MutationType.GOLDEN_AEGIS;
    }

    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }

    @Override
    public int getDurabilityBonus(int level) {
        return level * 500;
    }

    // ── Core logic ────────────────────────────────────────────────────────────

    /**
     * Calculates and applies the correct absorption cap for this player.
     * Called whenever armor contents may have changed.
     *
     * Absorption breakdown:
     *   mutationHP    — base HP from GA pieces (scaled by Emerald recovery amp)
     *   trimHP        — flat 5.0 HP per gold trim piece (resin-amplified, not recovery-amplified)
     *   synergyHP     — bonus HP per gold trim piece based on average GA level:
     *                     avgLevel 1 → +3.0 HP per trim piece  (GA I synergy — always active with GA)
     *                     avgLevel 2 → +5.0 HP per trim piece  (GA II synergy)
     *                   synergy is a flat trim-system passive and is NOT recovery-amplified.
     *                   avgLevel is clamped to [1, 2] so invalid/missing level data never
     *                   suppresses synergy below the GA I floor.
     *
     * Hard cap = (mutationPieces × 5.0) + (effectiveGoldTrims × 5.0)
     *          + (effectiveGoldTrims × synergyHPPerTrim)   ← uses the actual interpolated value
     * Applied after emerald amplification to prevent over-inflation.
     *
     * The gold trim legacy set-bonus (trim_set_gold_absorption) is suppressed entirely
     * for players with any GA piece — this method owns all absorption for those players.
     */
    public static void checkPlayer(Player player) {
        MutationManager mm = MutationManager.getInstance();
        if (mm == null) return;

        GoldenAegis self = (GoldenAegis) mm.getMutation(MutationType.GOLDEN_AEGIS);
        if (self == null) return;

        // ── Tally GA pieces ───────────────────────────────────────────────────
        double mutationHP = 0;
        int mutationPieceCount = 0;
        int mutationLevelSum = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null || piece.getType().isAir()) continue;
            if (mm.hasMutation(piece, MutationType.GOLDEN_AEGIS)) {
                mutationPieceCount++;
                // Clamp level to [1, maxLevel] so a missing/zero level never silently
                // skips synergy or assigns wrong HP — treats it as level 1 (minimum).
                int lvl = Math.max(1, Math.min(mm.getMutationLevel(piece, MutationType.GOLDEN_AEGIS), 2));
                mutationLevelSum += lvl;
                mutationHP += (lvl == 1) ? 3.0 : 5.0;
            }
        }

        // ── Tally gold trims ──────────────────────────────────────────────────
        PlayerTrimProfile profile = TrimManager.getInstance().getProfile(player);
        int goldTrimCount = profile.goldCount;

        // ── Early-out: nothing to do ──────────────────────────────────────────
        if (mutationPieceCount == 0 && goldTrimCount == 0) {
            self.targetAbsorption.remove(player.getUniqueId());
            TrimCalculator.applyAttributeModifier(player, Attribute.MAX_ABSORPTION,
                    "mutation_golden_aegis", 0, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER);
            TrimCalculator.applyAttributeModifier(player, Attribute.MAX_ABSORPTION,
                    "trim_set_gold_absorption", 0, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER);
            return;
        }

        // ── Compute average GA level (used for synergy bonus) ─────────────────
        // avgLevel interpolates between 1 and 2 for mixed-level sets, giving a
        // smooth synergy bonus for players who haven't upgraded all four pieces.
        //
        // FIX: clamp avgGaLevel to [1.0, 2.0] so it never falls below 1.0.
        // Previously, if getMutationLevel() returned 0 for any piece (e.g. due to
        // a lookup edge-case), mutationLevelSum would be under-counted, driving
        // avgGaLevel below 1.0, making t negative, and reducing synergyHPPerTrim
        // below SYNERGY_HP_LVL1 — silently breaking GA I synergy.
        double avgGaLevel = (mutationPieceCount > 0)
                ? Math.max(1.0, Math.min(2.0, (double) mutationLevelSum / mutationPieceCount))
                : 0.0;

        // ── Trim base HP (resin-replicated, flat passive) ──────────────────────
        double effectiveGoldTrims = TrimCalculator.getEffectivePieceCount(TrimMaterialType.GOLD, profile);
        double trimHP = effectiveGoldTrims * 5.0;

        // ── Synergy bonus HP (flat passive, NOT recovery-amplified) ───────────
        // Each gold trim piece earns a bonus that scales with average GA level:
        //   avgLevel == 1.0 → SYNERGY_HP_LVL1 (3.0 HP = +1.5♥) per trim piece  [GA I]
        //   avgLevel == 2.0 → SYNERGY_HP_LVL2 (5.0 HP = +2.5♥) per trim piece  [GA II]
        //   Mixed sets interpolate linearly between the two values.
        //
        // Synergy is active whenever mutationPieceCount > 0, regardless of GA level.
        // avgGaLevel is already clamped to [1, 2] above, so t is always in [0, 1].
        double synergyHPPerTrim = 0.0;
        if (mutationPieceCount > 0 && effectiveGoldTrims > 0) {
            double t = avgGaLevel - 1.0; // 0.0 at avgLevel=1 (GA I), 1.0 at avgLevel=2 (GA II)
            synergyHPPerTrim = SYNERGY_HP_LVL1 + t * (SYNERGY_HP_LVL2 - SYNERGY_HP_LVL1);
        }
        double synergyHP = effectiveGoldTrims * synergyHPPerTrim;

        // ── Recovery amplification (mutation side only) ───────────────────────
        // Emerald trims amplify only the base mutation HP, not trim passives or synergy,
        // preventing over-inflation when both Emerald and Gold trims are equipped.
        double recoveryAmp = TrimCalculator.getAmplification(self.getCategories(), profile, "recovery");
        double totalAbsorptionCap = (mutationHP * recoveryAmp) + trimHP + synergyHP;

        // ── Hard cap ──────────────────────────────────────────────────────────
        // Uses the actual interpolated synergyHPPerTrim (not always SYNERGY_HP_LVL2)
        // so the cap accurately reflects the player's current GA level mix.
        // e.g. 4× GA I + 4× gold trims: cap = 20 + 20 + 12 = 52 HP (not 60).
        double hardCap = (mutationPieceCount * 5.0) + (effectiveGoldTrims * 5.0)
                + (effectiveGoldTrims * synergyHPPerTrim);
        totalAbsorptionCap = Math.min(totalAbsorptionCap, hardCap);

        self.targetAbsorption.put(player.getUniqueId(), totalAbsorptionCap);

        // Apply MAX_ABSORPTION attribute (required in 1.21+ for absorption hearts to exist)
        TrimCalculator.applyAttributeModifier(player, Attribute.MAX_ABSORPTION,
                "mutation_golden_aegis", totalAbsorptionCap, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER);

        // Suppress legacy trim modifier to prevent double-stacking
        TrimCalculator.applyAttributeModifier(player, Attribute.MAX_ABSORPTION,
                "trim_set_gold_absorption", 0, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER);
    }

    // ── Regen task ────────────────────────────────────────────────────────────

    private void startRegenTask() {
        // Run every 60 ticks (3 seconds)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                Double cap = targetAbsorption.get(uuid);
                if (cap == null || cap <= 0) continue;

                double current = player.getAbsorptionAmount();
                if (current >= cap) continue;

                // Full Gold Trim Set bonus: absorption regenerates 2x as fast when
                // wearing a pure 4/4 gold trim set (resin replication counts toward this).
                PlayerTrimProfile profile = TrimManager.getInstance().getProfile(player);
                double regenAmount = TrimCalculator.hasPureSet(TrimMaterialType.GOLD, profile)
                        ? REGEN_AMOUNT_PER_TICK * FULL_GOLD_SET_REGEN_MULTIPLIER
                        : REGEN_AMOUNT_PER_TICK;

                double newAmount = Math.min(cap, current + regenAmount);
                player.setAbsorptionAmount(newAmount);
            }
        }, 60L, 60L);
    }

    // ── Inventory / equip watchers ────────────────────────────────────────────

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        targetAbsorption.remove(e.getPlayer().getUniqueId());
        lastRegenTime.remove(e.getPlayer().getUniqueId());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isGoldArmor(Material type) {
        return type == Material.GOLDEN_HELMET
                || type == Material.GOLDEN_CHESTPLATE
                || type == Material.GOLDEN_LEGGINGS
                || type == Material.GOLDEN_BOOTS;
    }

    /**
     * Returns true if this player has any Golden Aegis mutation piece.
     * Used by TrimEventListener to suppress the normal gold trim absorption grant
     * and defer entirely to this mutation's system.
     */
    public static boolean hasGoldenAegis(Player player) {
        MutationManager mm = MutationManager.getInstance();
        if (mm == null) return false;
        GoldenAegis self = (GoldenAegis) mm.getMutation(MutationType.GOLDEN_AEGIS);
        if (self == null) return false;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null || piece.getType().isAir()) continue;
            if (mm.hasMutation(piece, MutationType.GOLDEN_AEGIS)) {
                return true;
            }
        }
        return false;
    }
}