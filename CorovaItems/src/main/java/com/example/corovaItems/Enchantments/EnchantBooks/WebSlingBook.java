package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.BowDamageScaling;
import com.example.corovaItems.ItemManager;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

public class WebSlingBook extends EnchantmentBook implements Listener {

    public static final int MAX_LEVEL = 4;

    private static final String WEB_PROJECTILE_TAG = "websling_projectile";
    private static final double HIT_RADIUS = 0.65;
    private static final double GRAVITY    = 0.04;
    private static final double DRAG       = 0.99;

    private static final Particle.DustOptions DUST_WHITE = new Particle.DustOptions(Color.WHITE, 1.5f);
    private static final Particle.DustOptions DUST_GREEN = new Particle.DustOptions(Color.fromRGB(0, 200, 0), 1.5f);
    private static final Particle.DustOptions DUST_BLACK = new Particle.DustOptions(Color.fromRGB(0, 0, 0), 1.5f);

    private static final Map<UUID, WebProjectileData> activeProjectiles = new HashMap<>();
    private static BukkitTask tickTask;


    public WebSlingBook() {
        this(1);
    }

    public WebSlingBook(int level) {
        super(
                "Book of Web Slinger",
                CorovaEnchantments.WEB_SLING_ID,
                level,
                "book_websling",
                Set.of(Material.BOW, Material.CROSSBOW)
        );
        ItemManager.getInstance().registerItem(this);
        startTickTask();
    }

    // -------------------------------------------------------------------------
    // Bow shoot event — cancel vanilla arrow, consume one arrow, fire web
    // -------------------------------------------------------------------------
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityShootBow(EntityShootBowEvent event) {
        ItemStack bow = event.getBow();
        if (bow == null || !CorovaEnchantments.hasEnchant(bow, CorovaEnchantments.WEB_SLING_ID)) return;

        // Only the instance whose level matches the bow's enchant level should process
        if (CorovaEnchantments.getEnchantLevel(bow, CorovaEnchantments.WEB_SLING_ID) != this.getLevel()) return;

        Entity projectile = event.getProjectile();
        if (!(projectile instanceof Arrow arrow)) return;

        event.setCancelled(true);

        LivingEntity shooter = event.getEntity();

        // ── Arrow consumption ─────────────────────────────────────────────────
        if (shooter instanceof Player player && player.getGameMode() != GameMode.CREATIVE) {
            if (bow.getType() == Material.CROSSBOW) {
                // For crossbows, we just need to uncharge it once.
                // This prevents over-consumption and handles Multishot correctly.
                ItemMeta meta = bow.getItemMeta();
                if (meta instanceof CrossbowMeta crossbowMeta) {
                    if (crossbowMeta.hasChargedProjectiles()) {
                        crossbowMeta.setChargedProjectiles(null);
                        bow.setItemMeta(crossbowMeta);
                    }
                }
            } else {
                // For bows, we must manually consume one arrow of the correct type.
                // We use event.getConsumable() to ensure we decrement the exact stack vanilla picked.
                ItemStack consumable = event.getConsumable();
                if (consumable != null) {
                    // Infinity only works on regular arrows for Bows
                    boolean hasInfinity = bow.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.INFINITY) > 0;
                    if (!(hasInfinity && consumable.getType() == Material.ARROW)) {
                        if (consumable.getAmount() > 1) {
                            consumable.setAmount(consumable.getAmount() - 1);
                        } else {
                            consumable.setAmount(0);
                        }
                    }
                } else {
                    // Fallback if consumable is null (shouldn't happen for players with ammo)
                    if (!consumeOneArrow(player, bow, arrow)) return;
                }
            }
        }

        Vector velocity = arrow.getVelocity();
        double damage   = arrow.getDamage() * velocity.length();

        boolean venomous = MutationManager.getInstance().getSynergyHandler().isVenomous(bow);
        boolean withered = MutationManager.getInstance().getSynergyHandler().isWithered(bow);

