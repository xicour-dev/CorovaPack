package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.enchantments.Enchantment;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PianoKey extends CorovaItems implements Listener {

    private final JavaPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Set<UUID> chargingPlayers = new HashSet<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final String inventoryTitle = ChatColor.RED + "Piano Key Song Customizer";
    private static final long COOLDOWN_MS = 3000;
    private final NamespacedKey selectedSongKey;

    private static final Map<Material, Sound> MUSIC_DISC_SOUNDS = new HashMap<>();
    static {
        MUSIC_DISC_SOUNDS.put(Material.MUSIC_DISC_13, Sound.MUSIC_DISC_13);
        MUSIC_DISC_SOUNDS.put(Material.MUSIC_DISC_CAT, Sound.MUSIC_DISC_CAT);
        MUSIC_DISC_SOUNDS.put(Material.MUSIC_DISC_BLOCKS, Sound.MUSIC_DISC_BLOCKS);
        MUSIC_DISC_SOUNDS.put(Material.MUSIC_DISC_CHIRP, Sound.MUSIC_DISC_CHIRP);
        MUSIC_DISC_SOUNDS.put(Material.MUSIC_DISC_FAR, Sound.MUSIC_DISC_FAR);
        MUSIC_DISC_SOUNDS.put(Material.MUSIC_DISC_MALL, Sound.MUSIC_DISC_MALL);
        MUSIC_DISC_SOUNDS.put(Material.MUSIC_DISC_MELLOHI, Sound.MUSIC_DISC_MELLOHI);
        MUSIC_DISC_SOUNDS.put(Material.MUSIC_DISC_STAL, Sound.MUSIC_DISC_STAL);
    }

    public PianoKey() {
        super(
                ChatColor.AQUA + "Piano Key",
                Material.DIAMOND_SWORD,
                lore(),
                enchantments(),
                "pianokey"
        );
        this.plugin = JavaPlugin.getProvidingPlugin(PianoKey.class);
        this.selectedSongKey = new NamespacedKey(plugin, "selected_song");
        ItemManager.getInstance().registerItem(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker && isThisItem(attacker.getInventory().getItemInMainHand())) {
            Location loc = event.getEntity().getLocation().add(0, 1.5, 0);
            attacker.getWorld().spawnParticle(Particle.NOTE, loc, 5, 0.5, 0.5, 0.5);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null && isThisItem(killer.getInventory().getItemInMainHand())) {
            Sound song = getSelectedSong(killer.getInventory().getItemInMainHand());
            if (song != null) {
                for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                    onlinePlayer.playSound(onlinePlayer.getLocation(), song, 1f, 1f);
                }
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && isThisItem(player.getInventory().getItemInMainHand()) && event.getAction().isRightClick()) {
            event.setCancelled(true);
            openSongGUI(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(inventoryTitle)) return;
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem != null && clickedItem.getType().isRecord()) {
            ItemStack pianoKey = player.getInventory().getItemInMainHand();
            if (isThisItem(pianoKey)) {
                setSelectedSong(pianoKey, clickedItem.getType());
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            }
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (!isThisItem(player.getInventory().getItemInMainHand())) return;

        UUID playerId = player.getUniqueId();
        if (event.isSneaking()) {
            if (chargingPlayers.contains(playerId)) return;

            long lastUsed = cooldowns.getOrDefault(playerId, 0L);
            if (System.currentTimeMillis() - lastUsed < COOLDOWN_MS) {
                long remaining = (COOLDOWN_MS - (System.currentTimeMillis() - lastUsed)) / 1000;
                player.sendActionBar(Component.text("Piano Key is on cooldown for " + remaining + "s", NamedTextColor.RED));
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
                        activate(player, charge);
                        cancel();
                        return;
                    }
                    if (charge >= 10) {
                        activate(player, charge);
                        cancel();
                        return;
                    }
                    charge++;
                    bossBar.setProgress((double) charge / 10.0);
                    bossBar.setTitle("Music Radius: " + charge);
                }
            }.runTaskTimer(plugin, 0L, 20L);
        } else {
            if (chargingPlayers.contains(playerId)) {
                BossBar bar = bossBars.get(playerId);
                int charge = (int) Math.round(bar.getProgress() * 10);
                activate(player, charge);
            }
        }
    }

    private void activate(Player player, int radius) {
        UUID playerId = player.getUniqueId();
        chargingPlayers.remove(playerId);
        cooldowns.put(playerId, System.currentTimeMillis());
        if (bossBars.containsKey(playerId)) {
            bossBars.remove(playerId).removeAll();
        }

        if (radius <= 1) return;

        World world = player.getWorld();
        for (Location loc : getCircle(player.getLocation(), radius, 30)) {
            world.spawnParticle(Particle.NOTE, loc, 1);
        }

        for (Player victim : player.getWorld().getPlayers()) {
            if (victim.getLocation().distance(player.getLocation()) <= radius && !victim.equals(player)) {
                victim.damage(4.0, player);
                new BukkitRunnable() {
                    int i = 0;
                    @Override
                    public void run() {
                        if (i >= 15) {
                            cancel();
                            return;
                        }
                        victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 1f, 0.5f + (i * 0.1f));
                        victim.getWorld().spawnParticle(Particle.NOTE, victim.getLocation().add(0, 1.5, 0), 2, 0.5, 0.5, 0.5);
                        i++;
                    }
                }.runTaskTimer(plugin, 0L, 2L);
            }
        }
    }

    private void openSongGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, Component.text(inventoryTitle));
        for (Material disc : MUSIC_DISC_SOUNDS.keySet()) {
            inv.addItem(new ItemStack(disc));
        }
        player.openInventory(inv);
    }

    private void setSelectedSong(ItemStack item, Material song) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(selectedSongKey, PersistentDataType.STRING, song.name());
        List<String> currentLore = meta.getLore();
        if (currentLore != null && currentLore.size() >= 4) {
            currentLore.set(3, ChatColor.DARK_GRAY + "Selected Song: " + song.name().replace("MUSIC_DISC_", "").toLowerCase());
            meta.setLore(currentLore);
        }
        item.setItemMeta(meta);
    }

    private Sound getSelectedSong(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        String songName = meta.getPersistentDataContainer().get(selectedSongKey, PersistentDataType.STRING);
        if (songName != null) {
            try {
                Material songMaterial = Material.valueOf(songName);
                return MUSIC_DISC_SOUNDS.get(songMaterial);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    private List<Location> getCircle(Location center, double radius, int amount) {
        World world = center.getWorld();
        double increment = (2 * Math.PI) / amount;
        List<Location> locations = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            double angle = i * increment;
            double x = center.getX() + (radius * Math.cos(angle));
            double z = center.getZ() + (radius * Math.sin(angle));
            locations.add(new Location(world, x, center.getY() + 1, z));
        }
        return locations;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        chargingPlayers.remove(playerId);
        cooldowns.remove(playerId);
        if (bossBars.containsKey(playerId)) {
            bossBars.remove(playerId).removeAll();
        }
    }

    private static List<String> lore() {
        return Arrays.asList(
                ChatColor.GRAY + "Music I",
                ChatColor.DARK_GRAY + "Hold shift to play music.",
                ChatColor.DARK_GRAY + "Hold shift and right click air to select a song.",
                ChatColor.DARK_GRAY + "Selected Song: NONE"
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> map = new HashMap<>();
        map.put(Enchantment.SHARPNESS, 10);
        map.put(Enchantment.SMITE, 10);
        return map;
    }
}
