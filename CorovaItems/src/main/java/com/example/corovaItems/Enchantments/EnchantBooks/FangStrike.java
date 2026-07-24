package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.BowDamageScaling;
import com.example.corovaItems.ItemManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class FangStrike extends EnchantmentBook {

    private static final int MAX_LEVEL = 3;
    public static final int UPGRADE_COST = 15; // XP levels per upgrade

    // Cooldown
    private static final long COOLDOWN_MILLIS = 12000L;

    // Fang Strike I constants
    private static final int FS1_FANG_COUNT = 5;
    private static final double FS1_FANG_SPACING = 1.0;
    private static final long FS1_FANG_DELAY_TICKS = 2L;

    // Fang Strike II constants
    private static final int FS2_FANG_COUNT = 8;
    private static final double FS2_FANG_SPACING = 1.0;
    private static final long FS2_FANG_DELAY_TICKS = 2L;

    // Fang Strike III constants
    private static final int FS3_FANG_COUNT = 12;
    private static final double FS3_FANG_SPACING = 0.8;
    private static final long FS3_FANG_DELAY_TICKS = 1L;
    private static final int FS3_SIDE_FANGS = 2; // Fangs on each side

    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final Set<UUID> notifiedPlayers = new HashSet<>();
    private final JavaPlugin plugin;

    public FangStrike() {
        this(1);
    }

    public FangStrike(int level) {
        super(
                "Book of Fang Strike",
                CorovaEnchantments.FANG_STRIKE_ID,
                level,
                "book_fangstrike",
                allowedMaterials()
        );
        this.plugin = JavaPlugin.getProvidingPlugin(FangStrike.class);

        // Register this item with ItemManager
        ItemManager.getInstance().registerItem(this);
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

        if (cooldowns.containsKey(player.getUniqueId())) {
            long timeLeft = System.currentTimeMillis() - cooldowns.get(player.getUniqueId());
            if (timeLeft < COOLDOWN_MILLIS) {
                if (!notifiedPlayers.contains(player.getUniqueId())) {
                    long secondsLeft = (COOLDOWN_MILLIS - timeLeft) / 1000;
                    String enchantName = EnchantmentBook.applyEnchantmentGradient(CorovaEnchantments.FANG_STRIKE_ID, CorovaEnchantments.DISPLAY_NAME.getOrDefault(CorovaEnchantments.FANG_STRIKE_ID, "Fang Strike"));
                    player.sendMessage(enchantName + ChatColor.RED + " is on cooldown! (" + secondsLeft + "s)");
                    notifiedPlayers.add(player.getUniqueId());
                }
                return;
            }
        }

        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        notifiedPlayers.remove(player.getUniqueId());

        LivingEntity target = getTarget(player, 30);
        double damage = calculateScytheDamage(handItem, target);

        if (level == 1) {
            activateFangStrikeI(player, damage);
        } else if (level == 2) {
            activateFangStrikeII(player, damage);
        } else if (level == 3) {
            activateFangStrikeIII(player, damage);
        }
    }

    public static void triggerEffect(LivingEntity damager, LivingEntity target, int level) {
        if (level <= 0) return;

        if (cooldowns.containsKey(damager.getUniqueId())) {
            long timeLeft = System.currentTimeMillis() - cooldowns.get(damager.getUniqueId());
            if (timeLeft < COOLDOWN_MILLIS) return;
        }

        cooldowns.put(damager.getUniqueId(), System.currentTimeMillis());

        double damage = calculateScytheDamageStatic(damager.getEquipment() != null ? damager.getEquipment().getItemInMainHand() : null, target);

        if (level == 1) {
            activateFangStrikeIStatic(damager, damage);
        } else if (level == 2) {
            activateFangStrikeIIStatic(damager, damage);
        } else if (level == 3) {
            activateFangStrikeIIIStatic(damager, damage);
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
        LivingEntity target = event.getEntity() instanceof LivingEntity ? (LivingEntity) event.getEntity() : null;
        triggerEffect(damager, target, level);
    }

    private LivingEntity getTarget(Player player, int range) {
        Entity target = player.getTargetEntity(range);
        if (target instanceof LivingEntity) {
            return (LivingEntity) target;
        }
        return null;
    }

    private double calculateScytheDamage(ItemStack scythe, LivingEntity target) {
        return calculateScytheDamageStatic(scythe, target);
    }

    private static double calculateScytheDamageStatic(ItemStack scythe, LivingEntity target) {
        double baseDamage = 0.0;

        Material material = scythe.getType();
        baseDamage = switch (material) {
            case WOODEN_HOE -> 1.0;
            case STONE_HOE -> 2.0;
            case IRON_HOE -> 3.0;
            case GOLDEN_HOE -> 1.0;
            case DIAMOND_HOE -> 4.0;
            case NETHERITE_HOE -> 5.0;
            default -> {
                if (material.toString().equals("COPPER_HOE")) {
                    yield 2.5;
                }
                yield 1.0;
            }
        };

        if (scythe != null && scythe.hasItemMeta()) {
            // Sharpness
            int sharpnessLevel = scythe.getEnchantmentLevel(Enchantment.SHARPNESS);
            if (sharpnessLevel > 0) {
                baseDamage += (0.5 * sharpnessLevel + 0.5);
            }

            if (target != null) {
                // Smite
                int smite = scythe.getEnchantmentLevel(Enchantment.SMITE);
                if (smite > 0 && BowDamageScaling.isUndead(target)) {
                    baseDamage += 2.5 * smite;
                }

                // Bane of Arthropods
                int bane = scythe.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS);
                if (bane > 0 && BowDamageScaling.isArthropod(target)) {
                    baseDamage += 2.5 * bane;
                }
            }
        }

        return baseDamage * 3.0;
    }

    private void activateFangStrikeI(LivingEntity caster, double damage) {
        activateFangStrikeIStatic(caster, damage);
    }

    private static void activateFangStrikeIStatic(LivingEntity caster, double damage) {
        spawnFangLineStatic(caster, damage, FS1_FANG_COUNT, FS1_FANG_SPACING, FS1_FANG_DELAY_TICKS, false);
    }

    private void activateFangStrikeII(LivingEntity caster, double damage) {
        activateFangStrikeIIStatic(caster, damage);
    }

    private static void activateFangStrikeIIStatic(LivingEntity caster, double damage) {
        spawnFangLineStatic(caster, damage, FS2_FANG_COUNT, FS2_FANG_SPACING, FS2_FANG_DELAY_TICKS, false);
    }

    private void activateFangStrikeIII(LivingEntity caster, double damage) {
        activateFangStrikeIIIStatic(caster, damage);
    }

    private static void activateFangStrikeIIIStatic(LivingEntity caster, double damage) {
        spawnFangLineStatic(caster, damage, FS3_FANG_COUNT, FS3_FANG_SPACING, FS3_FANG_DELAY_TICKS, false);
        spawnFangLineStatic(caster, damage, FS3_FANG_COUNT, FS3_FANG_SPACING, FS3_FANG_DELAY_TICKS, true);
    }

    private void spawnFangLine(LivingEntity caster, double damage, int fangCount, double spacing, long delayTicks, boolean includeSideLines) {
        spawnFangLineStatic(caster, damage, fangCount, spacing, delayTicks, includeSideLines);
    }

    private static void spawnFangLineStatic(LivingEntity caster, double damage, int fangCount, double spacing, long delayTicks, boolean includeSideLines) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(FangStrike.class);
        Location startLoc = caster.getLocation();
        Vector direction = startLoc.getDirection().setY(0).normalize();

        caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.5f, 1.0f);

        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= fangCount) {
                    this.cancel();
                    return;
                }

                double distance = spacing * (count + 1);
                Location fangLoc = startLoc.clone().add(direction.clone().multiply(distance));
                fangLoc.setY(startLoc.getWorld().getHighestBlockYAt(fangLoc) + 1.0);

                spawnFangStatic(caster, fangLoc, damage);

                if (includeSideLines) {
                    Vector perpendicular = new Vector(-direction.getZ(), 0, direction.getX()).normalize();

                    for (int side = 1; side <= FS3_SIDE_FANGS; side++) {
                        Location rightLoc = fangLoc.clone().add(perpendicular.clone().multiply(side * 1.5));
                        rightLoc.setY(startLoc.getWorld().getHighestBlockYAt(rightLoc) + 1.0);
                        spawnFangStatic(caster, rightLoc, damage);

                        Location leftLoc = fangLoc.clone().subtract(perpendicular.clone().multiply(side * 1.5));
                        leftLoc.setY(startLoc.getWorld().getHighestBlockYAt(leftLoc) + 1.0);
                        spawnFangStatic(caster, leftLoc, damage);
                    }
                }

                count++;
            }
        }.runTaskTimer(plugin, 0L, delayTicks);
    }

    private void spawnFang(LivingEntity caster, Location location, double damage) {
        spawnFangStatic(caster, location, damage);
    }

    private static void spawnFangStatic(LivingEntity caster, Location location, double damage) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(FangStrike.class);
        org.bukkit.entity.EvokerFangs fangs = location.getWorld().spawn(location, org.bukkit.entity.EvokerFangs.class);
        fangs.setOwner(caster);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (fangs.isDead() || !fangs.isValid()) {
                    this.cancel();
                    return;
                }

                for (LivingEntity entity : location.getWorld().getNearbyEntitiesByType(LivingEntity.class, location, 1.5)) {
                    if (entity.equals(caster)) continue;

                    if (entity instanceof Player victim) {
                        if (com.example.corovaGuard.CorovaGuard.getInstance().isPlayerInSafeZone(victim)) {
                            if (caster instanceof Player) com.example.corovaGuard.CorovaGuard.sendSafeZoneMessage((Player) caster);
                            continue;
                        }
                    }

                    if (!com.example.corovaItems.Enchantments.EnchantBooks.BoomerangBook.IS_BOOMERANG_HIT.get()) {
                        // Recalculate damage for each individual hit entity based on their type (undead/arthropod)
                        double finalDamage = calculateScytheDamageStatic(caster.getEquipment().getItemInMainHand(), entity);
                        entity.setNoDamageTicks(0);
                        com.example.corovaItems.WeaponProperties.CorovaCombat.abilityBypass.add(caster.getUniqueId());
                        try {
                            entity.damage(finalDamage, caster);
                        } finally {
                            com.example.corovaItems.WeaponProperties.CorovaCombat.abilityBypass.remove(caster.getUniqueId());
                        }
                    }
                }
            }
        }.runTaskLater(plugin, 10L);

        location.getWorld().playSound(location, Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 1.0f);
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

        if (id1 == null || !id1.equals(id2) || !id1.equals(CorovaEnchantments.FANG_STRIKE_ID)) {
            return null;
        }

        Integer level1 = bookMeta1.getPersistentDataContainer().get(keyLvl, PersistentDataType.INTEGER);
        Integer level2 = bookMeta2.getPersistentDataContainer().get(keyLvl, PersistentDataType.INTEGER);

        if (level1 == null || !level1.equals(level2) || level1 >= MAX_LEVEL) {
            return null;
        }

        int newLevel = level1 + 1;
        FangStrike upgradedBook = new FangStrike(newLevel);
        return upgradedBook.getItemStack();
    }
}