        spawnWeb(shooter, velocity, damage, venomous, withered, bow, this.getLevel());
    }

    /**
     * Removes one arrow of the appropriate type from the player's inventory.
     * Respects Infinity for regular arrows.
     */
    private boolean consumeOneArrow(Player player, ItemStack bow, Arrow arrowEntity) {
        Material arrowType = Material.ARROW;
        if (arrowEntity instanceof TippedArrow) arrowType = Material.TIPPED_ARROW;
        else if (arrowEntity instanceof SpectralArrow) arrowType = Material.SPECTRAL_ARROW;

        // Infinity only works on regular arrows for Bows
        boolean hasInfinity = bow.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.INFINITY) > 0;
        if (hasInfinity && arrowType == Material.ARROW) return true;

        PlayerInventory inv = player.getInventory();

        // Check off-hand first
        ItemStack offhand = inv.getItemInOffHand();
        if (offhand != null && offhand.getType() == arrowType) {
            if (offhand.getAmount() > 1) {
                offhand.setAmount(offhand.getAmount() - 1);
            } else {
                inv.setItemInOffHand(new ItemStack(Material.AIR));
            }
            return true;
        }

        // Search main inventory
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && stack.getType() == arrowType) {
                if (stack.getAmount() > 1) {
                    stack.setAmount(stack.getAmount() - 1);
                } else {
                    inv.setItem(i, null);
                }
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // spawnWeb overloads
    // -------------------------------------------------------------------------
    public static void spawnWeb(LivingEntity shooter, Vector velocity, double damage, boolean venomous) {
        spawnWeb(shooter, velocity, damage, venomous, false, null, 1);
    }

    public static void spawnWeb(LivingEntity shooter, Vector velocity, double damage, boolean venomous, boolean withered) {
        spawnWeb(shooter, velocity, damage, venomous, withered, null, 1);
    }

    public static void spawnWeb(LivingEntity shooter, Vector velocity, double damage, boolean venomous, ItemStack bow) {
        spawnWeb(shooter, shooter.getEyeLocation(), velocity, damage, venomous, false, bow, false, 1);
    }

    public static void spawnWeb(LivingEntity shooter, Vector velocity, double damage, boolean venomous, boolean withered, ItemStack bow) {
        spawnWeb(shooter, shooter.getEyeLocation(), velocity, damage, venomous, withered, bow, false, 1);
    }

    public static void spawnWeb(LivingEntity shooter, Vector velocity, double damage, boolean venomous, boolean withered, ItemStack bow, int enchantLevel) {
        spawnWeb(shooter, shooter.getEyeLocation(), velocity, damage, venomous, withered, bow, false, enchantLevel);
    }

    public static void spawnWeb(LivingEntity shooter, Location loc, Vector velocity, double damage, boolean venomous, boolean withered, ItemStack bow) {
        spawnWeb(shooter, loc, velocity, damage, venomous, withered, bow, false, 1);
    }

    public static void spawnWebFromRain(LivingEntity shooter, Location loc, Vector velocity, double damage, boolean venomous, boolean withered, ItemStack bow) {
        spawnWeb(shooter, loc, velocity, damage, venomous, withered, bow, true, 1);
    }

    private static void spawnWeb(LivingEntity shooter, Location loc, Vector velocity, double damage,
                                 boolean venomous, boolean withered, ItemStack bow, boolean isRainWeb, int enchantLevel) {
        World world = loc.getWorld();
        if (world == null) return;

        Item webItem = world.dropItem(loc, new ItemStack(Material.STRING));
        webItem.setVelocity(velocity);
        webItem.setPickupDelay(Integer.MAX_VALUE);
        webItem.setGravity(false);
        webItem.addScoreboardTag(WEB_PROJECTILE_TAG);

        double chargeMultiplier = Math.min(1.0, velocity.length() / 3.0);
        activeProjectiles.put(webItem.getUniqueId(),
                new WebProjectileData(shooter, damage, venomous, withered, bow, chargeMultiplier, isRainWeb, enchantLevel));

        world.playSound(loc, Sound.ENTITY_SPIDER_STEP, 1.0f, 1.8f);
    }

    // -------------------------------------------------------------------------
    // Per-tick physics + collision task
    // -------------------------------------------------------------------------
    private synchronized void startTickTask() {
        if (tickTask != null && !tickTask.isCancelled()) return;

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(WebSlingBook.class);
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeProjectiles.isEmpty()) return;

            Iterator<Map.Entry<UUID, WebProjectileData>> it = activeProjectiles.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, WebProjectileData> entry = it.next();
                UUID id   = entry.getKey();
                WebProjectileData data = entry.getValue();

                Entity entity = Bukkit.getEntity(id);
                if (!(entity instanceof Item webItem) || webItem.isDead() || !webItem.isValid() || webItem.getTicksLived() > 100) {
                    if (entity != null) entity.remove();
                    it.remove();
                    continue;
                }

                Location loc   = webItem.getLocation();
                World    world = loc.getWorld();
                if (world == null) {
                    webItem.remove();
                    it.remove();
                    continue;
                }

                // Physics
                Vector vel = webItem.getVelocity();
                vel.setY(vel.getY() - GRAVITY);
                vel.multiply(DRAG);
                webItem.setVelocity(vel);

                // Particles
                Particle.DustOptions dust = DUST_WHITE;
                if (data.venomous) dust = DUST_GREEN;
                else if (data.withered) dust = DUST_BLACK;

                world.spawnParticle(Particle.DUST, loc, 3, 0.05, 0.05, 0.05, 0, dust);
                if (data.venomous) world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 1, 0.05, 0.05, 0.05, 0.02);
                if (data.withered)  world.spawnParticle(Particle.SMOKE,          loc, 1, 0.05, 0.05, 0.05, 0.02);

                // Block collision
                if (loc.getBlock().getType().isSolid()) {
                    spawnImpactParticles(world, loc, data.venomous, data.withered);
                    webItem.remove();
                    it.remove();
                    continue;
                }

                // Entity collision
                boolean hit = false;
                for (Entity nearby : world.getNearbyEntities(loc, HIT_RADIUS, HIT_RADIUS, HIT_RADIUS)) {
                    if (nearby instanceof LivingEntity target && !nearby.equals(data.shooter)) {
                        applyWebHit(target, data, loc);
                        hit = true;
                        break;
                    }
                }

                if (hit) {
                    webItem.remove();
                    it.remove();
                }
            }
        }, 1L, 1L);
    }

    // -------------------------------------------------------------------------
    // Hit application
    // -------------------------------------------------------------------------
    private void applyWebHit(LivingEntity target, WebProjectileData data, Location loc) {
        World world = loc.getWorld();
        if (world == null) return;

        double    damage = data.damage;
        ItemStack bow    = data.bow;
        if (bow == null && data.shooter instanceof Player player) {
            bow = BowDamageScaling.getBowFromArrow(null, player);
        }

        if (bow != null) {
            double bonusDamage = BowDamageScaling.getEnchantmentDamageBonus(bow, target);
            damage += (bonusDamage * data.chargeMultiplier);
        }

        target.setNoDamageTicks(0);
        target.damage(damage, data.shooter);

        // Level-based slowness: I=Slowness1 0.5s, II=Slowness2 0.5s, III=Slowness3 1s, IV=Slowness4 1s
        int slownessAmplifier = data.enchantLevel - 1; // 0-indexed: level 1 → amplifier 0 (Slowness I)
        int slownessDuration  = (data.enchantLevel <= 2) ? 10 : 20; // ticks: 0.5s or 1s
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slownessDuration, slownessAmplifier));

        if (data.venomous) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 2));
            MutationManager.getInstance().getSynergyHandler().handleMutationSynergy(MutationType.VENOM, data.shooter, target, bow, 1);
        }

        if (data.withered) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
            MutationManager.getInstance().getSynergyHandler().handleMutationSynergy(MutationType.DECAY, data.shooter, target, bow, 1);
        }

        spawnImpactParticles(world, loc, data.venomous, data.withered);
        world.playSound(loc, Sound.ENTITY_ITEM_FRAME_BREAK, 1.0f, 0.5f);

        // Arrow Rain synergy
        if (!data.isRainWeb
                && bow != null
                && CorovaEnchantments.hasEnchant(bow, CorovaEnchantments.ARROW_RAIN_ID)
                && com.example.corovaItems.ItemMutations.SynergyHandler.getArrowRainCallback() != null) {
            com.example.corovaItems.ItemMutations.SynergyHandler.getArrowRainCallback().accept(target, data.shooter);
        }
    }

    private void spawnImpactParticles(World world, Location loc, boolean venomous, boolean withered) {
        Particle.DustOptions dust = DUST_WHITE;
        if (venomous) dust = DUST_GREEN;
        else if (withered) dust = DUST_BLACK;
        world.spawnParticle(Particle.DUST, loc, 30, 0.3, 0.3, 0.3, 0, dust);
        world.spawnParticle(Particle.ITEM, loc, 15, 0.2, 0.2, 0.2, 0.05, new ItemStack(Material.STRING));
    }

    // -------------------------------------------------------------------------
    // Data class
    // -------------------------------------------------------------------------
    private static class WebProjectileData {
        final LivingEntity shooter;
        final double       damage;
        final boolean      venomous;
        final boolean      withered;
        final ItemStack    bow;
        final double       chargeMultiplier;
        final boolean      isRainWeb;
        final int          enchantLevel;

        WebProjectileData(LivingEntity shooter, double damage, boolean venomous, boolean withered,
                          ItemStack bow, double chargeMultiplier, boolean isRainWeb, int enchantLevel) {
            this.shooter          = shooter;
            this.damage           = damage;
            this.venomous         = venomous;
            this.withered         = withered;
            this.bow              = bow;
            this.chargeMultiplier = chargeMultiplier;
            this.isRainWeb        = isRainWeb;
            this.enchantLevel     = enchantLevel;
        }
    }
}