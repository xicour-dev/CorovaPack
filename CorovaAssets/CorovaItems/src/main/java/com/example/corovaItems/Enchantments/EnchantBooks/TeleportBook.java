package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.Vector;

import java.util.*;

public class TeleportBook extends EnchantmentBook implements Listener {

    private final Set<UUID> teleportEnabled = new HashSet<>();
    private final Map<UUID, ItemStack> primedTridents = new HashMap<>();
    private final Set<Arrow>   trackedArrows   = new HashSet<>();
    private final Set<Trident> trackedTridents = new HashSet<>();

    // Melee cooldown: same 1 s as Assassin8
    private static final Map<UUID, Long> meleeCooldowns = new HashMap<>();
    private static final long MELEE_COOLDOWN_MS = 1_000;

    public static void triggerEffect(LivingEntity damager, LivingEntity target, int level) {
        if (!(damager instanceof Player player)) return;

        long now = System.currentTimeMillis();
        long lastUsed = meleeCooldowns.getOrDefault(player.getUniqueId(), 0L);

        if (now - lastUsed > MELEE_COOLDOWN_MS) {
            BlockIterator iterator = new BlockIterator(player, 4);
            Block lastBlock = null;
            while (iterator.hasNext()) {
                lastBlock = iterator.next();
            }

            if (lastBlock != null && !lastBlock.getType().isSolid()) {
                Location dest = lastBlock.getLocation();
                dest.setDirection(player.getLocation().getDirection());
                player.teleport(dest);
                meleeCooldowns.put(player.getUniqueId(), now);
            } else if (lastBlock != null) {
                // Land on the block just before the solid one
                iterator = new BlockIterator(player, 4);
                Block previous = iterator.next();
                while (iterator.hasNext()) {
                    Block current = iterator.next();
                    if (current.equals(lastBlock)) break;
                    previous = current;
                }
                Location dest = previous.getLocation();
                dest.setDirection(player.getLocation().getDirection());
                player.teleport(dest);
                meleeCooldowns.put(player.getUniqueId(), now);
            }
        }
    }

    public TeleportBook() {
        this(1);
    }

    public TeleportBook(int level) {
        super(
                "Book of Teleport",
                CorovaEnchantments.TELEPORT_ID,
                level,
                "book_teleport",
                allowedMaterialsStatic()
        );
    }

    private static Set<Material> allowedMaterialsStatic() {
        Set<Material> s = new HashSet<>();
        // ── Ranged (original) ──────────────────────────────────────────────
        s.add(Material.BOW);
        s.add(Material.CROSSBOW);
        s.add(Material.TRIDENT);
        // ── Melee (new) ────────────────────────────────────────────────────
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
        return s;
    }

    // ── Helper: is this item a melee weapon? ─────────────────────────────────

    private static boolean isMelee(Material type) {
        return switch (type) {
            case WOODEN_SWORD, STONE_SWORD, IRON_SWORD, GOLDEN_SWORD,
                 DIAMOND_SWORD, NETHERITE_SWORD,
                 WOODEN_AXE, STONE_AXE, IRON_AXE, GOLDEN_AXE,
                 DIAMOND_AXE, NETHERITE_AXE -> true;
            default -> false;
        };
    }

    private static boolean isRanged(Material type) {
        return type == Material.BOW || type == Material.CROSSBOW || type == Material.TRIDENT;
    }

