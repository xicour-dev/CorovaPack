package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.BlockIterator;

import java.util.*;

public class Assassin8 extends CorovaItems implements Listener {

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long ABILITY_COOLDOWN_MS = 1000;

    public Assassin8() {
        super(
                ChatColor.AQUA + "Assassin8",
                Material.DIAMOND_SWORD,
                lore(),
                enchantments(),
                "assassin8"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return Arrays.asList(
                ChatColor.GRAY + "Teleport VIII",
                ChatColor.DARK_GRAY + "The teleport sword!"
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> map = new HashMap<>();
        map.put(Enchantment.SHARPNESS, 8);
        map.put(Enchantment.BANE_OF_ARTHROPODS, 8);
        map.put(Enchantment.SMITE, 8);
        map.put(Enchantment.FIRE_ASPECT, 8);
        map.put(Enchantment.LOOTING, 8);
        map.put(Enchantment.UNBREAKING, 8);
        return map;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (ItemManager.getInstance().isCorovaItem(player.getInventory().getItemInMainHand(), this) &&
                (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {

            long now = System.currentTimeMillis();
            long lastUsed = cooldowns.getOrDefault(player.getUniqueId(), 0L);

            if (now - lastUsed > ABILITY_COOLDOWN_MS) {
                BlockIterator iterator = new BlockIterator(player, 4);
                Block lastBlock = null;
                while(iterator.hasNext()){
                    lastBlock = iterator.next();
                }

                if (lastBlock != null && !lastBlock.getType().isSolid()) {
                    Location teleportLocation = lastBlock.getLocation();
                    teleportLocation.setDirection(player.getLocation().getDirection());
                    player.teleport(teleportLocation);
                    cooldowns.put(player.getUniqueId(), now);
                } else if (lastBlock != null && lastBlock.getType().isSolid()) {
                    // find the block before it
                    iterator = new BlockIterator(player, 4);
                    Block previousBlock = iterator.next();
                    while(iterator.hasNext()){
                        Block currentBlock = iterator.next();
                        if(currentBlock.equals(lastBlock)){
                            break;
                        }
                        previousBlock = currentBlock;
                    }
                    Location teleportLocation = previousBlock.getLocation();
                    teleportLocation.setDirection(player.getLocation().getDirection());
                    player.teleport(teleportLocation);
                    cooldowns.put(player.getUniqueId(), now);
                }
            } else {
                long remaining = (ABILITY_COOLDOWN_MS - (now - lastUsed));
                player.sendMessage(this.getName() + ChatColor.RED + " is on cooldown for " + (remaining / 1000.0) + " seconds.");
            }
        }
    }
}
