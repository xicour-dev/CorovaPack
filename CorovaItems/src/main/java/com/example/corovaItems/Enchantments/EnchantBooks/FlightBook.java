package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import org.bukkit.*;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FlightBook extends EnchantmentBook implements Listener {

    public static final int MAX_LEVEL    = 3;
    public static final int UPGRADE_COST = 15;

    /** Blocks above ground allowed per level: I=20, II=40, III=60 */
    private static final int HEIGHT_PER_LEVEL = 20;

    private final Set<UUID> hitPlayers    = new HashSet<>();
    private final Set<UUID> flyingPlayers = new HashSet<>();

    public FlightBook() {
        this(1);
    }

    public FlightBook(int level) {
        super(
                "Book of Flight",
                CorovaEnchantments.FLIGHT_ID,
                level,
                "book_flight",
                allowedMaterials()
        );
    }

    private static Set<Material> allowedMaterials() {
        return Set.of(
                Material.LEATHER_CHESTPLATE,
                Material.CHAINMAIL_CHESTPLATE,
                Material.IRON_CHESTPLATE,
                Material.GOLDEN_CHESTPLATE,
                Material.DIAMOND_CHESTPLATE,
                Material.NETHERITE_CHESTPLATE
        );
    }

    // ── Book + Book upgrade result ────────────────────────────────────────────

    /**
     * Called by EnchantmentAnvilListener when two enchanted books are combined.
     * Returns a Flight (level+1) book if both inputs are Flight books of the same
     * level and that level is below MAX_LEVEL, otherwise null.
     */
    public static ItemStack getUpgradedBook(ItemStack left, ItemStack right,
                                            NamespacedKey keyId, NamespacedKey keyLvl) {
        if (!(left.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta lMeta)) return null;
        if (!(right.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta rMeta)) return null;

        String lId = lMeta.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
        String rId = rMeta.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);

        if (!CorovaEnchantments.FLIGHT_ID.equals(lId) || !CorovaEnchantments.FLIGHT_ID.equals(rId)) return null;

        Integer lLvl = lMeta.getPersistentDataContainer().get(keyLvl, PersistentDataType.INTEGER);
        Integer rLvl = rMeta.getPersistentDataContainer().get(keyLvl, PersistentDataType.INTEGER);

        if (lLvl == null || rLvl == null || !lLvl.equals(rLvl)) return null;
        if (lLvl >= MAX_LEVEL) return null;

        return new FlightBook(lLvl + 1).getItemStack();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean hasFlightEnchant(ItemStack item) {
        return CorovaEnchantments.hasEnchant(item, CorovaEnchantments.FLIGHT_ID);
    }

    /** Returns the max height above ground for the given enchant level. */
    private static int maxHeight(int level) {
        return HEIGHT_PER_LEVEL * Math.max(1, level);  // 20 / 40 / 60
    }

    private void checkFlight(Player player) {
        ItemStack chestplate = player.getInventory().getChestplate();
        boolean hasFlightEnchant = chestplate != null && hasFlightEnchant(chestplate);

        if (hasFlightEnchant && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR) {
            player.setAllowFlight(true);
        } else if (!hasFlightEnchant && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR) {
            player.setAllowFlight(false);
        }
    }

    /** Finds the nearest solid block below the entity, or null in the void. */
    private Location findGroundBelow(LivingEntity entity) {
        Location currentLoc = entity.getLocation();
        World world = currentLoc.getWorld();
        int startY = currentLoc.getBlockY();
        int minY   = world.getMinHeight();

        for (int y = startY; y >= minY; y--) {
            Location checkLoc = new Location(world, currentLoc.getX(), y, currentLoc.getZ());
            Material blockType = checkLoc.getBlock().getType();
            if (blockType.isSolid() && blockType != Material.AIR) return checkLoc;
        }
        return null;
    }

    /**
     * Returns true if the entity is within the height limit for its enchant level.
     * Uses the level stored on the worn chestplate (defaults to 1 if unreadable).
     */
    private boolean canFlyAtCurrentHeight(LivingEntity entity) {
        Location ground = findGroundBelow(entity);
        if (ground == null) return true; // void — unlimited

        int level = 1;
        if (entity instanceof Player player) {
            ItemStack chest = player.getInventory().getChestplate();
            if (chest != null) level = Math.max(1, CorovaEnchantments.getEnchantLevel(chest));
        }

        return (entity.getLocation().getY() - ground.getY()) <= maxHeight(level);
    }

    // ── Events ────────────────────────────────────────────────────────────────

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        checkFlight(event.getPlayer());
    }

    @EventHandler
    public void onFlightAttempt(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR) return;

        ItemStack chest = player.getInventory().getChestplate();
        if ((chest != null && hasFlightEnchant(chest))
                || flyingPlayers.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null || !hasFlightEnchant(chest)) return;

        if (canFlyAtCurrentHeight(player) && !hitPlayers.contains(player.getUniqueId())) {
            applyFlightBoost(player);
            flyingPlayers.add(player.getUniqueId());
        }
    }

    private void applyFlightBoost(LivingEntity entity) {
        entity.setVelocity(entity.getEyeLocation().getDirection()
                .multiply(0.5).add(new Vector(0, 0.5, 0)));
        entity.getWorld().playSound(entity.getLocation(),
                Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1f);
        entity.getWorld().spawnParticle(Particle.FIREWORK,
                entity.getLocation(), 30, 0.25, 0.25, 0.25, 0.25);
        entity.setFallDistance(0);

        if (entity instanceof Player player) {
            if (!player.getAllowFlight()) player.setAllowFlight(true);
        }
    }

    @EventHandler
    public void onArrowHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow)) return;
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null || !hasFlightEnchant(chest)) return;

        if (player.getLocation().subtract(0, 1, 0).getBlock().getType() == Material.AIR) {
            hitPlayers.add(player.getUniqueId());
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        ItemStack chest = player.getInventory().getChestplate();
        boolean hasEnchant = chest != null && hasFlightEnchant(chest);

        if (player.isOnGround()
                || player.getLocation().getBlock().getType() != Material.AIR) {
            hitPlayers.remove(uuid);
        }

        if (!flyingPlayers.contains(uuid)) return;

        if (!hasEnchant) {
            player.setAllowFlight(false);
            flyingPlayers.remove(uuid);
            return;
        }

        if (player.isOnGround()
                || player.getLocation().getBlock().getType() != Material.AIR) {
            player.setAllowFlight(false);
            flyingPlayers.remove(uuid);
        }
    }

    @EventHandler
    public void onMobDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity damager)) return;
        if (damager instanceof Player) return;

        ItemStack chest = damager.getEquipment().getChestplate();
        if (chest == null || !hasFlightEnchant(chest)) return;

        if (Math.random() < 0.2 && canFlyAtCurrentHeight(damager)) {
            applyFlightBoost(damager);
        }
    }
}