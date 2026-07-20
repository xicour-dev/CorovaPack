package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.ItemManager;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Flare extends EnchantmentBook implements Listener {

    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 8000;

    public Flare() {
        this(1);
    }

    public Flare(int level) {
        super(
                "Book of Flare",
                CorovaEnchantments.FLARE_ID,
                level,
                "book_flare",
                Set.of(Material.ELYTRA)
        );
        ItemManager.getInstance().registerItem(this);
    }

    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return;
        Player player = event.getPlayer();
        if (!player.isGliding()) return;

        ItemStack elytra = player.getInventory().getChestplate();
        if (elytra == null || elytra.getType() != Material.ELYTRA) return;
        if (!CorovaEnchantments.hasEnchant(elytra, CorovaEnchantments.FLARE_ID)) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (cooldowns.containsKey(uuid)) {
            long remaining = (cooldowns.get(uuid) + COOLDOWN_MS) - now;
            if (remaining > 0) {
                String enchantName = EnchantmentBook.applyEnchantmentGradient(CorovaEnchantments.FLARE_ID, CorovaEnchantments.DISPLAY_NAME.getOrDefault(CorovaEnchantments.FLARE_ID, "Flare"));
                player.sendMessage(enchantName + ChatColor.RED + " is on cooldown! (" + (remaining / 1000 + 1) + "s)");
                return;
            }
        }

        cooldowns.put(uuid, now);
        int level = CorovaEnchantments.getEnchantLevel(elytra, CorovaEnchantments.FLARE_ID);
        launchFlares(player, level);
    }

    private void launchFlares(Player player, int level) {
        World world = player.getWorld();
        Location loc = player.getLocation();
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(Flare.class);

        for (int i = 0; i < 2 + level; i++) {
            final int index = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;

                Location flareLoc = player.getLocation().add(0, 0.5, 0);
                Vector backward = player.getLocation().getDirection().multiply(-1).rotateAroundY(Math.toRadians((index - 1) * 30));

                // Find target
                LivingEntity target = null;
                double closest = 30.0;
                for (Entity entity : player.getNearbyEntities(30, 30, 30)) {
                    if (entity instanceof LivingEntity living && (entity instanceof Monster || entity instanceof Player || entity instanceof Phantom) && !entity.equals(player)) {
                        double dist = entity.getLocation().distance(loc);
                        if (dist < closest) {
                            closest = dist;
                            target = living;
                        }
                    }
                }

                final LivingEntity finalTarget = target;
                new FlareProjectile(flareLoc, backward, finalTarget, player, plugin).runTaskTimer(plugin, 0L, 1L);
            }, i * 5L);
        }
    }

    private static class FlareProjectile extends org.bukkit.scheduler.BukkitRunnable {
        private Location loc;
        private Vector vel;
        private final LivingEntity target;
        private final Player shooter;
        private int ticks = 0;
        private final JavaPlugin plugin;

        public FlareProjectile(Location loc, Vector vel, LivingEntity target, Player shooter, JavaPlugin plugin) {
            this.loc = loc;
            this.vel = vel;
            this.target = target;
            this.shooter = shooter;
            this.plugin = plugin;
        }

        @Override
        public void run() {
            if (ticks > 60 || loc.getBlock().getType().isSolid()) {
                this.cancel();
                return;
            }

            if (ticks > 10 && target != null && target.isValid()) {
                Vector direction = target.getEyeLocation().toVector().subtract(loc.toVector()).normalize();
                vel = direction.multiply(1.5);
            } else if (ticks > 10) {
                vel = vel.multiply(1.05); // Accelerate straight if no target
            }

            loc.add(vel);
            loc.getWorld().spawnParticle(Particle.FLAME, loc, 5, 0.1, 0.1, 0.1, 0.02);
            loc.getWorld().spawnParticle(Particle.DUST, loc, 5, 0.1, 0.1, 0.1, 0.02, new Particle.DustOptions(Color.RED, 1.0f));

            if (target != null && loc.distance(target.getLocation()) < 2.0) {
                target.damage(10.0, shooter);
                target.setFireTicks(100);
                loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 2f);
                this.cancel();
            }

            ticks++;
        }
    }

    public static ItemStack getUpgradedBook(ItemStack left, ItemStack right, NamespacedKey keyId, NamespacedKey keyLvl) {
        if (left == null || right == null) return null;
        ItemMeta leftMeta = left.getItemMeta();
        ItemMeta rightMeta = right.getItemMeta();
        if (!(leftMeta instanceof EnchantmentStorageMeta lMeta && rightMeta instanceof EnchantmentStorageMeta rMeta)) return null;

        String id1 = lMeta.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
        String id2 = rMeta.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);

        if (id1 == null || !id1.equals(CorovaEnchantments.FLARE_ID)) return null;
        if (id2 == null || !id2.equals(CorovaEnchantments.FLARE_ID)) return null;

        int lvl1 = lMeta.getPersistentDataContainer().getOrDefault(keyLvl, PersistentDataType.INTEGER, 1);
        int lvl2 = rMeta.getPersistentDataContainer().getOrDefault(keyLvl, PersistentDataType.INTEGER, 1);

        if (lvl1 == lvl2 && lvl1 < CorovaEnchantments.getMaxLevel(CorovaEnchantments.FLARE_ID)) {
            return new Flare(lvl1 + 1).getItemStack();
        }
        return null;
    }
}
