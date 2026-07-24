package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.ItemManager;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class ArrowRainBook extends EnchantmentBook implements Listener {

    public static final String ARROW_RAIN_ID = "arrowrain";
    public static final int    MAX_LEVEL     = 3;
    public static final int    UPGRADE_COST  = 10;

    // Arrows per volley by level: {min, max} inclusive.
    // I: light shower, II: solid volley, III: overwhelming downpour.
    private static final int[][] ARROW_COUNT_BY_LEVEL = {
            {2,  4},  // Level I
            {4,  7},  // Level II
            {7, 12},  // Level III
    };
    private static final double SPAWN_HEIGHT     = 15.0; // blocks above target
    private static final double SPREAD_RADIUS    = 2.5;  // horizontal spawn scatter
    // Stagger each arrow by this many ticks so they don't all land simultaneously
    private static final int    STAGGER_TICKS    = 3;

    // Tag key placed on rain arrows so their damage is attributed to the shooter
    private static org.bukkit.NamespacedKey RAIN_ARROW_KEY;
    // Stores the shooter UUID on each rain arrow
    private static org.bukkit.NamespacedKey RAIN_SHOOTER_KEY;

    private static final Random RANDOM = new Random();

    public ArrowRainBook() { this(1); }

    public ArrowRainBook(int level) {
        super(
                "Book of Arrow Rain",
                ARROW_RAIN_ID,
                level,
                "book_arrowrain",
                allowedMaterials()
        );

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(ArrowRainBook.class);
        if (RAIN_ARROW_KEY == null) {
            RAIN_ARROW_KEY    = new org.bukkit.NamespacedKey(plugin, "arrow_rain_arrow");
            RAIN_SHOOTER_KEY  = new org.bukkit.NamespacedKey(plugin, "arrow_rain_shooter");
        }
        ItemManager.getInstance().registerItem(this);

        // ── Web Sling synergy wiring ──────────────────────────────────────────────
        // WebSlingBook cancels the real Arrow entity and spawns a custom Item
        // projectile instead, which means EntityDamageByEntityEvent with an Arrow
        // damager is never fired for web hits. We register a callback so
        // WebSlingBook.applyWebHit() can trigger our volley directly.
        //
        // FIX: The callback is only registered by the highest-level ArrowRainBook
        // instance (the last one constructed). Since registerArrowRainCallback
        // overwrites the single static field each time, registering from every
        // level's constructor means the callback always ends up pointing to the
        // last-constructed instance's closure — which happens to be correct — but
        // having every level register identical logic is redundant and fragile.
        // We now only register the callback from level 1 (the base registration);
        // the callback itself reads the bow's actual enchant level at call time,
        // so the correct volley size is always used regardless of which instance
        // registered it.
        if (this.getLevel() == 1) {
            com.example.corovaItems.ItemMutations.SynergyHandler.registerArrowRainCallback((victim, shooter) -> {
                ItemStack bow = shooter.getEquipment().getItemInMainHand();
                if (!isUsableBow(bow)) bow = shooter.getEquipment().getItemInOffHand();
                if (!isUsableBow(bow)) return;
                int bowLevel = CorovaEnchantments.getEnchantLevel(bow, ARROW_RAIN_ID);
                if (bowLevel <= 0) return;
                boolean explode = com.example.corovaItems.ItemMutations.MutationManager.getInstance().getSynergyHandler().hasExplosiveSynergy(bow);
                launchVolley(victim, shooter, plugin, explode, bowLevel);
            });
        }
    }

    public static ItemStack getUpgradedBook(ItemStack left, ItemStack right, String keyId, int keyLvl) {
        if (!keyId.equals(ARROW_RAIN_ID)) return null;

        org.bukkit.inventory.meta.ItemMeta rightMeta = right.getItemMeta();
        if (rightMeta == null) return null;

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(ArrowRainBook.class);
        NamespacedKey bookLvlKey = new NamespacedKey(plugin, "corova_enchant_book_level");

        Integer rightLvl = rightMeta.getPersistentDataContainer().get(bookLvlKey, PersistentDataType.INTEGER);
        if (rightLvl == null) return null;

        if (keyLvl == rightLvl && keyLvl < MAX_LEVEL) {
            return new ArrowRainBook(keyLvl + 1).getItemStack();
        } else if (rightLvl > keyLvl) {
            return new ArrowRainBook(rightLvl).getItemStack();
        }
        return new ArrowRainBook(keyLvl).getItemStack();
    }

    // ─── Arrow hit event ──────────────────────────────────────────────────────
    // Triggers when a real arrow damages a living entity. We check whether the
    // bow that fired it has Arrow Rain, then launch a volley at the victim.
    // Note: this path is NOT reached for Web Sling bows because WebSlingBook
    // cancels the shot before a real Arrow can land — the callback above handles
    // that case instead.

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Only care about arrow hits on living entities
        if (!(event.getDamager() instanceof Arrow arrow)) return;
        if (!(event.getEntity()  instanceof LivingEntity victim)) return;

        // The arrow must have been shot by a living entity
        if (!(arrow.getShooter() instanceof LivingEntity shooter)) return;

        // Skip rain arrows hitting things — don't chain indefinitely
        if (arrow.getPersistentDataContainer().has(RAIN_ARROW_KEY, PersistentDataType.BYTE)) return;

        // Check the bow in the shooter's hand for the Arrow Rain enchant
        ItemStack bow = shooter.getEquipment().getItemInMainHand();
        if (!isUsableBow(bow)) {
            bow = shooter.getEquipment().getItemInOffHand();
            if (!isUsableBow(bow)) return;
        }
        if (!CorovaEnchantments.hasEnchant(bow, ARROW_RAIN_ID)) return;

        // Only one ArrowRainBook instance is registered as a listener to handle hits
        // for all enchantment levels (I, II, III) at once, preventing duplicate volleys.

        // Skip bows with Web Sling — those hits come through the callback above,
        // not through this event, so we'd never reach here for them anyway.
        // Guard is kept for clarity and future-proofing.
        if (CorovaEnchantments.hasEnchant(bow, CorovaEnchantments.WEB_SLING_ID)) return;

        int bowLevel = CorovaEnchantments.getEnchantLevel(bow, ARROW_RAIN_ID);
        boolean explode = com.example.corovaItems.ItemMutations.MutationManager.getInstance().getSynergyHandler().hasExplosiveSynergy(bow);

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(ArrowRainBook.class);
        launchVolley(victim, shooter, plugin, explode, bowLevel);
    }

    // ─── Launch the arrow rain volley ─────────────────────────────────────────

    private static void launchVolley(LivingEntity target, LivingEntity shooter, JavaPlugin plugin, boolean explode, int level) {
        // Clamp level to valid range and look up the arrow count bounds for that level.
        int idx = Math.max(0, Math.min(level - 1, ARROW_COUNT_BY_LEVEL.length - 1));
        int min = ARROW_COUNT_BY_LEVEL[idx][0];
        int max = ARROW_COUNT_BY_LEVEL[idx][1];
        int arrowCount = min + RANDOM.nextInt(max - min + 1);
        UUID shooterUUID = shooter.getUniqueId();

        ItemStack bow = shooter.getEquipment().getItemInMainHand();
        if (!isUsableBow(bow)) {
            bow = shooter.getEquipment().getItemInOffHand();
        }
        boolean isWebSlinger = CorovaEnchantments.hasEnchant(bow, CorovaEnchantments.WEB_SLING_ID);
        boolean venomous = isWebSlinger && com.example.corovaItems.ItemMutations.MutationManager.getInstance().getSynergyHandler().isVenomous(bow);
        boolean withered = isWebSlinger && com.example.corovaItems.ItemMutations.MutationManager.getInstance().getSynergyHandler().isWithered(bow);

        for (int i = 0; i < arrowCount; i++) {
            final int delay = i * STAGGER_TICKS; // stagger each arrow

            ItemStack finalBow = bow;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Re-check target is still valid at fire time
                if (!target.isValid() || target.isDead()) return;

                Location targetLoc = target.getLocation();
                World    world     = targetLoc.getWorld();
                if (world == null) return;

                // Spawn point: 15 blocks up, scattered horizontally
                double spawnX = targetLoc.getX() + (RANDOM.nextDouble() - 0.5) * SPREAD_RADIUS * 2;
                double spawnZ = targetLoc.getZ() + (RANDOM.nextDouble() - 0.5) * SPREAD_RADIUS * 2;
                double spawnY = targetLoc.getY() + SPAWN_HEIGHT;
                Location spawnLoc = new Location(world, spawnX, spawnY, spawnZ);

                // Aim directly at the target's centre (head height)
                Location aimTarget = targetLoc.clone().add(0, target.getHeight() / 2.0, 0);
                Vector direction = aimTarget.toVector()
                        .subtract(spawnLoc.toVector())
                        .normalize();

                if (isWebSlinger) {
                    // Use spawnWebFromRain — marks the projectile as a rain web so it
                    // cannot trigger another Arrow Rain volley when it hits (no infinite chain).
                    WebSlingBook.spawnWebFromRain(shooter, spawnLoc, direction.clone().multiply(1.8), 2.5, venomous, withered, finalBow);
                    return;
                }

                // Spawn the arrow and tag it
                Arrow rainArrow = world.spawnEntity(spawnLoc, EntityType.ARROW) instanceof Arrow a ? a : null;
                if (rainArrow == null) return;

                rainArrow.setShooter(shooter);
                rainArrow.setVelocity(direction.multiply(1.8)); // fast enough to feel impactful
                rainArrow.setDamage(2.5);                       // ~1.25 hearts base damage
                rainArrow.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
                rainArrow.setCritical(true);                    // crit particles for visual flair

                // Tag so we don't re-trigger Arrow Rain on their hits
                rainArrow.getPersistentDataContainer()
                        .set(RAIN_ARROW_KEY, PersistentDataType.BYTE, (byte) 1);
                rainArrow.getPersistentDataContainer()
                        .set(RAIN_SHOOTER_KEY, PersistentDataType.STRING, shooterUUID.toString());

                if (explode) {
                    ExplosiveRoundBook.applyExplosiveRound(finalBow, rainArrow);
                }

                // Whoosh sound at spawn point so players hear the volley incoming
                world.playSound(spawnLoc, Sound.ENTITY_ARROW_SHOOT, 0.6f, 0.8f);

                // Particle trail: white/yellow dust descending with the arrow
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!rainArrow.isValid() || rainArrow.isDead()) { cancel(); return; }
                        world.spawnParticle(Particle.CRIT, rainArrow.getLocation(),
                                3, 0.05, 0.05, 0.05, 0.02);
                    }
                }.runTaskTimer(plugin, 0L, 1L);

            }, delay);
        }

        // Impact sound at the target location a moment after the last arrow lands
        int soundDelay = arrowCount * STAGGER_TICKS + 15;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!target.isValid()) return;
            target.getWorld().playSound(target.getLocation(),
                    Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 1.0f);
        }, soundDelay);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static boolean isUsableBow(ItemStack stack) {
        if (stack == null) return false;
        return stack.getType() == Material.BOW || stack.getType() == Material.CROSSBOW;
    }

    private static Set<Material> allowedMaterials() {
        return Set.of(Material.BOW, Material.CROSSBOW);
    }
}