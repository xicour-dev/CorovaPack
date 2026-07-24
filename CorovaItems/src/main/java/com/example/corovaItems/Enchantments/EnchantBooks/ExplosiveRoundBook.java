package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.BowDamageScaling;
import com.example.corovaItems.ItemManager;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

public class ExplosiveRoundBook extends EnchantmentBook implements Listener {

    private static NamespacedKey EXPLOSIVE_ROUND_KEY;
    private static NamespacedKey POWER_KEY;
    private static NamespacedKey PUNCH_KEY;
    private static NamespacedKey FLAME_KEY;
    private static NamespacedKey PIERCING_KEY;
    private static NamespacedKey SMITE_KEY;
    private static NamespacedKey BANE_KEY;

    public ExplosiveRoundBook() {
        this(1);
    }

    public ExplosiveRoundBook(int level) {
        super(
                "Book of Explosive Round",
                CorovaEnchantments.EXPLOSIVE_ROUND_ID,
                level,
                "book_explosiveround",
                allowedMaterials()
        );

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(ExplosiveRoundBook.class);

        if (EXPLOSIVE_ROUND_KEY == null) {
            EXPLOSIVE_ROUND_KEY = new NamespacedKey(plugin, "explosive_round_level");
            POWER_KEY = new NamespacedKey(plugin, "explosive_round_power");
            PUNCH_KEY = new NamespacedKey(plugin, "explosive_round_punch");
            FLAME_KEY = new NamespacedKey(plugin, "explosive_round_flame");
            PIERCING_KEY = new NamespacedKey(plugin, "explosive_round_piercing");
            SMITE_KEY = new NamespacedKey(plugin, "explosive_round_smite");
            BANE_KEY = new NamespacedKey(plugin, "explosive_round_bane");
        }

        ItemManager.getInstance().registerItem(this);
    }

