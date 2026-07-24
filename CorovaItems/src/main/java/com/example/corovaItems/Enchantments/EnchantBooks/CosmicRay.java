package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.WandEnchantListener;
import com.example.corovaItems.ItemManager;
import com.example.corovaItems.MageSystem.ManaManager;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class CosmicRay extends EnchantmentBook implements Listener {

    private final int level;

    // ── Tuning constants ──────────────────────────────────────────────────────
    private static final double RAY_BASE_DAMAGE = 20.0;
    private static final double RAY_MAX_DIST    = 50.0;
    private static final double SUB_RAY_MAX_DIST = 20.0;
    private static final int    SUB_STEPS       = 4;
    private static final double AOE_RADIUS      = 1.5;
    private static final double ENTITY_HIT_RADIUS = 0.5;
    private static final double SUB_ENTITY_HIT_RADIUS = 0.3;

    private static final Color COLOR_PURPLE  = Color.fromRGB(180, 50, 255);
    private static final Color COLOR_MAGENTA = Color.fromRGB(255, 80, 255);

    private final Random    random         = new Random();
    private final Set<UUID> firingThisTick = new HashSet<>();

    public CosmicRay() { this(1); }

    public CosmicRay(int level) {
        super(
                "Book of Cosmic Ray",
                CorovaEnchantments.COSMIC_RAY_ID,
                level,
                "book_cosmic_ray_" + level,
                WandEnchantListener.WAND_MATERIALS
        );
        this.level = level;
        ItemManager.getInstance().registerItem(this);
    }

    @Override
    public boolean canApplyTo(ItemStack stack) {
        if (stack == null || !super.canApplyTo(stack)) return false;
        ItemMeta meta = stack.getItemMeta();
        return meta != null && meta.hasDisplayName() && meta.getDisplayName().contains("Wand");
    }

    // ── Interaction handler ───────────────────────────────────────────────────
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player    player = event.getPlayer();
        ItemStack hand   = player.getInventory().getItemInMainHand();
        if (!CorovaEnchantments.hasEnchant(hand, CorovaEnchantments.COSMIC_RAY_ID)) return;
        if (CorovaEnchantments.getEnchantLevel(hand, CorovaEnchantments.COSMIC_RAY_ID) != this.level) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        event.setCancelled(true);

        UUID uuid = player.getUniqueId();
        if (firingThisTick.contains(uuid)) return;
        firingThisTick.add(uuid);
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(this.getClass());
        plugin.getServer().getScheduler().runTask(plugin, () -> firingThisTick.remove(uuid));

        ManaManager mana = ManaManager.getInstance();
        if (mana == null || !mana.tryConsumeMana(player, ManaManager.COST_COSMIC_SPIRAL)) return;

        fireRay(player, plugin);
    }

    // ── Mob-facing entry point ────────────────────────────────────────────────
    public void fireRayForMob(LivingEntity shooter, JavaPlugin plugin) {
        Location eyeLoc = shooter.getEyeLocation();
        Vector   dir    = eyeLoc.getDirection().normalize();
        double   speed  = levelSpeed();

        shooter.getWorld().playSound(eyeLoc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 0.8f);

        new BukkitRunnable() {
            final Location current = eyeLoc.clone();
            double traveled = 0.0;

            @Override
            public void run() {
                if (!shooter.isValid() || traveled >= RAY_MAX_DIST) { cancel(); return; }
                double stepDist = speed / SUB_STEPS;
                for (int s = 0; s < SUB_STEPS; s++) {
                    current.add(dir.clone().multiply(stepDist));
                    traveled += stepDist;
                    World w = current.getWorld();
                    if (w == null) { cancel(); return; }
                    if (s % 2 == 0) w.spawnParticle(Particle.DUST, current, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(COLOR_PURPLE, 1.0f));
                    if (!current.getBlock().isPassable()) { cancel(); return; }
                    for (Entity e : w.getNearbyEntities(current, ENTITY_HIT_RADIUS, ENTITY_HIT_RADIUS, ENTITY_HIT_RADIUS)) {
                        if (!(e instanceof LivingEntity le) || le.equals(shooter)) continue;
                        le.damage(RAY_BASE_DAMAGE, shooter);
                        cancel();
                        return;
                    }
                    if (traveled >= RAY_MAX_DIST) { cancel(); return; }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── Player ray ────────────────────────────────────────────────────────────
    private void fireRay(Player shooter, JavaPlugin plugin) {
        Location eyeLoc = shooter.getEyeLocation();
        Vector   dir    = eyeLoc.getDirection().normalize();
        double   speed  = levelSpeed();
        float    pSize  = level == 1 ? 0.6f : 1.5f;

        shooter.getWorld().playSound(eyeLoc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 0.8f);
        shooter.getWorld().playSound(eyeLoc, Sound.BLOCK_PORTAL_AMBIENT, 0.5f, 2.0f);

        new BukkitRunnable() {
            final Location current = eyeLoc.clone();
            double  traveled  = 0.0;
            boolean hasSplit  = false;

            @Override
            public void run() {
                if (!shooter.isValid() || traveled >= RAY_MAX_DIST) { cancel(); return; }

                double stepDist = speed / SUB_STEPS;
                for (int s = 0; s < SUB_STEPS; s++) {
                    current.add(dir.clone().multiply(stepDist));
                    traveled += stepDist;

                    World w = current.getWorld();
                    if (w == null) { cancel(); return; }

                    if (s % 2 == 0) w.spawnParticle(Particle.DUST, current, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(COLOR_PURPLE, pSize));

                    // Split — fire sub-rays and stop this ray
                    if (!hasSplit && level > 1 && traveled > 5.0) {
                        int splitCount = switch (level) {
                            case 2  -> random.nextInt(2) + 2;   // 2-3
                            case 3  -> random.nextInt(2) + 5;   // 5-6
                            case 4  -> random.nextInt(3) + 10;  // 10-12
                            default -> 0;
                        };
                        for (int i = 0; i < splitCount; i++) {
                            Vector splitDir = dir.clone().add(new Vector(
                                    (random.nextDouble() - 0.5) * 0.4,
                                    (random.nextDouble() - 0.5) * 0.4,
                                    (random.nextDouble() - 0.5) * 0.4
                            )).normalize();
                            fireSubRay(shooter, current.clone(), splitDir, plugin, speed * 0.8, 1);
                        }
                        hasSplit = true;
                        cancel();
                        return;
                    }

                    if (!current.getBlock().isPassable()) {
                        handleImpact(current, null, 1.0, shooter);
                        cancel();
                        return;
                    }

                    for (Entity e : w.getNearbyEntities(current, ENTITY_HIT_RADIUS, ENTITY_HIT_RADIUS, ENTITY_HIT_RADIUS)) {
                        if (!(e instanceof LivingEntity le)) continue;
                        if (!WandEnchantListener.isValidTarget(le, shooter)) continue;
                        handleImpact(current, le, 1.0, shooter);
                        cancel();
                        return;
                    }

                    if (traveled >= RAY_MAX_DIST) { cancel(); return; }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── Sub-ray (splits) ──────────────────────────────────────────────────────
    private void fireSubRay(Player shooter, Location start, Vector dir,
                            JavaPlugin plugin, double speed, int depth) {
        new BukkitRunnable() {
            final Location current = start.clone();
            double  traveled = 0.0;
            boolean hasSplit = false;

            @Override
            public void run() {
                if (traveled >= SUB_RAY_MAX_DIST) { cancel(); return; }

                double stepDist = speed / SUB_STEPS;
                for (int s = 0; s < SUB_STEPS; s++) {
                    current.add(dir.clone().multiply(stepDist));
                    traveled += stepDist;

                    World w = current.getWorld();
                    if (w == null) { cancel(); return; }

                    if (s % 2 == 0) w.spawnParticle(Particle.DUST, current, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(COLOR_MAGENTA, 0.8f));

                    // Second-generation split (levels 3+, depth-1 rays only)
                    if (!hasSplit && depth == 1 && level >= 3 && traveled > 5.0
                            && random.nextDouble() < 0.4) {
                        int subCount = switch (level) {
                            case 3  -> random.nextInt(2) + 1; // 1-2
                            case 4  -> random.nextInt(2) + 2; // 2-3
                            default -> 0;
                        };
                        for (int i = 0; i < subCount; i++) {
                            Vector subDir = dir.clone().add(new Vector(
                                    (random.nextDouble() - 0.5) * 0.4,
                                    (random.nextDouble() - 0.5) * 0.4,
                                    (random.nextDouble() - 0.5) * 0.4
                            )).normalize();
                            fireSubRay(shooter, current.clone(), subDir, plugin, speed * 0.8, depth + 1);
                        }
                        hasSplit = true;
                        cancel();
                        return;
                    }
                    // Prevent re-rolling the split check after it was skipped once
                    if (!hasSplit && depth == 1 && level >= 3 && traveled > 5.0) hasSplit = true;

                    if (!current.getBlock().isPassable()) {
                        handleImpact(current, null, 0.5, shooter);
                        cancel();
                        return;
                    }

                    for (Entity e : w.getNearbyEntities(current, SUB_ENTITY_HIT_RADIUS, SUB_ENTITY_HIT_RADIUS, SUB_ENTITY_HIT_RADIUS)) {
                        if (!(e instanceof LivingEntity le)) continue;
                        if (!WandEnchantListener.isValidTarget(le, shooter)) continue;
                        handleImpact(current, le, 0.5, shooter);
                        cancel();
                        return;
                    }

                    if (traveled >= SUB_RAY_MAX_DIST) { cancel(); return; }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ── Impact handler ────────────────────────────────────────────────────────
    /**
     * Applies direct damage to {@code target} (if non-null), triggers a level-IV
     * AoE explosion, and always plays impact visual/sound effects.
     *
     * @param damageMultiplier fraction of RAY_BASE_DAMAGE for the direct hit
     */
    private void handleImpact(Location loc, LivingEntity target, double damageMultiplier, Player shooter) {
        com.example.corovaItems.WeaponProperties.CorovaCombat.abilityBypass.add(shooter.getUniqueId());
        try {
            if (target != null) {
                target.setNoDamageTicks(0);
                ItemStack weapon = shooter.getInventory().getItemInMainHand();
                target.damage(WandEnchantListener.getScaledDamage(
                        RAY_BASE_DAMAGE * damageMultiplier, weapon, shooter, target), shooter);
            }

            if (level >= 4) {
                World w = loc.getWorld();
                if (w != null) {
                    w.spawnParticle(Particle.EXPLOSION, loc, 1);
                    w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.2f);

                    ItemStack weapon = shooter.getInventory().getItemInMainHand();
                    for (Entity e : w.getNearbyEntities(loc, AOE_RADIUS, AOE_RADIUS, AOE_RADIUS)) {
                        if (!(e instanceof LivingEntity le) || le == target) continue;
                        if (!WandEnchantListener.isValidTarget(le, shooter)) continue;
                        le.setNoDamageTicks(0);
                        le.damage(WandEnchantListener.getScaledDamage(
                                RAY_BASE_DAMAGE * 0.4, weapon, shooter, le), shooter);
                    }
                }
            }
        } finally {
            com.example.corovaItems.WeaponProperties.CorovaCombat.abilityBypass.remove(shooter.getUniqueId());
        }

        spawnImpactEffects(loc);
    }

    private void spawnImpactEffects(Location loc) {
        World w = loc.getWorld();
        if (w == null) return;
        w.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_BREAK,      1.0f, 0.5f);
        w.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE,  1.0f, 0.5f);
        w.spawnParticle(Particle.DUST, loc, 15, 0.2, 0.2, 0.2, 0,
                new Particle.DustOptions(COLOR_PURPLE, 1.5f));
        w.spawnParticle(Particle.FLASH, loc, 1, 0, 0, 0, 0);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private double levelSpeed() {
        return switch (level) {
            case 1  -> 0.75;
            case 2  -> 1.0;
            case 3  -> 1.5;
            case 4  -> 2.0;
            default -> 1.5;
        };
    }

    // ── Upgrade recipe ────────────────────────────────────────────────────────
    public static ItemStack getUpgradedBook(ItemStack left, ItemStack right,
                                            NamespacedKey keyId, NamespacedKey keyLvl) {
        if (left == null || right == null) return null;
        if (!(left.getItemMeta()  instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta lm)) return null;
        if (!(right.getItemMeta() instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta rm)) return null;

        String idL = lm.getPersistentDataContainer().get(keyId, org.bukkit.persistence.PersistentDataType.STRING);
        String idR = rm.getPersistentDataContainer().get(keyId, org.bukkit.persistence.PersistentDataType.STRING);
        if (!CorovaEnchantments.COSMIC_RAY_ID.equals(idL) || !CorovaEnchantments.COSMIC_RAY_ID.equals(idR)) return null;

        int lvlL = lm.getPersistentDataContainer().getOrDefault(keyLvl, org.bukkit.persistence.PersistentDataType.INTEGER, 1);
        int lvlR = rm.getPersistentDataContainer().getOrDefault(keyLvl, org.bukkit.persistence.PersistentDataType.INTEGER, 1);

        return (lvlL == lvlR && lvlL < 4) ? new CosmicRay(lvlL + 1).getItemStack() : null;
    }
}