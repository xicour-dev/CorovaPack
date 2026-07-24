package com.example.corovaItems.LOLPVP;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.block.Block;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Beast extends CorovaItems implements Listener {

    public Beast() {
        super(
                ChatColor.AQUA + "The Beast", // Name
                Material.DIAMOND_AXE,         // Material
                lore(),                        // Lore
                enchantments(),                // Enchantments
                "thebeast"                     // Internal ID
        );
    }

    @EventHandler
    public void onBlockClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if (block == null) return;

        if (ItemManager.getInstance().isCorovaItem(player.getInventory().getItemInMainHand(), this)
                && event.getAction() == Action.LEFT_CLICK_BLOCK
                && block.getType() != Material.BEDROCK
                && block.getType() != Material.CHEST
                && block.getType() != Material.TRAPPED_CHEST
                && !event.isCancelled())
        {

            // Play block break effect
            block.getWorld().playEffect(block.getLocation(), org.bukkit.Effect.STEP_SOUND, block.getType());
            block.breakNaturally();
        }
    }

    private static List<String> lore() {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Instant Break I");
        lore.add(ChatColor.DARK_GRAY + "An all-in-one tool that breaks any block instantly!");
        return lore;
    }

    private static Map<Enchantment, Integer> enchantments() {
        Map<Enchantment, Integer> enchants = new HashMap<>();
        enchants.put(Enchantment.SHARPNESS, 5);
        enchants.put(Enchantment.SMITE, 5);
        enchants.put(Enchantment.LOOTING, 5);
        return enchants;
    }
}
