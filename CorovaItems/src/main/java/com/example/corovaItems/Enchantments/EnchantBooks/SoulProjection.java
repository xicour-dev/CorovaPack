package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaGuard.CorovaGuard;
import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.ItemManager;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

public class SoulProjection extends EnchantmentBook {

    private static final int MAX_LEVEL = 2;
    public static final int UPGRADE_COST = 15; // XP levels per upgrade

    // Soul Projection I constants
    private static final long SP1_COOLDOWN_MILLIS = 5000L;
    private static final double SP1_VEX_SPEED = 1.5;
    private static final int SP1_VEX_LIFETIME_TICKS = 30;
    private static final double SP1_VEX_DAMAGE = 60.0;

    // Soul Projection II constants
    private static final long SP2_COOLDOWN_MILLIS = 5000L;
    private static final double SP2_RAY_TRACE_DISTANCE = 30.0;
    private static final int SP2_VEX_SPAWN_COUNT = 3;
    private static final long SP2_VEX_SPAWN_DELAY_TICKS = 5L;
    private static final double SP2_VEX_SPAWN_RADIUS = 3.0;
    private static final long SP2_VEX_LIFETIME_TICKS = 10L;
    private static final double SP2_VEX_SPEED = 0.75;
    private static final double SP2_VEX_DAMAGE = 20.0;

    private static final String VEX_CUSTOM_NAME_PREFIX = "SoulProjectionVex:";
    private static final String VEX_METADATA_KEY = "has-hit";

    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final Set<UUID> notifiedPlayers = new HashSet<>();
    private final JavaPlugin plugin;

    public SoulProjection() {
        this(1);
    }

    public SoulProjection(int level) {
        super(
                "Book of Soul Projection",
                CorovaEnchantments.SOUL_PROJECTION_ID,
                level,
                "book_soulprojection",
                allowedMaterials()
        );
        this.plugin = JavaPlugin.getProvidingPlugin(SoulProjection.class);

        // Register this item with ItemManager
        ItemManager.getInstance().registerItem(this);
    }

    public static void triggerEffect(LivingEntity damager, LivingEntity target, int level) {
        EnchantmentBook book = EnchantmentBook.getBookById(CorovaEnchantments.SOUL_PROJECTION_ID);
        if (!(book instanceof SoulProjection sp)) return;

        if (sp.checkCooldown(damager, level)) {
            if (level == 1) {
                sp.activateSoulProjectionI(damager);
            } else if (level == 2) {
                if (target != null) {
                    sp.spawnVexes(damager, target);
                } else {
                    sp.activateSoulProjectionII(damager);
                }
            }
        }
    }

    private boolean checkCooldown(LivingEntity damager, int level) {
        long cooldownTime = level == 1 ? SP1_COOLDOWN_MILLIS : SP2_COOLDOWN_MILLIS;
        if (cooldowns.containsKey(damager.getUniqueId())) {
            long timeLeft = System.currentTimeMillis() - cooldowns.get(damager.getUniqueId());
            if (timeLeft < cooldownTime) return false;
        }
        cooldowns.put(damager.getUniqueId(), System.currentTimeMillis());
        return true;
    }

    private static Set<Material> allowedMaterials() {
        Set<Material> materials = new HashSet<>();
        materials.add(Material.WOODEN_HOE);
        materials.add(Material.STONE_HOE);
        materials.add(Material.IRON_HOE);
        materials.add(Material.GOLDEN_HOE);
        materials.add(Material.DIAMOND_HOE);
        materials.add(Material.NETHERITE_HOE);

        Material copperHoe = Material.matchMaterial("COPPER_HOE");
        if (copperHoe != null) {
            materials.add(copperHoe);
        }

        return materials;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (handItem == null || handItem.getType() == Material.AIR) return;
        if (!CorovaEnchantments.hasEnchant(handItem, getEnchantId())) return;

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        int level = CorovaEnchantments.getEnchantLevel(handItem);
        if (level <= 0) return;

        long cooldownTime = level == 1 ? SP1_COOLDOWN_MILLIS : SP2_COOLDOWN_MILLIS;

        if (cooldowns.containsKey(player.getUniqueId())) {
            long timeLeft = System.currentTimeMillis() - cooldowns.get(player.getUniqueId());
            if (timeLeft < cooldownTime) {
                if (!notifiedPlayers.contains(player.getUniqueId())) {
                    String enchantName = EnchantmentBook.applyEnchantmentGradient(getEnchantId(), CorovaEnchantments.DISPLAY_NAME.getOrDefault(getEnchantId(), "Soul Projection"));
                    player.sendMessage(enchantName + ChatColor.RED + " is on cooldown!");
                    notifiedPlayers.add(player.getUniqueId());
                }
                return;
            }
        }

        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        notifiedPlayers.remove(player.getUniqueId());

        if (level == 1) {
            activateSoulProjectionI(player);
        } else if (level == 2) {
            activateSoulProjectionII(player);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity damager)) return;
        if (damager instanceof Player) return;

