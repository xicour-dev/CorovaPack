package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.ItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class MusicBook extends EnchantmentBook implements Listener {

    public static final int MAX_LEVEL = 8;

    private static final double KILL_SONG_RADIUS = 26.0;

    // ── Level scaling (used for lore display only — Music deals damage via AOE blast) ──
    // I=3× II=4× III=5× IV=6× V=7× VI=8× VII=9× VIII=10×
    private static final double[] MULTIPLIER_BY_LEVEL = { 0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0 };

    public static double multiplierForLevel(int level) {
        if (level >= 1 && level < MULTIPLIER_BY_LEVEL.length) return MULTIPLIER_BY_LEVEL[level];
        return MULTIPLIER_BY_LEVEL[MULTIPLIER_BY_LEVEL.length - 1];
    }

    private final JavaPlugin plugin;

    // Static maps shared across all level instances
    private static final Map<UUID, Long>    cooldowns       = new HashMap<>();
    private static final Set<UUID>          chargingPlayers = new HashSet<>();
    private static final Map<UUID, BossBar> bossBars        = new HashMap<>();
    private static final Map<UUID, Integer> musicBurstActive = new HashMap<>();

    private static final long   COOLDOWN_MS = 3_000;
    private static final String GUI_TITLE   = EnchantmentBook.applyEnchantmentGradient(CorovaEnchantments.MUSIC_ID, "Music") + ChatColor.RED + " Enchant Song Customizer";

    // Shared across all instances — initialised once on first construction
    private static NamespacedKey selectedSongKey;

    private static final Map<Material, Sound> MUSIC_DISC_SOUNDS = new HashMap<>();
    static {
        MUSIC_DISC_SOUNDS.put(Material.MUSIC_DISC_13,      Sound.MUSIC_DISC_13);
        MUSIC_DISC_SOUNDS.put(Material.MUSIC_DISC_CAT,     Sound.MUSIC_DISC_CAT);
        MUSIC_DISC_SOUNDS.put(Material.MUSIC_DISC_BLOCKS,  Sound.MUSIC_DISC_BLOCKS);
        MUSIC_DISC_SOUNDS.put(Material.MUSIC_DISC_CHIRP,   Sound.MUSIC_DISC_CHIRP);
        MUSIC_DISC_SOUNDS.put(Material.MUSIC_DISC_FAR,     Sound.MUSIC_DISC_FAR);
        MUSIC_DISC_SOUNDS.put(Material.MUSIC_DISC_MALL,    Sound.MUSIC_DISC_MALL);
        MUSIC_DISC_SOUNDS.put(Material.MUSIC_DISC_MELLOHI, Sound.MUSIC_DISC_MELLOHI);
        MUSIC_DISC_SOUNDS.put(Material.MUSIC_DISC_STAL,    Sound.MUSIC_DISC_STAL);
    }

    public MusicBook() {
        this(1);
    }

    public MusicBook(int level) {
        super(
                "Book of Music",
                CorovaEnchantments.MUSIC_ID,
                level,
                "book_music",
                allowedMaterialsStatic()
        );
        this.plugin = JavaPlugin.getProvidingPlugin(this.getClass());
        if (selectedSongKey == null) {
            selectedSongKey = new NamespacedKey(plugin, "corova_music_selected_song");
        }
        ItemManager.getInstance().registerItem(this);
    }

    private static Set<Material> allowedMaterialsStatic() {
        Set<Material> s = new HashSet<>();
        s.add(Material.WOODEN_SWORD);
        s.add(Material.STONE_SWORD);
        s.add(Material.IRON_SWORD);
        s.add(Material.GOLDEN_SWORD);
        s.add(Material.DIAMOND_SWORD);
        s.add(Material.NETHERITE_SWORD);
        s.add(Material.WOODEN_AXE);
        s.add(Material.STONE_AXE);
        s.add(Material.IRON_AXE);
        s.add(Material.GOLDEN_AXE);
        s.add(Material.DIAMOND_AXE);
        s.add(Material.NETHERITE_AXE);
        s.add(Material.TRIDENT);
        return s;
    }

    // ── Damage multiplier for blast hits only ──
    // The multiplier is ONLY applied when musicBurstActive contains the victim,
    // which is set immediately before victim.damage() in activateDirectStatic and
    // removed right after. Normal melee hits are never multiplied.

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamage(EntityDamageByEntityEvent event) {
        // Single-instance guard: level is read from the weapon at runtime.
        if (getLevel() != 1) return;

        Integer burstLevel = musicBurstActive.get(event.getEntity().getUniqueId());
        if (burstLevel == null) return;

        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        // Bypass combat cooldown for the enchantment portion
        if (event.isCancelled()) {
            event.setCancelled(false);
            victim.setNoDamageTicks(0);
        }

        double multiplier = multiplierForLevel(burstLevel);
        event.setDamage(event.getDamage() * multiplier);

        Location loc = victim.getLocation().add(0, 1.5, 0);
        victim.getWorld().spawnParticle(Particle.NOTE, loc, 5, 0.5, 0.5, 0.5);
    }

    // ── Kill player → play selected song within 26 block radius ──────────────

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (getLevel() != 1) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        ItemStack held = killer.getInventory().getItemInMainHand();
        if (!CorovaEnchantments.hasEnchant(held, CorovaEnchantments.MUSIC_ID)) return;

        Sound song = getSelectedSong(held);
        if (song == null) return;

        Location killerLoc = killer.getLocation();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getWorld().equals(killerLoc.getWorld())
                    && online.getLocation().distance(killerLoc) <= KILL_SONG_RADIUS) {
                online.playSound(online.getLocation(), song, 1f, 1f);
            }
        }
    }

    // ── Shift + right-click air → open song selector ─────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (getLevel() != 1) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!CorovaEnchantments.hasEnchant(held, CorovaEnchantments.MUSIC_ID)) return;

        if (player.isSneaking() && event.getAction().isRightClick()) {
            event.setCancelled(true);
            openSongGUI(player);
        }
    }

    // ── GUI: pick a disc to set selected song ─────────────────────────────────

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (getLevel() != 1) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.getType().isRecord()) return;

        ItemStack held = player.getInventory().getItemInMainHand();
        if (!CorovaEnchantments.hasEnchant(held, CorovaEnchantments.MUSIC_ID)) return;

        setSelectedSong(held, clicked.getType());
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
    }

    // ── Shift to charge, release to blast notes ───────────────────────────────

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        if (getLevel() != 1) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!CorovaEnchantments.hasEnchant(held, CorovaEnchantments.MUSIC_ID)) return;
        if (player.isSneaking()) return; // guard: only on sneak-start

        int level = CorovaEnchantments.getEnchantLevel(held, CorovaEnchantments.MUSIC_ID);
        UUID playerId = player.getUniqueId();
        if (chargingPlayers.contains(playerId)) return;

        long lastUsed = cooldowns.getOrDefault(playerId, 0L);
        if (System.currentTimeMillis() - lastUsed < COOLDOWN_MS) {
            long remaining = (COOLDOWN_MS - (System.currentTimeMillis() - lastUsed)) / 1000;
            Component enchantName = EnchantmentBook.applyEnchantmentGradientComponent(getEnchantId(), CorovaEnchantments.DISPLAY_NAME.getOrDefault(getEnchantId(), "Music"));
            player.sendActionBar(enchantName.append(Component.text(
                    " is on cooldown for " + remaining + "s", NamedTextColor.RED)));
            return;
        }

        chargingPlayers.add(playerId);
        BossBar bossBar = Bukkit.createBossBar("Music Radius: 0", BarColor.BLUE, BarStyle.SOLID);
        bossBar.addPlayer(player);
        bossBars.put(playerId, bossBar);

        new BukkitRunnable() {
            int charge = 0;
            @Override
            public void run() {
                if (!player.isOnline() || !player.isSneaking() || !chargingPlayers.contains(playerId)) {
                    activate(player, charge, level);
                    cancel();
                    return;
                }
                if (charge >= 10) {
                    activate(player, charge, level);
                    cancel();
                    return;
                }
                charge++;
                bossBar.setProgress(charge / 10.0);
                bossBar.setTitle("Music Radius: " + charge);
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // ── Activate music blast (hits players AND hostile mobs) ──────────────────

    public static void triggerEffect(LivingEntity damager, LivingEntity target, int level) {
        // Synergy activation: 5 block radius blast centered on target
        activateDirectStatic(damager, 5, level, target != null ? target.getLocation() : damager.getLocation());
    }

    private void activate(Player player, int radius, int level) {
        UUID playerId = player.getUniqueId();
        chargingPlayers.remove(playerId);
        cooldowns.put(playerId, System.currentTimeMillis());
        BossBar bar = bossBars.remove(playerId);
        if (bar != null) bar.removeAll();

        activateDirect(player, radius, level);
    }

    private void activateDirect(LivingEntity caster, int radius, int level) {
        activateDirectStatic(caster, radius, level);
    }

    private static void activateDirectStatic(LivingEntity caster, int radius, int level) {
        activateDirectStatic(caster, radius, level, caster.getLocation());
    }

    private static void activateDirectStatic(LivingEntity caster, int radius, int level, Location center) {
        if (radius <= 1) return;

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(MusicBook.class);
        World world = center.getWorld();
        for (Location loc : getCircleStatic(center, radius, 30)) {
            world.spawnParticle(Particle.NOTE, loc, 1);
        }

        // Damage all nearby living entities (players + mobs) except the caster immediately.
        UUID casterUUID = caster.getUniqueId();
        com.example.corovaItems.WeaponProperties.CorovaCombat.abilityBypass.add(casterUUID);

        // Base damage for the blast is the weapon's attribute damage.
        // Multipliers (from multiplierForLevel) are applied in onDamage to ensure
        // enchants like Sharpness are included.
        double blastDamage = EnchantmentBook.getWeaponDamage(caster);

        for (LivingEntity victim : world.getNearbyLivingEntities(center, radius)) {
            if (victim.getUniqueId().equals(caster.getUniqueId())) continue;
            if (!victim.isValid() || victim.isDead()) continue;

            musicBurstActive.put(victim.getUniqueId(), level);
            int savedMax = victim.getMaximumNoDamageTicks();
            victim.setMaximumNoDamageTicks(0);
            victim.setNoDamageTicks(0);
            victim.damage(blastDamage, caster);
            victim.setMaximumNoDamageTicks(savedMax);
            musicBurstActive.remove(victim.getUniqueId());

            final Location finalLoc = victim.getLocation();
            new BukkitRunnable() {
                int i = 0;
                @Override
                public void run() {
                    if (i >= 15) { cancel(); return; }
                    Location playLoc = (victim.isValid() && !victim.isDead()) ? victim.getLocation() : finalLoc;
                    playLoc.getWorld().playSound(
                            playLoc, Sound.BLOCK_NOTE_BLOCK_HARP, 1f, 0.5f + (i * 0.1f));
                    playLoc.getWorld().spawnParticle(
                            Particle.NOTE, playLoc.clone().add(0, 1.5, 0), 2, 0.5, 0.5, 0.5);
                    i++;
                }
            }.runTaskTimer(plugin, 0L, 2L);
        }
        com.example.corovaItems.WeaponProperties.CorovaCombat.abilityBypass.remove(casterUUID);
    }

    // ── Song selector GUI ─────────────────────────────────────────────────────

    private void openSongGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, Component.text(GUI_TITLE));
        for (Material disc : MUSIC_DISC_SOUNDS.keySet()) {
            inv.addItem(new ItemStack(disc));
        }
        player.openInventory(inv);
    }

    // ── Persistent song storage on the enchanted item itself ──────────────────

    private void setSelectedSong(ItemStack item, Material song) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(
                selectedSongKey, PersistentDataType.STRING, song.name());
        List<String> lore = meta.getLore();
        if (lore != null) {
            boolean replaced = false;
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i) != null && lore.get(i).contains("Selected Song:")) {
                    lore.set(i, ChatColor.DARK_GRAY + "Selected Song: "
                            + song.name().replace("MUSIC_DISC_", "").toLowerCase());
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                lore.add(ChatColor.DARK_GRAY + "Selected Song: "
                        + song.name().replace("MUSIC_DISC_", "").toLowerCase());
            }
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
    }

    private Sound getSelectedSong(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String name = item.getItemMeta().getPersistentDataContainer()
                .get(selectedSongKey, PersistentDataType.STRING);
        if (name == null) return null;
        try {
            return MUSIC_DISC_SOUNDS.get(Material.valueOf(name));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── Circle helper ─────────────────────────────────────────────────────────

    private List<Location> getCircle(Location center, double radius, int points) {
        return getCircleStatic(center, radius, points);
    }

    private static List<Location> getCircleStatic(Location center, double radius, int points) {
        World world = center.getWorld();
        double increment = (2 * Math.PI) / points;
        List<Location> locs = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            double angle = i * increment;
            locs.add(new Location(world,
                    center.getX() + radius * Math.cos(angle),
                    center.getY() + 1,
                    center.getZ() + radius * Math.sin(angle)));
        }
        return locs;
    }

    // ── Cleanup on disconnect ─────────────────────────────────────────────────

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (getLevel() != 1) return;
        UUID playerId = event.getPlayer().getUniqueId();
        chargingPlayers.remove(playerId);
        cooldowns.remove(playerId);
        BossBar bar = bossBars.remove(playerId);
        if (bar != null) bar.removeAll();
        musicBurstActive.remove(playerId);
    }
}