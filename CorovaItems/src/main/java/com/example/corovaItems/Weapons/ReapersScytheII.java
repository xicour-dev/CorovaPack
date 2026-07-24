package com.example.corovaItems.Weapons;

import com.example.corovaGuard.CorovaGuard;
import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

public class ReapersScytheII extends CorovaItems implements Listener {

    private static final long COOLDOWN_MILLIS = 5000L;
    private static final double RAY_TRACE_DISTANCE = 30.0;
    private static final int VEX_SPAWN_COUNT = 3;
    private static final long VEX_SPAWN_DELAY_TICKS = 5L;
    private static final double VEX_SPAWN_RADIUS = 3.0;
    private static final long VEX_LIFETIME_TICKS = 10L; // 0.5 seconds
    private static final double VEX_SPEED = 0.75;
    private static final double VEX_DAMAGE = 20.0;
    private static final int SHARPNESS_LEVEL = 12;
    private static final double ATTACK_DAMAGE = 4.0;
    private static final String VEX_CUSTOM_NAME_PREFIX = "ReapersScytheIISoul:";
    private static final String VEX_METADATA_KEY = "has-hit";

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Set<UUID> notifiedPlayers = new HashSet<>();
    private final JavaPlugin plugin;

    public ReapersScytheII() {
        super(
                ChatColor.DARK_RED + "Reaper's Scythe II",
                new Material[]{Material.DIAMOND_HOE},
                lore(),
                enchantments(),
                "reapersscytheii",
                null,
                attributes()
        );
        this.plugin = JavaPlugin.getProvidingPlugin(CorovaItems.class);
        ItemManager.getInstance().registerItem(this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private static List<String> lore() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Soul Projection II");
        lore.add(ChatColor.DARK_GRAY + "Summons 3 vexes to attack your target.");
        return lore;
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> map = new HashMap<>();
        map.put(Enchantment.SHARPNESS, SHARPNESS_LEVEL);
        return map;
    }

    private static Map<Attribute, AttributeModifier> attributes() {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(CorovaItems.class);
        return Map.of(
                Attribute.ATTACK_SPEED,
                new AttributeModifier(new NamespacedKey(plugin, "reapersscytheii_attack_speed"), 0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HAND),
                Attribute.ATTACK_DAMAGE,
                new AttributeModifier(new NamespacedKey(plugin, "reapersscytheii_attack_damage"), ATTACK_DAMAGE, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HAND)
        );
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack handItem = player.getInventory().getItemInMainHand();

        if (!isThisItem(handItem)) return;

        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            if (cooldowns.containsKey(player.getUniqueId())) {
                long timeLeft = System.currentTimeMillis() - cooldowns.get(player.getUniqueId());
                if (timeLeft < COOLDOWN_MILLIS) {
                    if (!notifiedPlayers.contains(player.getUniqueId())) {
                        player.sendMessage(ChatColor.RED + "Ability is on cooldown!");
                        notifiedPlayers.add(player.getUniqueId());
                    }
                    return;
                }
            }

            RayTraceResult result = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), RAY_TRACE_DISTANCE, entity -> entity instanceof LivingEntity && !entity.equals(player));

            if (result != null && result.getHitEntity() instanceof LivingEntity) {
                LivingEntity target = (LivingEntity) result.getHitEntity();
                if (target instanceof Player && CorovaGuard.getInstance().isPlayerInSafeZone((Player) target)) {
                    CorovaGuard.sendSafeZoneMessage(player);
                    return;
                }
                cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                notifiedPlayers.remove(player.getUniqueId());
                spawnVexes(player, target);
            }
        }
    }

    private void spawnVexes(Player player, LivingEntity initialTarget) {
        final Location initialTargetCenter = initialTarget.getLocation().add(0, initialTarget.getHeight() / 2.0, 0);

        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= VEX_SPAWN_COUNT) {
                    this.cancel();
                    return;
                }

                double angle = (360.0 / VEX_SPAWN_COUNT) * count;
                double x = initialTargetCenter.getX() + VEX_SPAWN_RADIUS * Math.cos(Math.toRadians(angle));
                double z = initialTargetCenter.getZ() + VEX_SPAWN_RADIUS * Math.sin(Math.toRadians(angle));
                Location spawnLocation = new Location(initialTarget.getWorld(), x, initialTargetCenter.getY(), z);

                Vex vex = initialTarget.getWorld().spawn(spawnLocation, Vex.class);
                vex.setCustomName(VEX_CUSTOM_NAME_PREFIX + player.getUniqueId());
                vex.setCustomNameVisible(false);
                vex.setSilent(true);
                vex.setInvulnerable(true);
                vex.setGravity(false);

                Vector direction = initialTargetCenter.toVector().subtract(spawnLocation.toVector()).normalize();

                new BukkitRunnable() {
                    long ticks = 0;

                    @Override
                    public void run() {
                        if (vex.isDead() || ticks >= VEX_LIFETIME_TICKS) {
                            if (!vex.isDead()) {
                                vex.remove();
                            }
                            this.cancel();
                            return;
                        }

                        if (!initialTarget.isDead() && !vex.hasMetadata(VEX_METADATA_KEY) && vex.getBoundingBox().overlaps(initialTarget.getBoundingBox())) {
                            initialTarget.setNoDamageTicks(0);
                            initialTarget.damage(VEX_DAMAGE, player);
                            vex.setMetadata(VEX_METADATA_KEY, new FixedMetadataValue(plugin, true));
                        }

                        Location loc = vex.getLocation();
                        loc.setDirection(direction);
                        vex.teleport(loc);

                        vex.setVelocity(direction.clone().multiply(VEX_SPEED));
                        ticks++;
                    }
                }.runTaskTimer(plugin, 0L, 1L);

                player.getWorld().playSound(spawnLocation, Sound.ENTITY_VEX_CHARGE, 1f, 1f);
                count++;
            }
        }.runTaskTimer(plugin, 0L, VEX_SPAWN_DELAY_TICKS);
    }

}