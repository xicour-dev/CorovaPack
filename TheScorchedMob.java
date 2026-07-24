package com.example.corovamobs.mobs;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantBooks.NapalmBook;
import com.example.corovamobs.spawnsystems.GeneralSpawnSystem;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class TheScorchedMob extends AbstractCustomMob {

    public static final NamespacedKey NAMESPACE_KEY = new NamespacedKey("corovamobs", "the_scorched_mob");

    // Base wither skeleton is ~2.4m tall, ~0.7m wide.
    // At scale 1.2 those become ~2.88m tall, ~0.84m wide.
    // Spread values are multiplied by 1.2 from the vanilla-sized defaults.
    private static final double SCALE       = 1.2;
    private static final double SPREAD_XZ   = 0.4 * SCALE; // ~0.48 — covers wider body
    private static final double SPREAD_Y    = 0.8 * SCALE; // ~0.96 — covers taller body
    private static final double SMALL_XZ    = 0.3 * SCALE; // ~0.36
    private static final double SMALL_Y     = 0.5 * SCALE; // ~0.60
    // Vertical offsets: mid-body at half the scaled height, feet cluster near base
    private static final double BODY_OFFSET = 1.44; // ~2.88 / 2
    private static final double FEET_OFFSET = 0.6;

    public TheScorchedMob(GeneralSpawnSystem spawnSystem) {
        super("thescorched", spawnSystem);
    }

    @Override
    public LivingEntity spawn(World world, double x, double y, double z) {
        Location spawnLoc = new Location(world, x + 0.5, y, z + 0.5);

        WitherSkeleton skeleton = (WitherSkeleton) world.spawnEntity(spawnLoc, EntityType.WITHER_SKELETON);

        if (skeleton.getAttribute(Attribute.MAX_HEALTH) != null) {
            skeleton.getAttribute(Attribute.MAX_HEALTH).setBaseValue(150.0);
            skeleton.setHealth(150.0);
        }

        applyAttributes(skeleton);

        try {
            spawnSystem.registerEntity(skeleton, this.getId());
        } catch (Throwable ignored) {}

        startParticleTask(skeleton);

        return skeleton;
    }

    @Override
    public void applyAttributes(LivingEntity entity) {
        if (!(entity instanceof WitherSkeleton skeleton)) return;

        skeleton.setCustomName(ChatColor.GOLD + "The Scorched");
        skeleton.setCustomNameVisible(true);

        if (skeleton.getAttribute(Attribute.SCALE) != null) {
            skeleton.getAttribute(Attribute.SCALE).setBaseValue(SCALE);
        }

        skeleton.addScoreboardTag("TheScorched");

        ItemStack bow = new ItemStack(Material.BOW);
        bow.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.POWER, 10);
        CorovaEnchantments.addEnchant(bow, NapalmBook.NAPALM_ID, 1);

        skeleton.getEquipment().setItemInMainHand(bow);
        skeleton.getEquipment().setItemInMainHandDropChance(0.0f);
    }

    private void startParticleTask(WitherSkeleton skeleton) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(TheScorchedMob.class);
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!skeleton.isValid() || skeleton.isDead()) {
                    this.cancel();
                    return;
                }
                // Main flame cloud centred on the scaled mob's mid-body
                skeleton.getWorld().spawnParticle(Particle.FLAME,
                        skeleton.getLocation().add(0, BODY_OFFSET, 0),
                        10, SPREAD_XZ, SPREAD_Y, SPREAD_XZ, 0.03);

                // Smaller foot-level trail
                skeleton.getWorld().spawnParticle(Particle.SMALL_FLAME,
                        skeleton.getLocation().add(0, FEET_OFFSET, 0),
                        4, SMALL_XZ, SMALL_Y, SMALL_XZ, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }
}