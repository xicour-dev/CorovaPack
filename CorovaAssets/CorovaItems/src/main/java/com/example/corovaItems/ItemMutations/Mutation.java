package com.example.corovaItems.ItemMutations;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface Mutation extends Listener {

    /**
     * Marker + behavior interface for mutations that proc via a deterministic
     * hit-counter build-up instead of a per-hit percentage chance.
     *
     * Counting is intentionally target-agnostic: the counter is keyed only by
     * the damaging player's UUID and is unaffected by which entity was hit.
     */
    interface BuildUpMutation {

        /**
         * Increments this player's hit counter for this mutation and checks
         * whether it has reached the required threshold. If it has, the
         * counter resets to 0 and this returns true (caller should fire
         * onProc). Otherwise increments and returns false (caller should fire
         * onNoProc, if that hook is meaningful for this mutation).
         *
         * @param canProc            If false, the counter should not reset even if the threshold is reached.
         * @param thresholdReduction Fractional reduction applied to this mutation's base
         *                           threshold before checking it — the INCREMENTAL-category
         *                           trim/synergy amplification (e.g. 0.0525 for Redstone
         *                           trim's 5.25% fewer-hits-needed bonus). Pass 0.0 for no
         *                           reduction. Implementations should apply it roughly as:
         *                           {@code effectiveThreshold = Math.max(1, Math.round(baseThreshold * (1.0 - thresholdReduction)))}
         */
        boolean incrementAndCheck(UUID damagerId, LivingEntity victim, int level, ItemStack item, LivingEntity user, boolean canProc, double thresholdReduction);

        /**
         * Convenience overload with no threshold reduction applied.
         */
        default boolean incrementAndCheck(UUID damagerId, LivingEntity victim, int level, ItemStack item, LivingEntity user, boolean canProc) {
            return incrementAndCheck(damagerId, victim, level, item, user, canProc, 0.0);
        }
    }

    enum MutationCategory {
        BURST,
        ACCUMULATION,
        DEBUFF,
        /**
         * Marks build-up (hit-counter) mutations whose required threshold can be
         * reduced by amplification — e.g. Redstone trim reduces the hits needed
         * by 5.25%. See {@link BuildUpMutation#incrementAndCheck(UUID, LivingEntity, int, ItemStack, LivingEntity, boolean, double)}.
         */
        INCREMENTAL,
        CONDITIONAL,
        SUSTAINED,
        DEFENSIVE,
        HEALTH_SCALE,
        ENCHANT_SYNERGY,
        RECOVERY
    }

    default Set<MutationCategory> getCategories() {
        return Set.of();
    }

    default java.util.List<String> getCategoryLore() {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (MutationCategory cat : getCategories()) {
            lines.add("§e[" + cat.name() + "]");
        }
        return lines;
    }

    default String getColor() {
        return "#FFFFFF";
    }

    String getName();

    int getMaxLevel();

    default boolean isCompatible(ItemStack item) {
        return true;
    }

    List<String> getLore(int var1);
    default List<String> getLore(int level, ItemStack item) {
        return getLore(level);
    }

    default List<String> getDescription(int level) {
        return List.of();
    }

    default List<String> getDescription(int level, ItemStack item) {
        return getDescription(level);
    }

    MutationType getType();

    default void applyAttributes(ItemStack item, ItemMeta meta, int level) {
    }

    default void removeAttributes(ItemStack item, ItemMeta meta) {
    }

    default int getDurabilityBonus(int level) {
        return 0;
    }

    /**
     * @deprecated Every mutation is now a {@link BuildUpMutation}; nothing rolls RNG
     * against this anymore. Use build-up amplification (the {@code progress} param
     * on {@link BuildUpMutation#incrementAndCheck}) instead of a percentage chance.
     * Kept only so old call sites still compile; safe to remove once confirmed unused.
     */
    @Deprecated
    default double getProcChance(int level) {
        return 0.0;
    }

    @Deprecated
    default double getProcChance(int level, ItemStack item) {
        return getProcChance(level);
    }

    @Deprecated
    default double getProcChance(int level, ItemStack item, LivingEntity user) {
        return getProcChance(level, item);
    }

    default double getSynergyMultiplier(int level) {
        return 0.0;
    }

    default void onProc(LivingEntity damager, LivingEntity victim, int level, EntityDamageByEntityEvent event) {
    }

    default void onNoProc(LivingEntity damager, LivingEntity victim, int level, EntityDamageByEntityEvent event) {
    }

    default void onProcSynergy(LivingEntity damager, LivingEntity victim, int level) {
    }

    default void onNoProcSynergy(LivingEntity damager, LivingEntity victim, int level) {
    }

    default double getWeight() {
        return ItemMutations.DEFAULT_WEIGHT;
    }
}