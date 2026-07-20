package com.example.corovaItems.ArmorTrims;

import com.example.corovaItems.ItemMutations.MutationManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TrimManager {

    private static TrimManager instance;
    private final Map<UUID, PlayerTrimProfile> cache         = new ConcurrentHashMap<>();
    // Snapshot of the last known armor contents so we can detect pieces that
    // were just unequipped and strip their stale set-bonus lore.
    private final Map<UUID, ItemStack[]>       armorSnapshot = new ConcurrentHashMap<>();

    private TrimManager() {}

    public static TrimManager getInstance() {
        if (instance == null) instance = new TrimManager();
        return instance;
    }

    /** Main access point — always returns something valid. */
    public PlayerTrimProfile getProfile(Player player) {
        PlayerTrimProfile profile = cache.get(player.getUniqueId());
        if (profile == null) {
            return refresh(player);
        }
        return profile;
    }

    /** Called by the listener whenever armor might have changed. */
    public void invalidate(Player player) {
        cache.remove(player.getUniqueId());
        refresh(player);
    }

    /** Force a rebuild immediately (used when you need it fresh right now). */
    public PlayerTrimProfile refresh(Player player) {
        PlayerTrimProfile oldProfile = cache.get(player.getUniqueId());

        // Grab the previous armor snapshot BEFORE building the new profile so we
        // know which pieces were equipped last time (they may no longer be in slots).
        ItemStack[] previousArmor = armorSnapshot.getOrDefault(
                player.getUniqueId(), new ItemStack[4]);

        PlayerTrimProfile fresh = PlayerTrimProfile.from(player);
        cache.put(player.getUniqueId(), fresh);

        // OPTIMIZATION: only re-apply attributes/effects if the profile (counts) changed.
        // This avoids frequent attribute removal/addition and logic re-runs (like GA absorption cap recalculation).
        if (oldProfile == null || !oldProfile.equals(fresh)) {
            TrimCalculator.applyGeneralPassives(player, fresh);
            TrimEventListener.applyPermanentSetEffects(player, fresh);
        }

        // Update lore on currently-equipped AND previously-equipped trimmed pieces.
        refreshArmorLore(player, previousArmor);

        // Save snapshot of current armor for next refresh cycle.
        ItemStack[] currentArmor = player.getInventory().getArmorContents();
        armorSnapshot.put(player.getUniqueId(),
                Arrays.stream(currentArmor)
                        .map(s -> (s != null && s.getType() != Material.AIR) ? s.clone() : null)
                        .toArray(ItemStack[]::new));

        return fresh;
    }

    /** Cleanup on logout — don't hold stale entries forever. */
    public void evict(UUID playerId) {
        cache.remove(playerId);
        armorSnapshot.remove(playerId);
    }

    /**
     * Re-runs updateLore() on:
     *   1. Every trimmed armor piece currently equipped   → shows adaptive set-bonus.
     *   2. Every trimmed armor piece that was equipped last tick but is gone now
     *      → updateLore with null viewer strips the set-bonus block entirely.
     */
    private void refreshArmorLore(Player player, ItemStack[] previousArmor) {
        MutationManager mm = MutationManager.getInstance();
        if (mm == null) return;

        ItemStack[] currentArmor = player.getInventory().getArmorContents();

        // ── 1. Update currently equipped trimmed pieces ───────────────────────
        boolean anyCurrentChanged = false;
        for (ItemStack piece : currentArmor) {
            if (!isTrimmedArmor(piece)) continue;
            mm.updateLore(piece, player);
            anyCurrentChanged = true;
        }
        if (anyCurrentChanged) {
            player.getInventory().setArmorContents(currentArmor);
        }

        // ── 2. Strip lore from pieces that just left the armor slots ──────────
        // We can't put them back into armor slots, but we can find them in the
        // main inventory by similarity and updateLore with null viewer, which
        // means no set-bonus lines will be appended — clearing the stale block.
        for (ItemStack prev : previousArmor) {
            if (!isTrimmedArmor(prev)) continue;

            // Skip if the same item is still in an armor slot (e.g. same trim material worn twice).
            boolean stillEquipped = false;
            for (ItemStack cur : currentArmor) {
                if (cur != null && cur.isSimilar(prev)) {
                    stillEquipped = true;
                    break;
                }
            }
            if (stillEquipped) continue;

            // Scan the player's full inventory for the unequipped piece and clear its lore.
            org.bukkit.inventory.PlayerInventory inv = player.getInventory();
            for (int slot = 0; slot < inv.getSize(); slot++) {
                ItemStack stack = inv.getItem(slot);
                if (stack != null && stack.isSimilar(prev)) {
                    mm.updateLore(stack, null); // null viewer → no set-bonus lines
                    inv.setItem(slot, stack);
                    break;
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isTrimmedArmor(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return false;
        if (!(item.getItemMeta() instanceof ArmorMeta am)) return false;
        return am.hasTrim();
    }
}