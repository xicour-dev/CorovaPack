package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.BowDamageScaling;
import com.example.corovaItems.ItemManager;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;

public class BeamBook extends EnchantmentBook {

    // -------------------------------------------------------------------------
    // Level scaling
    //   I   → MAX_CHARGES = 5, damage multiplier = 1.3
    //   II  → MAX_CHARGES = 3, damage multiplier = 1.6
    //   III → MAX_CHARGES = 2, damage multiplier = 2.0
    //
    // Higher levels need fewer hits to charge but deal more damage per shot.
    // -------------------------------------------------------------------------
    private static final int[]    CHARGES_BY_LEVEL   = { 0, 5, 3, 2 }; // index = level
    private static final double[] MULTIPLIER_BY_LEVEL = { 0, 1.3, 1.6, 2.0 };

    private final JavaPlugin plugin;
    private static final Map<UUID, Integer> chargeMap       = new HashMap<>();
    private static final Set<UUID>          beamDamageIgnore = new HashSet<>();

    public BeamBook() {
        this(1);
    }

    public BeamBook(int level) {
        super(
                "Book of Beam",
                CorovaEnchantments.BEAM_ID,
                level,
                "book_beam",
                allowedMaterials()
        );
        this.plugin = JavaPlugin.getProvidingPlugin(BeamBook.class);
        ItemManager.getInstance().registerItem(this);
    }

    private static Set<Material> allowedMaterials() {
        return Set.of(
                Material.WOODEN_SWORD,
                Material.STONE_SWORD,
                Material.IRON_SWORD,
                Material.GOLDEN_SWORD,
                Material.DIAMOND_SWORD,
                Material.NETHERITE_SWORD
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private static int maxChargesForLevel(int level) {
        if (level >= 1 && level < CHARGES_BY_LEVEL.length) return CHARGES_BY_LEVEL[level];
        return 2; // default to hardest for very high levels
    }

    private static double multiplierForLevel(int level) {
        if (level >= 1 && level < MULTIPLIER_BY_LEVEL.length) return MULTIPLIER_BY_LEVEL[level];
        return MULTIPLIER_BY_LEVEL[MULTIPLIER_BY_LEVEL.length - 1] + (level - 3) * 0.5;
    }

    // -------------------------------------------------------------------------
    // triggerEffect — called by BoomerangBook
    // -------------------------------------------------------------------------
    public static void triggerEffect(LivingEntity damager, LivingEntity target, int level) {
        ItemStack hand = (damager.getEquipment() != null) ? damager.getEquipment().getItemInMainHand() : null;
        if (com.example.corovaItems.Enchantments.EnchantBooks.BoomerangBook.IS_BOOMERANG_HIT.get()) {
            hand = com.example.corovaItems.Enchantments.EnchantBooks.BoomerangBook.SYNERGY_ITEM.get();
        }
        double damage = calculateBeamDamageStatic(hand, target, level);
        shootBeamStatic(damager, damage);
    }

    // -------------------------------------------------------------------------
    // Melee hit → charge accumulation
    // -------------------------------------------------------------------------
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity damager)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;

        UUID uuid = damager.getUniqueId();
        if (beamDamageIgnore.contains(uuid)) {
            beamDamageIgnore.remove(uuid);
            return;
        }

        ItemStack hand = (damager.getEquipment() != null) ? damager.getEquipment().getItemInMainHand() : null;
        if (hand == null || !CorovaEnchantments.hasEnchant(hand, CorovaEnchantments.BEAM_ID)) return;

        // Only process for this instance's level
        if (CorovaEnchantments.getEnchantLevel(hand, CorovaEnchantments.BEAM_ID) != this.getLevel()) return;

        int level       = this.getLevel();
        int maxCharges  = maxChargesForLevel(level);
        int currentCharges = chargeMap.getOrDefault(uuid, 0);

        if (currentCharges < maxCharges) {
            currentCharges++;
            chargeMap.put(uuid, currentCharges);

            if (damager instanceof Player player) {
                if (currentCharges == maxCharges) {
                    Component enchantName = EnchantmentBook.applyEnchantmentGradientComponent(CorovaEnchantments.BEAM_ID, "BEAM");
                    player.sendActionBar(enchantName.append(Component.text(" FULLY CHARGED! ", net.kyori.adventure.text.format.NamedTextColor.GOLD, net.kyori.adventure.text.format.TextDecoration.BOLD))
                            .append(Component.text("(Right-Click to fire)", net.kyori.adventure.text.format.NamedTextColor.GRAY)));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 2.0f);
                } else {
                    Component enchantName = EnchantmentBook.applyEnchantmentGradientComponent(CorovaEnchantments.BEAM_ID, "Beam");
                    player.sendActionBar(enchantName.append(Component.text(" Charge: " + currentCharges + "/" + maxCharges, net.kyori.adventure.text.format.NamedTextColor.YELLOW)));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.0f + (currentCharges * 0.2f));
                }
            } else {
                if (currentCharges >= maxCharges) {
                    triggerEffect(damager, (LivingEntity) event.getEntity(), level);
                    chargeMap.put(uuid, 0);
                }
            }

