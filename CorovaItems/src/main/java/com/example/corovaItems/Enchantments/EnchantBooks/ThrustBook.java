package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.ItemManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
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

public class ThrustBook extends EnchantmentBook implements Listener {

    private static final double MAX_FUEL = 100.0;
    private static final double BASE_CONSUMPTION = 0.5;
    private static final double MAX_CONSUMPTION_ADD = 0.5;
    private static final double RECHARGE_RATE = 100.0 / (25.0 * 20.0);
    private static final long RECHARGE_DELAY_MS = 5000;

    private static final Map<UUID, Double> fuelLevels = new HashMap<>();
    private static final Map<UUID, Long> lastThrustTimes = new HashMap<>();
    private static final Map<UUID, Integer> thrustTicks = new HashMap<>();

    public ThrustBook() {
        this(1);
    }

    public ThrustBook(int level) {
        super(
                "Book of Thrust",
                CorovaEnchantments.THRUST_ID,
                level,
                "book_thrust",
                Set.of(Material.ELYTRA)
        );
        ItemManager.getInstance().registerItem(this);
    }

    public static void startTask(JavaPlugin plugin) {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                ItemStack elytra = player.getInventory().getChestplate();
                if (elytra == null || elytra.getType() != Material.ELYTRA) continue;
                if (!CorovaEnchantments.hasEnchant(elytra, CorovaEnchantments.THRUST_ID)) continue;

                UUID uuid = player.getUniqueId();
                double fuel = fuelLevels.getOrDefault(uuid, MAX_FUEL);
                long now = System.currentTimeMillis();

                if (player.isGliding() && player.isSneaking() && fuel > 0) {
                    // Thrusting
                    int tTicks = thrustTicks.getOrDefault(uuid, 0);
                    // Gradual acceleration: 0.1 baseline + 0.1 additional over 6s
                    double accelerationFactor = Math.min(1.0, tTicks / 120.0);
                    double power = 0.1 + (0.1 * accelerationFactor);

                    Vector dir = player.getLocation().getDirection();
                    Vector vel = player.getVelocity();

                    // Maintain a minimum speed to prevent stalling even when looking up
                    double currentSpeed = vel.length();
                    if (currentSpeed < 0.5) {
                        player.setVelocity(dir.multiply(0.5));
                        vel = player.getVelocity();
                    }

                    double currentSpeedInDir = vel.dot(dir);
                    if (currentSpeedInDir < 3.0) {
                        player.setVelocity(vel.add(dir.multiply(power)));
                    }

                    // Display particles (Updated to modern PaperMC names)
                    Location back = player.getLocation().subtract(dir.multiply(0.5));
                    player.getWorld().spawnParticle(Particle.FLAME, back, 3, 0.1, 0.1, 0.1, 0.05);
                    player.getWorld().spawnParticle(Particle.SMOKE, back, 2, 0.1, 0.1, 0.1, 0.02);
                    if (tTicks % 6 == 0) {
                        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.4f, 1.2f);
                    }

                    // Variable consumption
                    double consumption = BASE_CONSUMPTION + (MAX_CONSUMPTION_ADD * accelerationFactor);
                    fuel = Math.max(0, fuel - consumption);

                    fuelLevels.put(uuid, fuel);
                    lastThrustTimes.put(uuid, now);
                    thrustTicks.put(uuid, tTicks + 1);

                    sendFuelBar(player, fuel);
                } else {
                    // Not thrusting
                    thrustTicks.remove(uuid);
                    long lastThrust = lastThrustTimes.getOrDefault(uuid, 0L);
                    if (now - lastThrust >= RECHARGE_DELAY_MS) {
                        if (fuel < MAX_FUEL) {
                            fuel = Math.min(MAX_FUEL, fuel + RECHARGE_RATE);
                            fuelLevels.put(uuid, fuel);
                            sendFuelBar(player, fuel);
                        }
                    } else if (fuel < MAX_FUEL) {
                        sendFuelBar(player, fuel);
                    }
                }
            }
        }, 1L, 1L);
    }

    private static void sendFuelBar(Player player, double fuel) {
        int displayPct = (int) Math.round(fuel);
        int bars = (int) Math.round(fuel / 5.0);

        StringBuilder barBuilder = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            barBuilder.append("|");
        }
        String fullBar = barBuilder.toString();
        String coloredBar = EnchantmentBook.applyEnchantmentGradient(CorovaEnchantments.THRUST_ID, fullBar.substring(0, bars))
                + org.bukkit.ChatColor.GRAY + fullBar.substring(bars);

        StringBuilder sb = new StringBuilder();
        sb.append(org.bukkit.ChatColor.DARK_GRAY + "[").append(coloredBar).append(org.bukkit.ChatColor.DARK_GRAY + "] ")
                .append(org.bukkit.ChatColor.YELLOW + String.valueOf(displayPct) + "%");

        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(sb.toString()));
    }

    public static ItemStack getUpgradedBook(ItemStack left, ItemStack right, NamespacedKey keyId, NamespacedKey keyLvl) {
        if (left == null || right == null) return null;
        ItemMeta leftMeta = left.getItemMeta();
        ItemMeta rightMeta = right.getItemMeta();
        if (!(leftMeta instanceof EnchantmentStorageMeta lMeta && rightMeta instanceof EnchantmentStorageMeta rMeta)) return null;

        String id1 = lMeta.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
        String id2 = rMeta.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);

        if (id1 == null || !id1.equals(CorovaEnchantments.THRUST_ID)) return null;
        if (id2 == null || !id2.equals(CorovaEnchantments.THRUST_ID)) return null;

        int lvl1 = lMeta.getPersistentDataContainer().getOrDefault(keyLvl, PersistentDataType.INTEGER, 1);
        int lvl2 = rMeta.getPersistentDataContainer().getOrDefault(keyLvl, PersistentDataType.INTEGER, 1);

        if (lvl1 == lvl2 && lvl1 < CorovaEnchantments.getMaxLevel(CorovaEnchantments.THRUST_ID)) {
            return new ThrustBook(lvl1 + 1).getItemStack();
        }
        return null;
    }
}
