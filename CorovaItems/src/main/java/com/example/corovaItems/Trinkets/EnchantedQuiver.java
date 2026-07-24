package com.example.corovaItems.Trinkets;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Enchanted Quiver — a trinket that boosts the player's bow damage by 15%.
 *
 * <b>Effect:</b> Arrows fired by the player deal 15% extra damage on hit.
 * The bonus is applied multiplicatively via the EntityDamageByEntityEvent.
 *
 * <b>Durability:</b> breaks after {@value #MAX_DURABILITY} arrows hit a target.
 *
 * <b>Trinket-count penalty:</b> the same soft-limit system used by other
 * trinkets applies — carrying more than 3 trinkets total reduces all trinket
 * effects via {@link TrinketUtils#applyTrinketScaling}.
 *
 * <b>Registration:</b> add {@code new EnchantedQuiver()} to
 * {@link TrinketUtils#registerListeners} and {@link TrinketUtils#checkEntity}.
 */
public class EnchantedQuiver extends CorovaItems implements Listener {

    // ── Tuning ────────────────────────────────────────────────────────────────
    /** Fractional bonus applied to arrow damage (0.10 → +10%). */
    private static final double DAMAGE_BONUS    = 0.10;
    /** Number of arrow hits before the trinket breaks. */
    private static final int    MAX_DURABILITY  = 500;

    private static final NamespacedKey DURABILITY_KEY =
            new NamespacedKey("corova", "enchantedquiver_durability");

    // ── Constructor ───────────────────────────────────────────────────────────

    public EnchantedQuiver() {
        super(
                ChatColor.GOLD + "Enchanted Quiver",
                Material.WOLF_ARMOR,
                lore(),
                null,
                "enchantedquiver"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(
                ChatColor.GRAY + "Trinket",
                ChatColor.GOLD + "+10% bow damage!",
                ChatColor.GRAY + "Stacks with bow enchantments!"
        );
    }

    @Override
    public ItemStack toItemStack() {
        ItemStack item = super.toItemStack();
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer()
                    .set(DURABILITY_KEY, PersistentDataType.INTEGER, 0);
            List<String> lore = meta.getLore() != null
                    ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(ChatColor.DARK_GRAY + "UUID: " + UUID.randomUUID());
            lore.add(ChatColor.GRAY + "Durability: 0/" + MAX_DURABILITY);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    // ── Static helper (no-op attribute version, kept for TrinketUtils.checkEntity) ──

    /**
     * No persistent attribute modifier is needed for this trinket — the bonus
     * is applied event-by-event in {@link #onArrowHit}.  This method is still
     * called by {@link TrinketUtils#checkEntity} for uniformity and does nothing.
     */
    public static void checkTrinket(LivingEntity entity) {
        // Intentionally empty: damage boost is applied on-hit, not via an attribute.
    }

    // ── Core mechanic: boost arrow damage on hit ──────────────────────────────

    @EventHandler
    public void onArrowHit(EntityDamageByEntityEvent event) {
        // We only care about projectile hits.
        if (!(event.getDamager() instanceof Projectile projectile)) return;

        // The projectile must be an Arrow shot by a Player.
        ProjectileSource source = projectile.getShooter();
        if (!(source instanceof Player player)) return;
        if (!(projectile instanceof Arrow)) return;

        // Skip creative-mode players.
        if (player.getGameMode() == GameMode.CREATIVE) return;

        // Look for an Enchanted Quiver in the player's inventory.
        ItemStack quiver = findEnchantedQuiver(player);
        if (quiver == null) return;

        ItemMeta meta = quiver.getItemMeta();
        if (meta == null) return;

        // Apply the scaling penalty (soft trinket limit).
        double scaling = TrinketUtils.applyTrinketScaling(player, player.getInventory().getContents());

        // Boost damage by DAMAGE_BONUS * scaling (multiplicative on the raw damage).
        double boosted = event.getDamage() * (1.0 + DAMAGE_BONUS * scaling);
        event.setDamage(boosted);

        // --- Durability tracking ---
        int hits    = meta.getPersistentDataContainer()
                .getOrDefault(DURABILITY_KEY, PersistentDataType.INTEGER, 0);
        int newHits = hits + 1;

        if (newHits >= MAX_DURABILITY) {
            quiver.setAmount(0);
            player.sendMessage(ChatColor.RED + "Your Enchanted Quiver has worn out!");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
        } else {
            meta.getPersistentDataContainer()
                    .set(DURABILITY_KEY, PersistentDataType.INTEGER, newHits);
            updateLore(meta, newHits);
            quiver.setItemMeta(meta);
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ItemStack findEnchantedQuiver(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (ItemManager.getInstance().isCorovaItem(item,
                    ItemManager.getInstance().getItemById("enchantedquiver"))) {
                return item;
            }
        }
        return null;
    }

    private void updateLore(ItemMeta meta, int hits) {
        List<String> lore = meta.getLore();
        if (lore == null) return;
        for (int i = 0; i < lore.size(); i++) {
            if (ChatColor.stripColor(lore.get(i)).startsWith("Durability:")) {
                lore.set(i, ChatColor.GRAY + "Durability: " + hits + "/" + MAX_DURABILITY);
                break;
            }
        }
        meta.setLore(lore);
    }

    // ── Inventory / session watchers ──────────────────────────────────────────

    @EventHandler public void onInventoryClick(InventoryClickEvent e) { scheduleCheck(e.getWhoClicked()); }
    @EventHandler public void onInventoryClose(InventoryCloseEvent e)  { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onHandSwap(PlayerSwapHandItemsEvent e)   { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onItemHeld(PlayerItemHeldEvent e)        { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onJoin(PlayerJoinEvent e)                { scheduleCheck(e.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (e.getPlayer() != null) TrinketUtils.removeEntity(e.getPlayer());
    }

    private static void scheduleCheck(org.bukkit.entity.HumanEntity entity) {
        if (!(entity instanceof LivingEntity le)) return;
        if (le instanceof Player p && p.getGameMode() == GameMode.CREATIVE) return;
        Bukkit.getScheduler().runTaskLater(
                JavaPlugin.getProvidingPlugin(EnchantedQuiver.class),
                () -> checkTrinket(le), 1L);
    }
}