    // ── Interact handler – handles both melee dash-teleport and ranged toggle ─

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        Action  action = event.getAction();
        Player  player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item == null || item.getType() == Material.AIR) return;
        if (!CorovaEnchantments.hasEnchant(item, CorovaEnchantments.TELEPORT_ID)) return;

        Material type = item.getType();

        // ── Melee: right-click to dash-teleport (Assassin8 logic) ────────────
        if (isMelee(type)) {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
                event.setCancelled(true);

                long now = System.currentTimeMillis();
                long lastUsed = meleeCooldowns.getOrDefault(player.getUniqueId(), 0L);

                if (now - lastUsed > MELEE_COOLDOWN_MS) {
                    triggerEffect(player, null, CorovaEnchantments.getEnchantLevel(item));
                } else {
                    long remaining = MELEE_COOLDOWN_MS - (now - lastUsed);
                    String enchantName = EnchantmentBook.applyEnchantmentGradient(CorovaEnchantments.TELEPORT_ID, CorovaEnchantments.DISPLAY_NAME.getOrDefault(CorovaEnchantments.TELEPORT_ID, "Teleport"));
                    player.sendActionBar(enchantName + ChatColor.RED + " is on cooldown for "
                            + (remaining / 1000.0) + " seconds.");
                }
            }
            return; // melee handling done
        }

        // ── Ranged: left-click to toggle, right-click to prime trident ────────
        if (!isRanged(type)) return;

        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            boolean enabled = !teleportEnabled.contains(player.getUniqueId());
            if (enabled) {
                teleportEnabled.add(player.getUniqueId());
                String enchantName = EnchantmentBook.applyEnchantmentGradient(CorovaEnchantments.TELEPORT_ID, "Teleportation");
                player.sendActionBar(enchantName + ChatColor.GREEN + ": ON");
            } else {
                teleportEnabled.remove(player.getUniqueId());
                String enchantName = EnchantmentBook.applyEnchantmentGradient(CorovaEnchantments.TELEPORT_ID, "Teleportation");
                player.sendActionBar(enchantName + ChatColor.RED + ": OFF");
            }
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        } else if (type == Material.TRIDENT
                && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            if (teleportEnabled.contains(player.getUniqueId())) {
                primedTridents.put(player.getUniqueId(), item);
            }
        }
    }

    // ── Arrow fired from bow/crossbow ─────────────────────────────────────────

    @EventHandler
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getProjectile() instanceof Arrow arrow)) return;
        ItemStack bow = event.getBow();
        if (bow == null) return;
        if (!CorovaEnchantments.hasEnchant(bow, CorovaEnchantments.TELEPORT_ID)) return;

        ProjectileSource shooter = arrow.getShooter();
        if (shooter instanceof Player player) {
            if (teleportEnabled.contains(player.getUniqueId())) trackedArrows.add(arrow);
        } else if (shooter instanceof LivingEntity) {
            trackedArrows.add(arrow);
        }
    }

    // ── Trident thrown ────────────────────────────────────────────────────────

    @EventHandler
    public void onThrowTrident(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) return;
        if (!(trident.getShooter() instanceof ProjectileSource shooter)) return;

        if (shooter instanceof Player player) {
            if (primedTridents.remove(player.getUniqueId()) != null) {
                trackedTridents.add(trident);
            }
        } else if (shooter instanceof LivingEntity mob) {
            EntityEquipment equipment = mob.getEquipment();
            if (equipment == null) return;
            ItemStack heldItem = equipment.getItemInMainHand();
            if (CorovaEnchantments.hasEnchant(heldItem, CorovaEnchantments.TELEPORT_ID)) {
                trackedTridents.add(trident);
            }
        }
    }

    // ── Projectile lands → teleport the shooter ───────────────────────────────

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof Arrow arrow) {
            handleArrowTeleport(arrow);
        } else if (event.getEntity() instanceof Trident trident) {
            handleTridentTeleport(trident);
        }
    }

    private void handleArrowTeleport(Arrow arrow) {
        if (!trackedArrows.contains(arrow)) return;
        ProjectileSource shooter = arrow.getShooter();
        if (!(shooter instanceof LivingEntity entity)) return;

        entity.teleport(buildLandingLocation(arrow.getLocation(), arrow.getVelocity()));
        arrow.getWorld().playEffect(arrow.getLocation(), Effect.ENDER_SIGNAL, 4);
        arrow.getWorld().playSound(arrow.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        cleanupArrow(arrow);
    }

    private void handleTridentTeleport(Trident trident) {
        if (!trackedTridents.contains(trident)) return;
        ProjectileSource shooter = trident.getShooter();
        if (!(shooter instanceof LivingEntity entity)) return;

        entity.teleport(buildLandingLocation(trident.getLocation(), trident.getVelocity()));
        trident.getWorld().playEffect(trident.getLocation(), Effect.ENDER_SIGNAL, 4);
        trident.getWorld().playSound(trident.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        cleanupTrident(trident);
    }

    private static Location buildLandingLocation(Location origin, Vector velocity) {
        Vector v = velocity.normalize();
        return new Location(
                origin.getWorld(),
                origin.getX(), origin.getY(), origin.getZ(),
                (float) Math.toDegrees(Math.atan2(-v.getX(), v.getZ())),
                (float) Math.toDegrees(Math.asin(-v.getY()))
        );
    }

    private void cleanupArrow(Arrow arrow) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(this.getClass());
        Bukkit.getScheduler().runTaskLater(plugin, () -> trackedArrows.remove(arrow), 3L);
    }

    private void cleanupTrident(Trident trident) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(this.getClass());
        Bukkit.getScheduler().runTaskLater(plugin, () -> trackedTridents.remove(trident), 3L);
    }
}