    public static void applyExplosiveRound(ItemStack bow, Entity projectile) {
        if (bow == null || projectile == null) return;
        if (EXPLOSIVE_ROUND_KEY == null) return;

        if (CorovaEnchantments.hasEnchant(bow, CorovaEnchantments.EXPLOSIVE_ROUND_ID)) {
            int level = CorovaEnchantments.getEnchantLevel(bow, CorovaEnchantments.EXPLOSIVE_ROUND_ID);
            projectile.getPersistentDataContainer().set(
                    EXPLOSIVE_ROUND_KEY,
                    PersistentDataType.INTEGER,
                    level
            );

            // Store additional enchantment levels for scaling
            projectile.getPersistentDataContainer().set(POWER_KEY, PersistentDataType.INTEGER, bow.getEnchantmentLevel(Enchantment.POWER));
            projectile.getPersistentDataContainer().set(PUNCH_KEY, PersistentDataType.INTEGER, bow.getEnchantmentLevel(Enchantment.PUNCH));
            projectile.getPersistentDataContainer().set(FLAME_KEY, PersistentDataType.INTEGER, bow.getEnchantmentLevel(Enchantment.FLAME));
            projectile.getPersistentDataContainer().set(PIERCING_KEY, PersistentDataType.INTEGER, bow.getEnchantmentLevel(Enchantment.PIERCING));
            projectile.getPersistentDataContainer().set(SMITE_KEY, PersistentDataType.INTEGER, bow.getEnchantmentLevel(Enchantment.SMITE));
            projectile.getPersistentDataContainer().set(BANE_KEY, PersistentDataType.INTEGER, bow.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS));
        }
    }

    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof LivingEntity shooter)) return;
        ItemStack bow = event.getBow();
        applyExplosiveRound(bow, event.getProjectile());
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        Entity projectile = event.getEntity();
        if (projectile.hasMetadata("corova_missile")) return;
        if (EXPLOSIVE_ROUND_KEY == null) return;
        if (!projectile.getPersistentDataContainer().has(EXPLOSIVE_ROUND_KEY, PersistentDataType.INTEGER)) return;

        Integer level = projectile.getPersistentDataContainer().get(EXPLOSIVE_ROUND_KEY, PersistentDataType.INTEGER);
        if (level == null) return;

        // Retrieve additional enchantment levels
        Integer powerVal = projectile.getPersistentDataContainer().get(POWER_KEY, PersistentDataType.INTEGER);
        int power = (powerVal != null) ? powerVal : 0;
        Integer punchVal = projectile.getPersistentDataContainer().get(PUNCH_KEY, PersistentDataType.INTEGER);
        int punch = (punchVal != null) ? punchVal : 0;
        Integer flameVal = projectile.getPersistentDataContainer().get(FLAME_KEY, PersistentDataType.INTEGER);
        int flame = (flameVal != null) ? flameVal : 0;
        Integer piercingVal = projectile.getPersistentDataContainer().get(PIERCING_KEY, PersistentDataType.INTEGER);
        int piercing = (piercingVal != null) ? piercingVal : 0;
        Integer smiteVal = projectile.getPersistentDataContainer().get(SMITE_KEY, PersistentDataType.INTEGER);
        int smite = (smiteVal != null) ? smiteVal : 0;
        Integer baneVal = projectile.getPersistentDataContainer().get(BANE_KEY, PersistentDataType.INTEGER);
        int bane = (baneVal != null) ? baneVal : 0;

        Location loc = event.getHitBlock() != null ? event.getHitBlock().getLocation() :
                (event.getHitEntity() != null ? event.getHitEntity().getLocation() : projectile.getLocation());

        World world = loc.getWorld();
        if (world == null) return;

        // Scale explosion with level and Power/Punch
        float yield = 0.3f + (level * 0.05f) + (power * 0.05f) + (punch * 0.05f);
        double radius = 2.0 + (level * 0.5) + (power * 0.2);
        double baseDamage = 10.0 * level + (power * 3.0);

        // Move explosion higher and reduce yield to further reduce vertical launch
        Location explosionLoc = loc.clone().add(0, 2.5, 0);
        // Flame enchantment makes the explosion incendiary
        world.createExplosion(explosionLoc, yield, false, false);

        if (flame > 0) {
            Location effectLoc = loc.clone();
            if (event.getHitBlock() != null) {
                effectLoc.add(0.5, 0.5, 0.5);
            }
            NapalmBook.spawnNapalmCloud(world, effectLoc, level, JavaPlugin.getProvidingPlugin(ExplosiveRoundBook.class));
        }

        // Manually apply damage to nearby entities
        Entity shooter = null;
        if (projectile instanceof Projectile proj && proj.getShooter() instanceof Entity s) {
            shooter = s;
        }

        for (Entity entity : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (entity instanceof LivingEntity living && !living.equals(shooter)) {
                double distance = living.getLocation().distance(loc);
                if (distance <= radius) {
                    double bonusDamage = 0.0;
                    if (BowDamageScaling.isUndead(living)) {
                        if (smite <= 5) {
                            bonusDamage = 1.5 * smite;
                        } else {
                            bonusDamage = 7.5 + 1.0 * (smite - 5);
                        }
                    } else if (BowDamageScaling.isArthropod(living)) {
                        if (bane <= 5) {
                            bonusDamage = 1.5 * bane;
                        } else {
                            bonusDamage = 7.5 + 1.0 * (bane - 5);
                        }
                    }

                    // Apply manual damage that scales with distance
                    double manualDamage = (baseDamage + bonusDamage) * (1 - (distance / radius));
                    if (shooter instanceof LivingEntity livingShooter) {
                        com.example.corovaItems.WeaponProperties.CorovaCombat.abilityBypass.add(livingShooter.getUniqueId());
                        try {
                            living.damage(manualDamage, livingShooter);
                        } finally {
                            com.example.corovaItems.WeaponProperties.CorovaCombat.abilityBypass.remove(livingShooter.getUniqueId());
                        }
                    } else {
                        living.damage(manualDamage);
                    }
                }
            }
        }

        // Handle projectile removal, considering Piercing
        if (event.getHitBlock() != null || piercing <= 0) {
            projectile.remove();
        }
    }

    private static Set<Material> allowedMaterials() {
        return Set.of(Material.BOW, Material.CROSSBOW);
    }
}