        ItemStack handItem = (damager.getEquipment() != null) ? damager.getEquipment().getItemInMainHand() : null;
        if (handItem == null || handItem.getType() == Material.AIR) return;
        if (!CorovaEnchantments.hasEnchant(handItem, getEnchantId())) return;

        int level = CorovaEnchantments.getEnchantLevel(handItem);
        if (level <= 0) return;

        long cooldownTime = level == 1 ? SP1_COOLDOWN_MILLIS : SP2_COOLDOWN_MILLIS;

        if (cooldowns.containsKey(damager.getUniqueId())) {
            long timeLeft = System.currentTimeMillis() - cooldowns.get(damager.getUniqueId());
            if (timeLeft < cooldownTime) return;
        }

        cooldowns.put(damager.getUniqueId(), System.currentTimeMillis());

        if (level == 1) {
            activateSoulProjectionI(damager);
        } else if (level == 2) {
            activateSoulProjectionII(damager);
        }
    }

    private void activateSoulProjectionI(LivingEntity caster) {
        Vex vex = caster.getWorld().spawn(caster.getEyeLocation(), Vex.class);
        vex.setCustomName(VEX_CUSTOM_NAME_PREFIX + caster.getUniqueId());
        vex.setCustomNameVisible(false);
        vex.setSilent(true);
        vex.setGravity(false);
        vex.setAware(false);
        vex.setInvulnerable(true);

        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_VEX_CHARGE, 1f, 1f);

        Vector velocity = caster.getEyeLocation().getDirection().multiply(SP1_VEX_SPEED);
        vex.setVelocity(velocity);

        final UUID vexUUID = vex.getUniqueId();
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                Entity entity = Bukkit.getEntity(vexUUID);

                if (entity == null || !entity.isValid() || entity.isDead() || !(entity instanceof Vex)) {
                    this.cancel();
                    return;
                }

                Vex vex = (Vex) entity;

                if (vex.getLocation().getBlock().getType().isSolid()) {
                    vex.remove();
                    this.cancel();
                    return;
                }

                ticks++;

                if (ticks >= SP1_VEX_LIFETIME_TICKS) {
                    vex.remove();
                    this.cancel();
                    return;
                }

                vex.setVelocity(velocity);

                for (Entity nearbyEntity : vex.getNearbyEntities(0.5, 0.5, 0.5)) {
                    if (!(nearbyEntity instanceof LivingEntity)) continue;
                    if (nearbyEntity.equals(caster)) continue;
                    if (nearbyEntity instanceof Vex) continue;

                    LivingEntity target = (LivingEntity) nearbyEntity;

                    if (target instanceof Player victim) {
                        if (CorovaGuard.getInstance().isPlayerInSafeZone(victim)) {
                            if (caster instanceof Player) CorovaGuard.sendSafeZoneMessage((Player) caster);
                            vex.remove();
                            this.cancel();
                            return;
                        }
                    }

                    if (!com.example.corovaItems.Enchantments.EnchantBooks.BoomerangBook.IS_BOOMERANG_HIT.get()) {
                        target.setNoDamageTicks(0);
                        target.damage(SP1_VEX_DAMAGE, caster);
                    }
                    vex.remove();
                    this.cancel();
                    return;
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void activateSoulProjectionII(LivingEntity caster) {
        RayTraceResult result = caster.getWorld().rayTraceEntities(
                caster.getEyeLocation(),
                caster.getEyeLocation().getDirection(),
                SP2_RAY_TRACE_DISTANCE,
                entity -> entity instanceof LivingEntity && !entity.equals(caster)
        );

        if (result != null && result.getHitEntity() instanceof LivingEntity) {
            LivingEntity target = (LivingEntity) result.getHitEntity();
            if (target instanceof Player && CorovaGuard.getInstance().isPlayerInSafeZone((Player) target)) {
                if (caster instanceof Player) CorovaGuard.sendSafeZoneMessage((Player) caster);
                return;
            }
            spawnVexes(caster, target);
        }
    }

    private void spawnVexes(LivingEntity caster, LivingEntity initialTarget) {
        final Location initialTargetCenter = initialTarget.getLocation().add(0, initialTarget.getHeight() / 2.0, 0);

        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= SP2_VEX_SPAWN_COUNT) {
                    this.cancel();
                    return;
                }

                double angle = (360.0 / SP2_VEX_SPAWN_COUNT) * count;
                double x = initialTargetCenter.getX() + SP2_VEX_SPAWN_RADIUS * Math.cos(Math.toRadians(angle));
                double z = initialTargetCenter.getZ() + SP2_VEX_SPAWN_RADIUS * Math.sin(Math.toRadians(angle));
                Location spawnLocation = new Location(initialTarget.getWorld(), x, initialTargetCenter.getY(), z);

                Vex vex = initialTarget.getWorld().spawn(spawnLocation, Vex.class);
                vex.setCustomName(VEX_CUSTOM_NAME_PREFIX + caster.getUniqueId());
                vex.setCustomNameVisible(false);
                vex.setSilent(true);
                vex.setInvulnerable(true);
                vex.setGravity(false);

                Vector direction = initialTargetCenter.toVector().subtract(spawnLocation.toVector()).normalize();

                new BukkitRunnable() {
                    long ticks = 0;

                    @Override
                    public void run() {
                        if (vex.isDead() || ticks >= SP2_VEX_LIFETIME_TICKS) {
                            if (!vex.isDead()) {
                                vex.remove();
                            }
                            this.cancel();
                            return;
                        }

                        if (!initialTarget.isDead() && !vex.hasMetadata(VEX_METADATA_KEY) && vex.getBoundingBox().overlaps(initialTarget.getBoundingBox())) {
                            if (!com.example.corovaItems.Enchantments.EnchantBooks.BoomerangBook.IS_BOOMERANG_HIT.get()) {
                                initialTarget.setNoDamageTicks(0);
                                initialTarget.damage(SP2_VEX_DAMAGE, caster);
                            }
                            vex.setMetadata(VEX_METADATA_KEY, new FixedMetadataValue(plugin, true));
                        }

                        Location loc = vex.getLocation();
                        loc.setDirection(direction);
                        vex.teleport(loc);

                        vex.setVelocity(direction.clone().multiply(SP2_VEX_SPEED));
                        ticks++;
                    }
                }.runTaskTimer(plugin, 0L, 1L);

                caster.getWorld().playSound(spawnLocation, Sound.ENTITY_VEX_CHARGE, 1f, 1f);
                count++;
            }
        }.runTaskTimer(plugin, 0L, SP2_VEX_SPAWN_DELAY_TICKS);
    }

    public static ItemStack getUpgradedBook(ItemStack book1, ItemStack book2, org.bukkit.NamespacedKey keyId, org.bukkit.NamespacedKey keyLvl) {
        if (book1.getType() != Material.ENCHANTED_BOOK || book2.getType() != Material.ENCHANTED_BOOK) {
            return null;
        }

        ItemMeta meta1 = book1.getItemMeta();
        ItemMeta meta2 = book2.getItemMeta();

        if (!(meta1 instanceof EnchantmentStorageMeta bookMeta1) || !(meta2 instanceof EnchantmentStorageMeta bookMeta2)) {
            return null;
        }

        String id1 = bookMeta1.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
        String id2 = bookMeta2.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);

        if (id1 == null || !id1.equals(id2) || !id1.equals(CorovaEnchantments.SOUL_PROJECTION_ID)) {
            return null;
        }

        Integer level1 = bookMeta1.getPersistentDataContainer().get(keyLvl, PersistentDataType.INTEGER);
        Integer level2 = bookMeta2.getPersistentDataContainer().get(keyLvl, PersistentDataType.INTEGER);

        if (level1 == null || !level1.equals(level2) || level1 >= MAX_LEVEL) {
            return null;
        }

        int newLevel = level1 + 1;
        SoulProjection upgradedBook = new SoulProjection(newLevel);
        return upgradedBook.getItemStack();
    }
}
