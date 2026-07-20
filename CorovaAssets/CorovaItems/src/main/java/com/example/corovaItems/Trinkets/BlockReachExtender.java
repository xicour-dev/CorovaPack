package com.example.corovaItems.Trinkets;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class BlockReachExtender extends CorovaItems implements Listener {

    private static final double BLOCK_REACH_BONUS = 3.0;
    private static final int MAX_DURABILITY = 800;
    private static final NamespacedKey MODIFIER_KEY = new NamespacedKey("corova", "block_reach_extender");
    private static final NamespacedKey DURABILITY_KEY = new NamespacedKey("corova", "blockreachextender_durability");

    public BlockReachExtender() {
        super(
                ChatColor.LIGHT_PURPLE + "Block Reach Extender",
                Material.ARMOR_STAND,
                lore(),
                null,
                "blockreachextender"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(
                ChatColor.GRAY + "Trinket",
                ChatColor.GRAY + "Increases block reach by 3 blocks!",
                ChatColor.GRAY + "Works for breaking and placing!"
        );
    }

    @Override
    public ItemStack toItemStack() {
        ItemStack item = super.toItemStack();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            meta.getPersistentDataContainer().set(DURABILITY_KEY, PersistentDataType.INTEGER, 0);

            List<String> lore = meta.getLore() != null ? meta.getLore() : new ArrayList<>();
            lore.add(ChatColor.DARK_GRAY + "UUID: " + java.util.UUID.randomUUID());
            lore.add(ChatColor.GRAY + "Durability: 0/" + MAX_DURABILITY);
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    public static void checkTrinket(LivingEntity entity) {
        if (entity == null || entity.isDead()) {
            return;
        }

        AttributeInstance blockReach = entity.getAttribute(Attribute.BLOCK_INTERACTION_RANGE);
        if (blockReach == null) return;

        // Remove any existing modifier
        blockReach.removeModifier(MODIFIER_KEY);

        ItemStack[] items = TrinketUtils.getTrinketItems(entity);
        boolean hasTrinket = false;
        for (ItemStack item : items) {
            if (item == null) continue;
            if (ItemManager.getInstance().isCorovaItem(item, ItemManager.getInstance().getItemById("blockreachextender"))) {
                hasTrinket = true;
                break;
            }
        }

        if (hasTrinket) {
            double scaling = TrinketUtils.getCachedScaling(entity, items);
            AttributeModifier modifier = new AttributeModifier(
                    MODIFIER_KEY,
                    BLOCK_REACH_BONUS * scaling,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.ANY
            );
            blockReach.addModifier(modifier);
        }
    }

    // --- Durability Handling ---
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        updateDurability(event.getPlayer());
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        updateDurability(event.getPlayer());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null) {
            updateDurability(event.getPlayer());
        }
    }

    private void updateDurability(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE) return;

        ItemStack blockReachExtender = findBlockReachExtender(player);
        if (blockReachExtender == null) return;

        ItemMeta meta = blockReachExtender.getItemMeta();
        if (meta == null) return;

        int interactions = meta.getPersistentDataContainer().getOrDefault(DURABILITY_KEY, PersistentDataType.INTEGER, 0);
        interactions += 1;

        if (interactions >= MAX_DURABILITY) {
            blockReachExtender.setAmount(0);
            player.sendMessage(ChatColor.RED + "Your Block Reach Extender has broken!");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);

            Bukkit.getScheduler().runTaskLater(
                    JavaPlugin.getProvidingPlugin(BlockReachExtender.class),
                    () -> checkTrinket(player),
                    1L
            );
        } else {
            meta.getPersistentDataContainer().set(DURABILITY_KEY, PersistentDataType.INTEGER, interactions);
            updateLore(meta, interactions);
            blockReachExtender.setItemMeta(meta);
        }
    }

    private ItemStack findBlockReachExtender(LivingEntity entity) {
        for (ItemStack item : TrinketUtils.getTrinketItems(entity)) {
            if (item == null) continue;
            if (ItemManager.getInstance().isCorovaItem(item, ItemManager.getInstance().getItemById("blockreachextender"))) {
                return item;
            }
        }
        return null;
    }

    private void updateLore(ItemMeta meta, int interactions) {
        List<String> lore = meta.getLore();
        if (lore != null) {
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).contains("Durability")) {
                    lore.set(i, ChatColor.GRAY + "Durability: " + interactions + "/" + MAX_DURABILITY);
                    break;
                }
            }
            meta.setLore(lore);
        }
    }

    // --- Inventory & player watchers ---
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        scheduleCheck(e.getWhoClicked());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        scheduleCheck(e.getPlayer());
    }

    @EventHandler
    public void onHandSwap(PlayerSwapHandItemsEvent e) {
        scheduleCheck(e.getPlayer());
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent e) {
        scheduleCheck(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        scheduleCheck(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (e.getPlayer() != null) {
            TrinketUtils.removeEntity(e.getPlayer());
        }
    }

    private static void scheduleCheck(org.bukkit.entity.HumanEntity entity) {
        if (!(entity instanceof LivingEntity le)) return;
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(BlockReachExtender.class),
                () -> checkTrinket(le), 1L);
    }
}