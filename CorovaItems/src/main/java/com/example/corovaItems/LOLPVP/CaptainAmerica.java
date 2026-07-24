package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class CaptainAmerica extends CorovaItems implements Listener {

    private final NamespacedKey vibraniumBounceKey;
    private final NamespacedKey vibraniumReturningKey;
    private final JavaPlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Set<UUID> thrownSwords = new HashSet<>();
    private static final long COOLDOWN_MS = 10000; // 10 seconds
    private static final int TIMEOUT_TICKS = 7 * 20; // 7 seconds

    public CaptainAmerica() {
        super(
                ChatColor.AQUA + "Captain America's Vibranium Sword",
                Material.STONE_SWORD,
                lore(),
                enchantments(),
                "vibraniumsword"
        );
        this.plugin = JavaPlugin.getProvidingPlugin(CaptainAmerica.class);
        this.vibraniumBounceKey = new NamespacedKey(plugin, "vibranium_bounces");
        this.vibraniumReturningKey = new NamespacedKey(plugin, "vibranium_returning");
        ItemManager.getInstance().registerItem(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (isThisItem(player.getInventory().getItemInMainHand())) {
                event.setCancelled(true);
                UUID playerId = player.getUniqueId();

                if (thrownSwords.contains(playerId)) {
                    player.sendActionBar(Component.text("You must wait for your sword to return!", NamedTextColor.RED));
                    return;
                }

                long lastUsed = cooldowns.getOrDefault(playerId, 0L);
                if (System.currentTimeMillis() - lastUsed < COOLDOWN_MS) {
                    double remaining = (COOLDOWN_MS - (System.currentTimeMillis() - lastUsed)) / 1000.0;
                    player.sendActionBar(Component.text("This item is on cooldown for " + String.format("%.1f", remaining) + "s", NamedTextColor.RED));
                    return;
                }

                thrownSwords.add(playerId);

                player.getInventory().setItemInMainHand(null);

                Item sword = player.getWorld().dropItem(player.getEyeLocation(), getItemStack());
                sword.setVelocity(player.getEyeLocation().getDirection().multiply(1.5));
                sword.setOwner(player.getUniqueId());
                sword.setCanMobPickup(false);

                PersistentDataContainer data = sword.getPersistentDataContainer();
                data.set(vibraniumBounceKey, PersistentDataType.INTEGER, 0); // Bounce count
                data.set(vibraniumReturningKey, PersistentDataType.BYTE, (byte) 0); // Is returning flag

                new BukkitRunnable() {
                    private int ticksLived = 0;
                    @Override
                    public void run() {
                        ticksLived++;
                        if (sword.isDead()) {
                            cancel();
                            return;
                        }

                        Player owner = Bukkit.getPlayer(player.getUniqueId());
                        if (owner == null || !owner.isOnline() || ticksLived > TIMEOUT_TICKS) {
                            if (owner != null && owner.isOnline()) {
                                owner.getInventory().addItem(getItemStack());
                                owner.sendMessage(Component.text("Your Vibranium Sword has been returned.", NamedTextColor.GREEN));
                                cooldowns.put(owner.getUniqueId(), System.currentTimeMillis());
                            }
                            sword.remove();
                            thrownSwords.remove(playerId);
                            cancel();
                            return;
                        }

                        boolean isReturning = sword.getPersistentDataContainer().getOrDefault(vibraniumReturningKey, PersistentDataType.BYTE, (byte) 0) == 1;

                        if (isReturning) {
                            // Homing logic
                            if (owner.getLocation().distanceSquared(sword.getLocation()) < 2.25) {
                                // Close enough, let it be picked up naturally
                                sword.setVelocity(new Vector());
                            } else {
                                Vector direction = owner.getEyeLocation().toVector().subtract(sword.getLocation().toVector()).normalize();
                                sword.setVelocity(direction.multiply(1.8));
                            }
                        } else {
                            // Forward-throw logic
                            for (LivingEntity entity : sword.getWorld().getNearbyLivingEntities(sword.getLocation(), 1.2)) {
                                if (!entity.getUniqueId().equals(owner.getUniqueId())) {
                                    onHit(owner, entity, sword);
                                    return;
                                }
                            }

                            if (sword.isOnGround()) {
                                int bounces = sword.getPersistentDataContainer().getOrDefault(vibraniumBounceKey, PersistentDataType.INTEGER, 0);
                                if (bounces >= 5) {
                                    sword.getPersistentDataContainer().set(vibraniumReturningKey, PersistentDataType.BYTE, (byte) 1);
                                } else {
                                    sword.getPersistentDataContainer().set(vibraniumBounceKey, PersistentDataType.INTEGER, bounces + 1);
                                    Vector velocity = sword.getVelocity();
                                    velocity.setY(Math.abs(velocity.getY()) * 0.7); // Bounce up
                                    sword.setVelocity(velocity);
                                    sword.getWorld().playSound(sword.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5F, 1.5F);
                                }
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0L, 1L);
            }
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        Item item = event.getItem();
        if (item.getPersistentDataContainer().has(vibraniumBounceKey, PersistentDataType.INTEGER)) {
            if (event.getEntity() instanceof Player player && player.getUniqueId().equals(item.getOwner())) {
                thrownSwords.remove(player.getUniqueId());
                cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
            } else {
                event.setCancelled(true);
            }
        }
    }

    private void onHit(Player thrower, LivingEntity victim, Item sword) {
        victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5F, 1.0F);

        if (victim instanceof Player) {
            damageArmor((Player) victim);
        }
        victim.damage(2.0, thrower);

        sword.getPersistentDataContainer().set(vibraniumReturningKey, PersistentDataType.BYTE, (byte) 1);
    }


    private void damageArmor(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (ItemStack piece : armor) {
            if (piece != null && piece.getType() != Material.AIR) {
                int damage = (int) (piece.getType().getMaxDurability() * ThreadLocalRandom.current().nextDouble(0.5, 0.75));
                piece.setDurability((short) (piece.getDurability() + damage));
                if (piece.getDurability() >= piece.getType().getMaxDurability()) {
                    piece.setAmount(0);
                    player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1F, 1F);
                }
            }
        }
        player.getInventory().setArmorContents(armor);
    }

    private static List<String> lore() {
        return Arrays.asList(
                ChatColor.GRAY + "Boomerang I",
                ChatColor.DARK_GRAY + "Avengers: Age of Ultron Collectible Item."
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> map = new HashMap<>();
        map.put(Enchantment.SHARPNESS, 10);
        map.put(Enchantment.UNBREAKING, 10);
        return map;
    }
}