            event.getEntity().getWorld().spawnParticle(
                    Particle.ELECTRIC_SPARK,
                    event.getEntity().getLocation().add(0, 1, 0),
                    5, 0.2, 0.2, 0.2, 0.1);
        }
    }

    // -------------------------------------------------------------------------
    // Right-click → fire
    // -------------------------------------------------------------------------
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || !CorovaEnchantments.hasEnchant(hand, CorovaEnchantments.BEAM_ID)) return;

        // Only the instance whose level matches should process.
        // Without this guard all three registered BeamBook instances (I, II, III) would
        // each attempt to fire / display the charge bar on a single right-click.
        if (CorovaEnchantments.getEnchantLevel(hand, CorovaEnchantments.BEAM_ID) != this.getLevel()) return;

        UUID uuid       = player.getUniqueId();
        int  level      = this.getLevel();
        int  maxCharges = maxChargesForLevel(level);
        int  charges    = chargeMap.getOrDefault(uuid, 0);

        if (charges >= maxCharges) {
            LivingEntity target = getTarget(player, 40);
            double damage = calculateBeamDamage(hand, target, level);
            shootBeam(player, damage);
            chargeMap.put(uuid, 0);
            Component enchantName = EnchantmentBook.applyEnchantmentGradientComponent(CorovaEnchantments.BEAM_ID, "BEAM");
            player.sendActionBar(enchantName.append(Component.text(" FIRED!", net.kyori.adventure.text.format.NamedTextColor.GOLD, net.kyori.adventure.text.format.TextDecoration.BOLD)));
        } else {
            Component enchantName = EnchantmentBook.applyEnchantmentGradientComponent(CorovaEnchantments.BEAM_ID, "Beam");
            player.sendActionBar(enchantName.append(Component.text(" not charged! (" + charges + "/" + maxCharges + ")", net.kyori.adventure.text.format.NamedTextColor.RED)));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        chargeMap.remove(uuid);
        beamDamageIgnore.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Targeting
    // -------------------------------------------------------------------------
    private LivingEntity getTarget(Player player, int range) {
        Entity target = player.getTargetEntity(range);
        return (target instanceof LivingEntity le) ? le : null;
    }

    // -------------------------------------------------------------------------
    // Damage calculation
    // -------------------------------------------------------------------------
    private double calculateBeamDamage(ItemStack sword, LivingEntity target, int level) {
        return calculateBeamDamageStatic(sword, target, level);
    }

    private static double calculateBeamDamageStatic(ItemStack sword, LivingEntity target, int level) {
        if (sword == null) return 1.0;
        double baseDamage = switch (sword.getType()) {
            case WOODEN_SWORD, GOLDEN_SWORD -> 4.0;
            case STONE_SWORD                -> 5.0;
            case IRON_SWORD                 -> 6.0;
            case DIAMOND_SWORD              -> 7.0;
            case NETHERITE_SWORD            -> 8.0;
            default                         -> 1.0;
        };

        if (sword.hasItemMeta()) {
            int sharpness = sword.getEnchantmentLevel(Enchantment.SHARPNESS);
            if (sharpness > 0) baseDamage += (0.5 * sharpness + 0.5);

            if (target != null) {
                int smite = sword.getEnchantmentLevel(Enchantment.SMITE);
                if (smite > 0 && BowDamageScaling.isUndead(target))     baseDamage += 2.5 * smite;

                int bane = sword.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS);
                if (bane > 0 && BowDamageScaling.isArthropod(target))   baseDamage += 2.5 * bane;
            }
        }

        return baseDamage * multiplierForLevel(level);
    }

    // -------------------------------------------------------------------------
    // Beam projectile
    // -------------------------------------------------------------------------
    private void shootBeam(LivingEntity caster, double damage) {
        shootBeamStatic(caster, damage);
    }

    private static void shootBeamStatic(LivingEntity caster, double damage) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(BeamBook.class);
        Location  start   = caster.getEyeLocation();
        Vector    direction = start.getDirection().normalize();
        boolean   isBoomerang = com.example.corovaItems.Enchantments.EnchantBooks.BoomerangBook.IS_BOOMERANG_HIT.get();

        caster.getWorld().playSound(start, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.0f, 2.0f);

        new BukkitRunnable() {
            Location current = start.clone();
            double   distance = 0;
            final double maxDistance = 40;
            final double speed       = 2.0;

            @Override
            public void run() {
                for (int i = 0; i < 4; i++) {
                    current.add(direction.clone().multiply(speed / 4.0));
                    distance += speed / 4.0;

                    current.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, current, 2, 0.05, 0.05, 0.05, 0.02);
                    current.getWorld().spawnParticle(Particle.SMOKE,           current, 1, 0, 0, 0, 0);
                    current.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,  current, 1, 0.1, 0.1, 0.1, 0.05);

                    for (Entity e : current.getWorld().getNearbyEntities(current, 0.5, 0.5, 0.5)) {
                        if (e instanceof LivingEntity hit && e != caster) {
                            if (hit instanceof Player victim &&
                                    com.example.corovaGuard.CorovaGuard.getInstance().isPlayerInSafeZone(victim)) {
                                continue;
                            }

                            beamDamageIgnore.add(caster.getUniqueId());

                            if (!isBoomerang) {
                                hit.setNoDamageTicks(0);
                                com.example.corovaItems.WeaponProperties.CorovaCombat.abilityBypass.add(caster.getUniqueId());
                                try {
                                    hit.damage(damage, caster);
                                } finally {
                                    com.example.corovaItems.WeaponProperties.CorovaCombat.abilityBypass.remove(caster.getUniqueId());
                                }
                            }
                            hit.getWorld().spawnParticle(Particle.BLOCK, current, 30, 0.2, 0.2, 0.2,
                                    Material.REDSTONE_BLOCK.createBlockData());
                            hit.getWorld().playSound(hit.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);

                            this.cancel();
                            return;
                        }
                    }

                    if (distance > maxDistance || !current.getBlock().getType().isAir()) {
                        if (!current.getBlock().getType().isAir()) {
                            current.getWorld().spawnParticle(Particle.FLASH, current, 1);
                        }
                        this.cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }
}