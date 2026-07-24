//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.example.corovaItems.ItemProperties;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;

public class SilkTouchSpawners implements Listener {
    @EventHandler(
            priority = EventPriority.HIGH,
            ignoreCancelled = true
    )
    public void onSpawnerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.SPAWNER) {
            ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
            if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
                event.setDropItems(false);
                event.setExpToDrop(0);
                CreatureSpawner spawnerState = (CreatureSpawner)block.getState();
                EntityType spawnedType = spawnerState.getSpawnedType();
                ItemStack spawnerDrop = new ItemStack(Material.SPAWNER);
                BlockStateMeta meta = (BlockStateMeta)spawnerDrop.getItemMeta();
                if (meta != null) {
                    CreatureSpawner metaSpawner = (CreatureSpawner)meta.getBlockState();
                    metaSpawner.setSpawnedType(spawnedType != null ? spawnedType : EntityType.PIG);
                    meta.setBlockState(metaSpawner);
                    spawnerDrop.setItemMeta(meta);
                }

                block.getWorld().dropItemNaturally(block.getLocation(), spawnerDrop);
            }
        }
    }

    @EventHandler(
            priority = EventPriority.HIGH,
            ignoreCancelled = true
    )
    public void onSpawnerPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() == Material.SPAWNER) {
            ItemStack item = event.getItemInHand();
            ItemMeta var5 = item.getItemMeta();
            if (var5 instanceof BlockStateMeta) {
                BlockStateMeta meta = (BlockStateMeta)var5;
                BlockState var6 = meta.getBlockState();
                if (var6 instanceof CreatureSpawner) {
                    CreatureSpawner itemSpawner = (CreatureSpawner)var6;
                    CreatureSpawner worldSpawner = (CreatureSpawner)block.getState();
                    worldSpawner.setSpawnedType(itemSpawner.getSpawnedType());
                    worldSpawner.update(true);
                }
            }
        }
    }
}
