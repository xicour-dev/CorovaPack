package com.example.corovaItems.LOLPVP;

import com.example.corovaGuard.CorovaGuard;
import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ThorsHammer extends CorovaItems implements Listener {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long ABILITY_COOLDOWN_MS = 7000;

    public ThorsHammer() {
        super(
                ChatColor.AQUA + "Mjolnir",
                Material.STONE_AXE,
                lore(),
                enchantments(),
                "thorshammer"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Lightning I");
        lore.add(ChatColor.DARK_GRAY + "Thor's Hammer: Right click to strike!");
        lore.add(ChatColor.DARK_GRAY + "Avengers: Age of Ultron Collectible Item.");
        return lore;
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> enchantments = new HashMap<>();
        enchantments.put(Enchantment.SHARPNESS, 10);
        enchantments.put(Enchantment.UNBREAKING, 10);
        return enchantments;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!(event.getRightClicked() instanceof LivingEntity rightClicked)) return;

        if (rightClicked instanceof Player && CorovaGuard.getInstance().isPlayerInSafeZone((Player) rightClicked)) {
            CorovaGuard.sendSafeZoneMessage(player);
            event.setCancelled(true);
            return;
        }

        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (ItemManager.getInstance().isCorovaItem(handItem, this)) {
            long now = System.currentTimeMillis();
            long lastUsed = cooldowns.getOrDefault(player.getUniqueId(), 0L);

            if (now - lastUsed > ABILITY_COOLDOWN_MS) {
                player.getWorld().strikeLightningEffect(rightClicked.getLocation());
                rightClicked.damage(4.0, player);
                cooldowns.put(player.getUniqueId(), now);
            } else {
                long remaining = (ABILITY_COOLDOWN_MS - (now - lastUsed)) / 1000;
                player.sendMessage(this.getName() + ChatColor.RED + " is on cooldown for " + remaining + " seconds.");
            }
            event.setCancelled(true);
        }
    }
}
