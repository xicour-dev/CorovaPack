package com.example.corovaItems.MageSystem;

import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.Trinkets.ManaTrinket;
import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import io.papermc.paper.event.entity.EntityEquipmentChangedEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Central mana manager.
 *
 * Call {@link #init(JavaPlugin)} once from your plugin's onEnable().
 *
 * Mana Conservation is now a MUTATION (not an enchantment) and is read from
 * the player's held item via {@link MutationManager} inside
 * {@link #tryConsumeMana}.
 */
public class ManaManager implements Listener {

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static ManaManager instance;
    public static ManaManager getInstance() { return instance; }

    public static void init(JavaPlugin plugin) {
        if (instance != null) return;
        instance = new ManaManager();
        Bukkit.getPluginManager().registerEvents(instance, plugin);
        instance.startRegenTask(plugin);
    }

    // ── Tuning constants ──────────────────────────────────────────────────────
    public static final double BASE_MAX_MANA         = 100.0;
    public static final double BASE_REGEN_PER_SECOND = 8.0;
    private static final long  REGEN_DELAY_MS        = 2_000L;

    // ── Spell costs ───────────────────────────────────────────────────────────
    public static final double COST_WATER_BALL      = 40.0;
    public static final double COST_ENDER_MISSILE   = 30.0;
    public static final double COST_BURST_ROD_TICK  = 0.8;
    public static final double COST_ARROW_PORTAL    = 20.0;
    public static final double COST_ARROW_FIRE      = 15.0;
    public static final double COST_SOUL_EXTRACTION = 30.0;
    public static final double COST_BOW_SPELL       = 100.0;
    public static final double COST_DIVINUM_TRABEM  = 50.0;
    public static final double COST_COSMIC_SPIRAL   = 50.0;

    // ── Per-player state ──────────────────────────────────────────────────────
    private final Map<UUID, Double>  currentMana        = new HashMap<>();
    /** Trinket-derived ceiling: BASE_MAX_MANA + ManaTrinket bonus. */
    private final Map<UUID, Double>  maxMana            = new HashMap<>();
    /** Extra max mana from a full set of Mystic Arcanum armor. */
    private final Map<UUID, Double>  armorManaBonus     = new HashMap<>();
    /** Extra max mana from Amethyst armor pieces (+25 each). */
    private final Map<UUID, Double>  amethystArmorBonus = new HashMap<>();
    /**
     * Extra max mana from armor trims (Amethyst trim +12/piece, Lapis trim +5/piece).
     * Set via {@link #setTrimManaBonus} — NOT additive per call.
     */
    private final Map<UUID, Double>  trimManaBonus      = new HashMap<>();
    private final Map<UUID, Long>    regenPauseUntil    = new HashMap<>();
    private final Map<UUID, BossBar> manaBars           = new HashMap<>();

    private ManaManager() {}

    // ── Public API ────────────────────────────────────────────────────────────

    public double getMana(Player player) {
        return currentMana.getOrDefault(player.getUniqueId(), getMaxMana(player));
    }

    /**
     * Effective max mana = trinket ceiling
     *                    + Mystic Arcanum armor bonus
     *                    + Amethyst armor item bonus
     *                    + armor trim bonus (Amethyst/Lapis trims)
     */
    public double getMaxMana(Player player) {
        UUID   id       = player.getUniqueId();
        double base     = maxMana.getOrDefault(id, BASE_MAX_MANA);
        double armor    = armorManaBonus.getOrDefault(id, 0.0);
        double amethyst = amethystArmorBonus.getOrDefault(id, 0.0);
        double trim     = trimManaBonus.getOrDefault(id, 0.0);
        return base + armor + amethyst + trim;
    }

    /**
     * Attempts to spend mana equal to {@code baseCost}, automatically reduced
     * by the Mana Conservation MUTATION on the player's held weapon:
     *   level I  → -7.5%
     *   level II → -15%
     *
     * Returns {@code true} and deducts on success; {@code false} + chat msg on failure.
     */
    public boolean tryConsumeMana(Player player, double baseCost) {
        double cost = applyConservation(player, baseCost);
        UUID   id   = player.getUniqueId();
        double cur  = currentMana.getOrDefault(id, getMaxMana(player));

        if (cur < cost) {
            player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "Not enough mana! "
                    + ChatColor.RESET + ChatColor.AQUA
                    + "(" + (int) cur + " / " + (int) getMaxMana(player) + ")");
            return false;
        }

        currentMana.put(id, cur - cost);
        regenPauseUntil.put(id, System.currentTimeMillis() + REGEN_DELAY_MS);
        ManaTrinket.notifyManaConsumed(player, cost);
        refreshBar(player);
        return true;
    }

    /**
     * Directly restores mana (Soul Siphon, Soul Extraction, etc.).
     * Will not exceed the player's current max mana.
     */
    public void restoreMana(Player player, double amount) {
        UUID   id  = player.getUniqueId();
        double cur = currentMana.getOrDefault(id, getMaxMana(player));
        currentMana.put(id, Math.min(getMaxMana(player), cur + amount));
        refreshBar(player);
    }

    /**
     * Called by {@link ManaTrinket#checkTrinket} to update the trinket-based
     * max mana ceiling.
     */
    public void setMaxMana(Player player, double newMax) {
        maxMana.put(player.getUniqueId(), newMax);
        clampCurrentMana(player);
        refreshBar(player);
    }

    /**
     * Called by {@link com.example.corovaItems.ItemMutations.Mutations.MysticArcanum}
     * when the player equips or removes a full set of Mystic Arcanum armor.
     * Pass {@code 50.0} for a full set, {@code 0.0} otherwise.
     */
    public void setArmorManaBonus(Player player, double bonus) {
        armorManaBonus.put(player.getUniqueId(), bonus);
        clampCurrentMana(player);
        refreshBar(player);
    }

    /**
     * Called by {@link com.example.corovaItems.ArmorTrims.TrimCalculator#applyGeneralPassives}
     * to set the total mana bonus derived from armor trims.
     *
     * This is a SET operation — pass the full computed value each time.
     * For example: (amethystTrimCount * 12) + (lapisTrimCount * 5).
     *
     * Do NOT call this additively on every armor change; always pass the
     * recalculated total so the bonus stays accurate.
     */
    public void setTrimManaBonus(Player player, double bonus) {
        trimManaBonus.put(player.getUniqueId(), bonus);
        clampCurrentMana(player);
        refreshBar(player);
    }

    /**
     * FIX 3: If the bar is currently invisible when refreshBar is called
     * (e.g. stuck from a missed visibility update), force a visibility
     * re-check so it recovers automatically on the next mana event.
     */
    public void refreshBar(Player player) {
        BossBar bar = getOrCreateBar(player);
        double  cur = getMana(player);
        double  max = getMaxMana(player);
        double  pct = max > 0 ? Math.max(0.0, Math.min(1.0, cur / max)) : 0.0;
        bar.setProgress(pct);
        bar.setTitle(ChatColor.AQUA + "✦ Mana   " + (int) cur + " / " + (int) max);

        if (!bar.isVisible()) {
            updateBarVisibility(player);
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static double applyConservation(Player player, double cost) {
        MutationManager mm = MutationManager.getInstance();
        if (mm == null) return cost;

        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null) return cost;

        int level = mm.getMutationLevel(held, MutationType.MANA_CONSERVATION);
        if (level <= 0) return cost;

        double reduction = (level >= 2) ? 0.15 : 0.075;
        return cost * (1.0 - reduction);
    }

    private void clampCurrentMana(Player player) {
        UUID   id  = player.getUniqueId();
        double max = getMaxMana(player);
        double cur = currentMana.getOrDefault(id, max);
        if (cur > max) currentMana.put(id, max);
    }

    private void updateAmethystArmorBonus(Player player) {
        double bonus = 0.0;
        for (ItemStack item : player.getInventory().getArmorContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            ItemMeta meta = item.getItemMeta();
            if (!meta.hasDisplayName()) continue;

            String name = ChatColor.stripColor(meta.getDisplayName());
            if (name.equals("Amethyst Helmet") || name.equals("Amethyst Chestplate") ||
                    name.equals("Amethyst Leggings") || name.equals("Amethyst Boots")) {
                bonus += 25.0;
            }
        }

        UUID   id       = player.getUniqueId();
        double oldBonus = amethystArmorBonus.getOrDefault(id, 0.0);
        if (bonus != oldBonus) {
            amethystArmorBonus.put(id, bonus);
            clampCurrentMana(player);
            refreshBar(player);
        }
    }

    private void startRegenTask(JavaPlugin plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID id = player.getUniqueId();
                    if (regenPauseUntil.getOrDefault(id, 0L) > now) continue;
                    double max = getMaxMana(player);
                    double cur = currentMana.getOrDefault(id, max);
                    if (cur >= max) continue;
                    double regen = ManaTrinket.getEffectiveRegen(player, BASE_REGEN_PER_SECOND);
                    currentMana.put(id, Math.min(max, cur + regen));
                    refreshBar(player);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private static boolean isManaItem(ItemStack item) {
        if (item == null) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String plain = ChatColor.stripColor(meta.getDisplayName());
            if (plain.contains("Wand") || plain.contains("Rod")) return true;
        }
        if (item.getType() == Material.BOW || item.getType() == Material.CROSSBOW) {
            return com.example.corovaItems.Enchantments.CorovaEnchantments
                    .hasAnyCustomEnchant(item);
        }
        return false;
    }

    private BossBar getOrCreateBar(Player player) {
        return manaBars.computeIfAbsent(player.getUniqueId(), k -> {
            BossBar bar = Bukkit.createBossBar(
                    ChatColor.AQUA + "Mana", BarColor.BLUE, BarStyle.SOLID);
            bar.addPlayer(player);
            return bar;
        });
    }

    private void updateBarVisibility(Player player) {
        BossBar   bar  = getOrCreateBar(player);
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean   show = isManaItem(held);
        bar.setVisible(show);
        if (show) refreshBar(player);
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p  = e.getPlayer();
        UUID   id = p.getUniqueId();
        currentMana.putIfAbsent(id, BASE_MAX_MANA);
        maxMana.putIfAbsent(id, BASE_MAX_MANA);
        Bukkit.getScheduler().runTaskLater(
                JavaPlugin.getProvidingPlugin(ManaManager.class),
                () -> {
                    updateAmethystArmorBonus(p);
                    ManaTrinket.checkTrinket(p);
                    updateBarVisibility(p);
                }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID    id  = e.getPlayer().getUniqueId();
        BossBar bar = manaBars.remove(id);
        if (bar != null) bar.removeAll();
        currentMana.remove(id);
        maxMana.remove(id);
        armorManaBonus.remove(id);
        amethystArmorBonus.remove(id);
        trimManaBonus.remove(id);
        regenPauseUntil.remove(id);
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent e) {
        Bukkit.getScheduler().runTaskLater(
                JavaPlugin.getProvidingPlugin(ManaManager.class),
                () -> updateBarVisibility(e.getPlayer()), 2L);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Bukkit.getScheduler().runTaskLater(
                JavaPlugin.getProvidingPlugin(ManaManager.class),
                () -> {
                    updateAmethystArmorBonus(p);
                    ManaTrinket.checkTrinket(p);
                    updateBarVisibility(p);
                }, 1L);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Bukkit.getScheduler().runTaskLater(
                JavaPlugin.getProvidingPlugin(ManaManager.class),
                () -> {
                    updateAmethystArmorBonus(p);
                    ManaTrinket.checkTrinket(p);
                    updateBarVisibility(p);
                }, 1L);
    }

    @EventHandler
    public void onEquipmentChange(EntityEquipmentChangedEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Bukkit.getScheduler().runTaskLater(
                JavaPlugin.getProvidingPlugin(ManaManager.class),
                () -> updateAmethystArmorBonus(p), 1L);
    }
}