package com.example.corovaItems.ItemMutations;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Keeps the resource pack's composite glow overlay in sync with an item's
 * actual active mutations. Every mutation shares the same "_shared" glow
 * model in the pack (see CorovaAssets/source/weapons/_shared/); which
 * slot(s) are visible and what color each renders is controlled entirely by
 * this item's custom_model_data component -- specifically the flags[] and
 * colors[] lists, one slot per MutationType.
 *
 * Slot index = MutationType.ordinal(). This means MutationType's enum order
 * must never be reordered or have entries removed once items are live --
 * only append new constants at the end. Reordering silently reassigns every
 * existing item's active glow(s) to the wrong mutation after next load,
 * since the index itself carries the meaning, not the enum name.
 *
 * syncVisuals() is a full resync, not an incremental patch: it rebuilds the
 * entire flags/colors state from whatever MutationManager currently
 * considers this item's active mutations to be (the same map updateLore()
 * already computes from the PDC). This makes it self-correcting -- call it
 * every time updateLore() runs and the visual state can never drift out of
 * sync with the real mutation state, even if a caller elsewhere bypasses
 * MutationVisuals directly.
 *
 * Index 0 of the strings[] list (the item's model identity, e.g.
 * "corova:weapons/copper_scythe") is preserved as-is -- only flags/colors
 * are rebuilt here.
 */
public final class MutationVisuals {

    private MutationVisuals() {}

    /**
     * Rebuilds flags[]/colors[] from scratch to match activeMutations exactly.
     * Call from MutationManager.updateLore() (or anywhere else that has just
     * computed the authoritative current mutation map for this item).
     *
     * Also updates custom model data strings for freeze model replacement
     * and custom model data flags for lightning animated overlays.
     *
     * @param item             the item to update. Modified in place via item.setData(...).
     * @param activeMutations  this item's current mutations, as returned by
     *                         MutationManager#getMutations(ItemStack, ItemMeta).
     * @param mutationLookup   resolves a MutationType to its Mutation instance
     *                         (e.g. MutationManager::getMutation) so its getColor() can be read.
     */
    public static void syncVisuals(
            ItemStack item,
            Map<MutationType, Integer> activeMutations,
            Function<MutationType, Mutation> mutationLookup
    ) {
        if (item == null) return;

        int slotCount = MutationType.values().length;
        List<Boolean> flags = new ArrayList<>(Collections.nCopies(slotCount, false));
        List<Color> colors = new ArrayList<>(Collections.nCopies(slotCount, Color.WHITE));

        if (activeMutations != null) {
            for (MutationType type : activeMutations.keySet()) {
                Mutation m = mutationLookup.apply(type);
                if (m == null) continue;
                int slot = type.ordinal();
                flags.set(slot, true);
                colors.set(slot, parseHexColor(m.getColor()));
            }
        }

        CustomModelData existing = item.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
        List<String> strings = (existing != null) ? new ArrayList<>(existing.strings()) : new ArrayList<>();
        List<Float> floats = (existing != null) ? new ArrayList<>(existing.floats()) : new ArrayList<>();

        // -- Custom Enchant visual updates (Freeze & Lightning) --

        // 1. Freeze texture/model replacement for swords and scythes
        boolean hasFreeze = CorovaEnchantments.hasEnchant(item, CorovaEnchantments.FREEZE_ID);
        boolean isScythe = MutationUtils.isScythe(item);
        boolean isSword = MutationUtils.isSword(item);

        if (isScythe || isSword) {
            if (hasFreeze) {
                if (!strings.isEmpty()) {
                    String original = strings.get(0);
                    if (!original.endsWith("_freeze")) {
                        strings.set(0, original + "_freeze");
                    }
                } else if (isSword) {
                    // For vanilla swords that have freeze but no custom model yet
                    String name = item.getType().name().toLowerCase();
                    strings.add("corova:vanilla/" + name + "_freeze");
                }
            } else {
                if (!strings.isEmpty()) {
                    String original = strings.get(0);
                    if (original.endsWith("_freeze")) {
                        strings.set(0, original.substring(0, original.length() - "_freeze".length()));
                    }
                }
                // Cleanup vanilla sword freeze model identity if freeze was removed
                if (!strings.isEmpty() && strings.get(0).startsWith("corova:vanilla/")) {
                    strings.remove(0);
                }
            }
        }

        // 2. Lightning overlay activation flag at index 41 (slotCount)
        boolean hasLightning = CorovaEnchantments.hasEnchant(item, CorovaEnchantments.LIGHTNING_ID);
        flags.add(hasLightning);
        colors.add(Color.WHITE);

        CustomModelData.Builder builder = CustomModelData.customModelData();
        if (!strings.isEmpty()) builder.addStrings(strings);
        if (!floats.isEmpty()) builder.addFloats(floats);
        builder.addFlags(flags);
        builder.addColors(colors);

        item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, builder.build());
    }

    private static Color parseHexColor(String hex) {
        if (hex == null || hex.isEmpty()) return Color.WHITE;
        String clean = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            int rgb = Integer.parseInt(clean, 16);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            return Color.fromRGB(r, g, b);
        } catch (NumberFormatException e) {
            return Color.WHITE;
        }
    }
}
