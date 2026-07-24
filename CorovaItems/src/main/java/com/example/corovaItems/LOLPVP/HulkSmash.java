package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HulkSmash extends CorovaItems implements Listener {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_MS = 5000; // 5 seconds

    public HulkSmash() {
        super(
                ChatColor.AQUA + "Hulk Smash",
                Material.SLIME_BALL,
                lore(),
                enchantments(),
                "hulksmash"
        );
        ItemManager.getInstance().registerItem(this);
        Bukkit.getPluginManager().registerEvents(this, JavaPlugin.getProvidingPlugin(HulkSmash.class));
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (isThisItem(player.getInventory().getItemInMainHand())) {
                event.setCancelled(true);

                UUID playerId = player.getUniqueId();
                long lastUsed = cooldowns.getOrDefault(playerId, 0L);
                long now = System.currentTimeMillis();

                if (now - lastUsed < COOLDOWN_MS) {
                    long remaining = (COOLDOWN_MS - (now - lastUsed)) / 1000;
                    player.sendActionBar(Component.text(ChatColor.RED + "Hulk Smash is on cooldown for " + remaining + "s"));
                    return;
                }

                cooldowns.put(playerId, now);
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2f, 1f);

                // Launch nearby entities
                for (Entity victim : player.getNearbyEntities(10, 10, 10)) {
                    if (victim instanceof LivingEntity) {
                        victim.setVelocity(new Vector(0, 1.2, 0));
                    }
                }

                // Ground crack particle effect
                int radius = 10;
                Block block = player.getLocation().getBlock();
                for (int x = -radius; x <= radius; x++) {
                    for (int z = -radius; z <= radius; z++) {
                        Block groundBlock = block.getRelative(x, -1, z);
                        if (groundBlock.getType() != Material.AIR) {
                            BlockData blockData = groundBlock.getBlockData();
                            player.getWorld().spawnParticle(
                                    Particle.BLOCK_CRUMBLE,
                                    block.getLocation().add(x, 0, z),
                                    5, 0.5, 0.5, 0.5,
                                    blockData
                            );
                        }
                    }
                }
            }
        }
    }

    private static List<String> lore() {
        return List.of(
                ChatColor.DARK_GRAY + "Right click to smash!",
                ChatColor.DARK_GRAY + "Avengers: Age Of Ultron Collectable Item."
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        return Collections.singletonMap(Enchantment.UNBREAKING, 1);
    }
}
