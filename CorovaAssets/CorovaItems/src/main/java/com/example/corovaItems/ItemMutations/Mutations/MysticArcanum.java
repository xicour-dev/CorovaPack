package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import com.example.corovaItems.MageSystem.ManaManager;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Set;

/**
 * Mystic Arcanum — armor mutation.
 *
 * Every piece of armor you're wearing with Mystic Arcanum will apply a mana buff.
 * Mystic Arcanum I gives +12 mana, and Mystic Arcanum II gives +25 mana.
 *
 * This is an armor-only mutation; it never appears on weapons.
 * It has two levels — Level I and Level II.
 */
public class MysticArcanum implements Mutation {

    private final MutationManager mutationManager;

    public MysticArcanum(MutationManager mutationManager) {
        this.mutationManager = mutationManager;
    }

    @Override
    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.ENCHANT_SYNERGY);
    }

    @Override
    public String getColor() {
        return "#9932CC";
    }

    @Override
    public String getName() { return "Mystic Arcanum"; }

    @Override
    public int getMaxLevel() { return 2; }

    @Override
    public MutationType getType() { return MutationType.MYSTIC_ARCANUM; }

    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }

    @Override
    public List<String> getLore(int level) {
        List<String> lore = new java.util.ArrayList<>();
        ChatColor color = ChatColor.of(getColor());
        lore.add(color + getName() + " " + MutationUtils.toRoman(level));
        return lore;
    }

    @Override
    public List<String> getDescription(int level) {
        int manaBonus = level == 2 ? 10 : 5;
        List<String> desc = new java.util.ArrayList<>();
        desc.add(ChatColor.GRAY + "Each piece: +" + manaBonus + " max mana.");
        return desc;
    }

    /** Passive — no combat proc. */

    @Override
    public void onProc(LivingEntity damager, LivingEntity victim, int level,
                       EntityDamageByEntityEvent event) {}

    // ── Full-set detection ────────────────────────────────────────────────────

    /**
     * Checks the player's armor and pushes the correct mana bonus to ManaManager.
     * Sums the bonus from each armor piece carrying the mutation.
     * Call whenever armor contents may have changed.
     */
    public static void checkPlayer(Player player) {
        ManaManager mana = ManaManager.getInstance();
        if (mana == null) return;

        MutationManager mm = MutationManager.getInstance();
        if (mm == null) return;

        double totalBonus = 0.0;
        ItemStack[] armor = {
                player.getInventory().getHelmet(),
                player.getInventory().getChestplate(),
                player.getInventory().getLeggings(),
                player.getInventory().getBoots()
        };

        for (ItemStack piece : armor) {
            if (piece == null || piece.getType().isAir()) continue;
            if (mm.hasMutation(piece, MutationType.MYSTIC_ARCANUM)) {
                int level = mm.getMutationLevel(piece, MutationType.MYSTIC_ARCANUM);
                totalBonus += (level == 2 ? 10.0 : 5.0);
            }
        }

        Mutation self = mm.getMutation(MutationType.MYSTIC_ARCANUM);
        if (self != null && totalBonus > 0) {
            com.example.corovaItems.ArmorTrims.PlayerTrimProfile profile = com.example.corovaItems.ArmorTrims.TrimManager.getInstance().getProfile(player);
            double amp = com.example.corovaItems.ArmorTrims.TrimCalculator.getAmplification(self.getCategories(), profile, "partial_synergy");
            totalBonus *= (1.0 + amp);
        }

        mana.setArmorManaBonus(player, totalBonus);
    }

    // ── Inventory / equip watchers ────────────────────────────────────────────

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
    public void onItemHeld(PlayerItemHeldEvent e) {
        schedule(e.getPlayer());
    }

    @EventHandler
    public void onHandSwap(PlayerSwapHandItemsEvent e) {
        schedule(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(
                JavaPlugin.getProvidingPlugin(MysticArcanum.class),
                () -> checkPlayer(e.getPlayer()), 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        ManaManager mana = ManaManager.getInstance();
        if (mana != null) mana.setArmorManaBonus(e.getPlayer(), 0.0);
    }

    private static void schedule(Player player) {
        Bukkit.getScheduler().runTaskLater(
                JavaPlugin.getProvidingPlugin(MysticArcanum.class),
                () -> checkPlayer(player), 1L);
    }
}