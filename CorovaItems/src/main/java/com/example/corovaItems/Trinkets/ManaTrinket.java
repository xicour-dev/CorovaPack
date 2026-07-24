package com.example.corovaItems.Trinkets;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import com.example.corovaItems.MageSystem.ManaManager;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mana Crystal — a trinket that extends a player's mana pool and regen.
 *
 * <b>Stats per crystal (stacks additively):</b>
 * <ul>
 *   <li>+50 max mana</li>
 *   <li>+50% mana regeneration rate</li>
 * </ul>
 *
 * <b>Durability:</b> breaks after 3 000 mana points have been consumed while
 * the crystal is in the player's inventory.
 *
 * <b>Trinket-count penalty:</b> the same soft-limit system used by other
 * trinkets applies here — carrying more than 3 trinkets total reduces all
 * trinket effects via {@link TrinketUtils#applyTrinketScaling}.
 *
 * <b>Registration:</b> add {@code new ManaTrinket()} to your TrinketUtils
 * registerListeners block, or register it as a Listener separately.
 */
public class ManaTrinket extends CorovaItems implements Listener {

    // ── Tuning ────────────────────────────────────────────────────────────────
    private static final double BONUS_MAX_MANA   = 50.0;
    private static final double REGEN_BONUS_MULT = 0.5;
    private static final int    MAX_DURABILITY   = 3_000;

    private static final NamespacedKey DURABILITY_KEY =
            new NamespacedKey("corova", "manatrinket_durability");

    // ── Constructor ───────────────────────────────────────────────────────────

    public ManaTrinket() {
        super(
                ChatColor.AQUA + "Mana Crystal",
                Material.AMETHYST_SHARD,
                lore(),
                null,
                "manatrinket"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(
                ChatColor.GRAY  + "Trinket",
                ChatColor.AQUA  + "+50 max mana",
                ChatColor.AQUA  + "+50% mana regeneration",
                ChatColor.GRAY  + "Stacks with more Mana Crystals!"
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

    // ── Static helpers called by ManaManager ──────────────────────────────────

    /**
     * Recounts the player's Mana Crystals and updates their max mana.
     *
     * FIX: Previously called {@link TrinketUtils#applyTrinketScaling} directly,
     * bypassing the per-checkEntity() scaling cache.  If ManaManager's regen
     * task calls this once per second per player that is a full 41-slot
     * inventory scan with lore reads on every call — paid on top of the scan
     * already performed by TrinketUtils.checkEntity().
     *
     * The fix routes through {@link TrinketUtils#getCachedScaling}: when this
     * method is called from inside a checkEntity() pass the cache is already
     * warm and the cost is a single HashMap lookup.  When called outside
     * checkEntity() (e.g. directly from ManaManager after mana is consumed)
     * it falls back to a fresh applyTrinketScaling() call, same as before —
     * so external callers are no worse off than they were originally.
     */
    public static void checkTrinket(Player player) {
        if (ManaManager.getInstance() == null) return;
        ItemStack[] contents = player.getInventory().getContents();
        int    count   = countTrinkets(player);
        double scaling = TrinketUtils.getCachedScaling(player, contents);
        double bonus   = count * BONUS_MAX_MANA * scaling;
        ManaManager.getInstance().setMaxMana(player, ManaManager.BASE_MAX_MANA + bonus);
    }

    /**
     * Returns the effective mana regen per second for this player.
     * Called by {@link ManaManager}'s regen task every second.
     *
     * FIX: Same as checkTrinket — routes through the cache so the regen task
     * does not trigger a redundant full inventory scan when it runs concurrently
     * with (or just after) the TrinketUtils background task.
     */
    public static double getEffectiveRegen(Player player, double baseRegen) {
        ItemStack[] contents = player.getInventory().getContents();
        int    count   = countTrinkets(player);
        double scaling = TrinketUtils.getCachedScaling(player, contents);
        return baseRegen * (1.0 + count * REGEN_BONUS_MULT * scaling);
    }

    /**
     * Called by {@link ManaManager#tryConsumeMana} after a successful spend.
     * Ticks durability on every Mana Crystal in the player's inventory.
     */
    public static void notifyManaConsumed(Player player, double amount) {
        if (player == null || player.getGameMode() == GameMode.CREATIVE) return;
        int spend = (int) Math.ceil(amount);
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (!ItemManager.getInstance().isCorovaItem(item,
                    ItemManager.getInstance().getItemById("manatrinket"))) continue;

            ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;

            int current = meta.getPersistentDataContainer()
                    .getOrDefault(DURABILITY_KEY, PersistentDataType.INTEGER, 0);
            int newDur  = current + spend;

            if (newDur >= MAX_DURABILITY) {
                item.setAmount(0);
                player.sendMessage(ChatColor.RED + "Your Mana Crystal has shattered!");
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                Bukkit.getScheduler().runTaskLater(
                        JavaPlugin.getProvidingPlugin(ManaTrinket.class),
                        () -> checkTrinket(player), 1L);
            } else {
                meta.getPersistentDataContainer()
                        .set(DURABILITY_KEY, PersistentDataType.INTEGER, newDur);
                updateLore(meta, newDur);
                item.setItemMeta(meta);
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static int countTrinkets(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (ItemManager.getInstance().isCorovaItem(item,
                    ItemManager.getInstance().getItemById("manatrinket"))) count++;
        }
        return count;
    }

    private static void updateLore(ItemMeta meta, int durability) {
        List<String> lore = meta.getLore();
        if (lore == null) return;
        for (int i = 0; i < lore.size(); i++) {
            if (ChatColor.stripColor(lore.get(i)).startsWith("Durability:")) {
                lore.set(i, ChatColor.GRAY + "Durability: " + durability + "/" + MAX_DURABILITY);
                break;
            }
        }
        meta.setLore(lore);
    }

    // ── Events ────────────────────────────────────────────────────────────────

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
        if (!(entity instanceof Player p)) return;
        Bukkit.getScheduler().runTaskLater(
                JavaPlugin.getProvidingPlugin(ManaTrinket.class),
                () -> checkTrinket(p), 1L);
    }
}