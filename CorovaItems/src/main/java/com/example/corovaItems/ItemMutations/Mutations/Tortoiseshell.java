package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ArmorTrims.TrimCalculator;
import com.example.corovaItems.ArmorTrims.TrimManager;
import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Tortoiseshell — Turtle Shell helmet mutation.
 *
 * Turns the turtle shell into a serious defensive helmet by granting
 * flat armor and armor toughness bonuses, making it a viable tanky
 * alternative to conventional armor helmets.
 *
 * Level I  : +3 Armor, +1 Toughness
 * Level II : +6 Armor, +2 Toughness  (cumulative, not additive to I)
 *
 * Turtle Shell helmet only.
 *
 * Attribute modifiers are applied via applyAttributes() / removeAttributes()
 * which MutationManager calls on equip and unequip. No scheduler or custom
 * event handling is needed — this mutation is purely passive.
 *
 * Iron Trim Synergy:
 *   Iron trims amplify DEFENSIVE mutations. Tortoiseshell benefits from
 *   the existing iron trim debuff-duration amplification pathway, and the
 *   diamond trim conditional amplification also applies to bonus thresholds.
 */
public class Tortoiseshell implements Mutation, Listener {

    // Modifier key names — must be unique across all mutations
    private static final String ARMOR_KEY     = "mutation_tortoiseshell_armor";
    private static final String TOUGHNESS_KEY = "mutation_tortoiseshell_toughness";

    // Stat values per level
    private static final double ARMOR_PER_LEVEL     = 1.5;
    private static final double TOUGHNESS_PER_LEVEL = 0.5;

    private final MutationManager mutationManager;
    private final JavaPlugin plugin;

    public Tortoiseshell(MutationManager mutationManager) {
        this.mutationManager = mutationManager;
        this.plugin = JavaPlugin.getProvidingPlugin(this.getClass());
    }

    // ── Mutation identity ─────────────────────────────────────────────────────

    @Override
    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.DEFENSIVE);
    }

    @Override
    public String getColor() {
        return "#5F9E5F"; // Muted turtle-shell green
    }

    @Override
    public String getName() {
        return "Tortoiseshell";
    }

    @Override
    public int getMaxLevel() {
        return 2;
    }

    @Override
    public MutationType getType() {
        return MutationType.TORTOISESHELL;
    }

    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.TORTOISESHELL_WEIGHT;
    }

    @Override
    public int getDurabilityBonus(int level) {
        if (level == 1) return 75;
        if (level == 2) return 150;
        return 0;
    }

    /**
     * Restricted to turtle shell helmets only.
     * MutationManager calls this before applying or displaying the mutation.
     */
    @Override
    public boolean isCompatible(ItemStack item) {
        return item != null && item.getType() == Material.TURTLE_HELMET;
    }

    // ── Lore ──────────────────────────────────────────────────────────────────

    @Override
    public List<String> getLore(int level) {
        List<String> lore = new ArrayList<>();
        ChatColor color = ChatColor.of(getColor());

        lore.add(color + getName() + " " + MutationUtils.toRoman(level));

        return lore;
    }

    @Override
    public List<String> getDescription(int level) {
        List<String> desc = new ArrayList<>();
        double armor     = ARMOR_PER_LEVEL * level;
        double toughness = TOUGHNESS_PER_LEVEL * level;

        desc.add(ChatColor.GRAY + "Hardens the shell into true armor.");
        String durability = (level == 1) ? "+75" : "+150";
        desc.add(ChatColor.GRAY + "+" + ChatColor.GREEN + armor + " Armor"
                + ChatColor.GRAY + ", +" + ChatColor.GREEN + toughness + " Toughness"
                + ChatColor.GRAY + ", and " + ChatColor.GREEN + durability + " Durability" + ChatColor.GRAY + ".");
        desc.add(ChatColor.GRAY + "Pair with any armor for a tank build.");
        if (level < getMaxLevel()) {
            desc.add(ChatColor.DARK_GRAY + "(Upgradeable to level " + MutationUtils.toRoman(getMaxLevel()) + ")");
        }
        desc.add(ChatColor.DARK_GRAY + "Turtle Shell helmet only.");

        return desc;
    }

    // ── Attribute application ─────────────────────────────────────────────────

    /**
     * Called by MutationManager when the item is equipped.
     * Applies flat armor and toughness modifiers to the player who owns this item.
     *
     * We use ADD_NUMBER so these stack correctly with base armor values
     * and with other modifier sources (trims, enchantments, etc.).
     */
    @Override
    public void applyAttributes(ItemStack item, ItemMeta meta, int level) {
        // Attributes are applied per-player in checkPlayer, called from equip events.
        // This override exists so MutationManager knows we handle attributes ourselves.
    }

    @Override
    public void removeAttributes(ItemStack item, ItemMeta meta) {
        // Removal is handled in checkPlayer — called on unequip events.
    }

    /**
     * Core logic: reads current armor contents, finds the highest Tortoiseshell
     * level equipped (should only ever be one turtle shell slot, but we take max
     * for safety), then applies or clears the modifiers accordingly.
     *
     * Called on equip/unequip/login via the event handlers below.
     */
    public static void checkPlayer(Player player) {
        MutationManager mm = MutationManager.getInstance();
        if (mm == null) return;

        int highestLevel = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null || piece.getType() != Material.TURTLE_HELMET) continue;
            int lvl = mm.getMutationLevel(piece, MutationType.TORTOISESHELL);
            if (lvl > highestLevel) highestLevel = lvl;
        }

        if (highestLevel == 0) {
            // No Tortoiseshell equipped — clear both modifiers
            TrimCalculator.applyAttributeModifier(player, Attribute.ARMOR,
                    ARMOR_KEY, 0, AttributeModifier.Operation.ADD_NUMBER);
            TrimCalculator.applyAttributeModifier(player, Attribute.ARMOR_TOUGHNESS,
                    TOUGHNESS_KEY, 0, AttributeModifier.Operation.ADD_NUMBER);
            return;
        }

        double armorBonus     = ARMOR_PER_LEVEL * highestLevel;
        double toughnessBonus = TOUGHNESS_PER_LEVEL * highestLevel;

        // Apply trim amplification for DEFENSIVE category if any iron trims are worn.
        // TrimCalculator.getAmplification handles returning 1.0 when no relevant trims exist.
        Tortoiseshell self = (Tortoiseshell) mm.getMutation(MutationType.TORTOISESHELL);
        if (self != null) {
            var profile = TrimManager.getInstance().getProfile(player);
            double amp = TrimCalculator.getAmplification(self.getCategories(), profile, "defensive");
            armorBonus     *= amp;
            toughnessBonus *= amp;
        }

        TrimCalculator.applyAttributeModifier(player, Attribute.ARMOR,
                ARMOR_KEY, armorBonus, AttributeModifier.Operation.ADD_NUMBER);
        TrimCalculator.applyAttributeModifier(player, Attribute.ARMOR_TOUGHNESS,
                TOUGHNESS_KEY, toughnessBonus, AttributeModifier.Operation.ADD_NUMBER);
    }

    // ── Equip / unequip watchers ──────────────────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        schedule(p);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        schedule(p);
    }

    @EventHandler
    public void onHandSwap(PlayerSwapHandItemsEvent e) {
        schedule(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> checkPlayer(e.getPlayer()), 20L);
    }

    private void schedule(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> checkPlayer(player), 1L);
    }
}