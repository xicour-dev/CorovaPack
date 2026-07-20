package com.example.corovaItems.Trinkets;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class MinersMight extends CorovaItems implements Listener {

    private static final double MINING_SPEED_BASE = 2.0;
    private static final int BLOCKS_PER_DURABILITY = 1;
    private static final int MAX_DURABILITY = 3000;

    private static final NamespacedKey COUNTER_KEY    = new NamespacedKey("corova", "minersmight_counter");
    private static final NamespacedKey DURABILITY_KEY = new NamespacedKey("corova", "minersmight_durability");

    public MinersMight() {
        super(ChatColor.GOLD + "Miner's Might", Material.matchMaterial("COPPER_PICKAXE"), lore(), null, "minersmight");
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(
                ChatColor.GRAY + "Trinket",
                ChatColor.GRAY + "Doubles mining speed!",
                ChatColor.GRAY + "Stacks with haste!"
        );
    }

    @Override
    public ItemStack toItemStack() {
        ItemStack item = super.toItemStack();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            meta.getPersistentDataContainer().set(COUNTER_KEY, PersistentDataType.INTEGER, 0);
            meta.getPersistentDataContainer().set(DURABILITY_KEY, PersistentDataType.INTEGER, 0);

            List<String> lore = meta.getLore() != null ? meta.getLore() : new ArrayList<>();
            lore.add(ChatColor.DARK_GRAY + "UUID: " + java.util.UUID.randomUUID());
            lore.add(ChatColor.GRAY + "Durability: 0/" + MAX_DURABILITY);
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        ItemStack minersMight = findMinersMight(player);
        if (minersMight == null) return;

        ItemMeta meta = minersMight.getItemMeta();
        if (meta == null) return;

        // Increment the per-block counter
        int count = meta.getPersistentDataContainer()
                .getOrDefault(COUNTER_KEY, PersistentDataType.INTEGER, 0) + 1;

        if (count >= BLOCKS_PER_DURABILITY) {
            // Reset block counter and apply 1 durability damage
            meta.getPersistentDataContainer().set(COUNTER_KEY, PersistentDataType.INTEGER, 0);

            int blocksDamaged = meta.getPersistentDataContainer()
                    .getOrDefault(DURABILITY_KEY, PersistentDataType.INTEGER, 0) + 1;

            if (blocksDamaged >= MAX_DURABILITY) {
                // Break the trinket
                minersMight.setAmount(0);
                player.sendMessage(ChatColor.RED + "Your Miner's Might has broken!");
                player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                scheduleCheck(player);
            } else {
                meta.getPersistentDataContainer().set(DURABILITY_KEY, PersistentDataType.INTEGER, blocksDamaged);
                updateLore(meta, blocksDamaged);
                minersMight.setItemMeta(meta);
            }
        } else {
            meta.getPersistentDataContainer().set(COUNTER_KEY, PersistentDataType.INTEGER, count);
            minersMight.setItemMeta(meta);
        }
    }

    private void updateLore(ItemMeta meta, int blocksDamaged) {
        List<String> lore = meta.getLore();
        if (lore != null) {
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).contains("Durability")) {
                    lore.set(i, ChatColor.GRAY + "Durability: " + blocksDamaged + "/" + MAX_DURABILITY);
                    break;
                }
            }
            meta.setLore(lore);
        }
    }

    private ItemStack findMinersMight(LivingEntity entity) {
        for (ItemStack item : TrinketUtils.getTrinketItems(entity)) {
            if (item == null) continue;
            if (ItemManager.getInstance().isCorovaItem(item, ItemManager.getInstance().getItemById("minersmight"))) {
                return item;
            }
        }
        return null;
    }

    public static void checkTrinket(LivingEntity entity) {
        ItemStack[] items = TrinketUtils.getTrinketItems(entity);
        double scaling = TrinketUtils.getCachedScaling(entity, items);

        boolean hasTrinket = false;
        for (ItemStack item : items) {
            if (item == null) continue;
            if (ItemManager.getInstance().isCorovaItem(item, ItemManager.getInstance().getItemById("minersmight"))) {
                hasTrinket = true;
                break;
            }
        }

        AttributeInstance breakSpeed = entity.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (breakSpeed == null) return;

        if (hasTrinket) {
            breakSpeed.setBaseValue(MINING_SPEED_BASE * scaling);
        } else {
            breakSpeed.setBaseValue(1.0);
        }
    }

    // --- Inventory & player watchers ---
    @EventHandler public void onInventoryClick(InventoryClickEvent e) { scheduleCheck(e.getWhoClicked()); }
    @EventHandler public void onInventoryClose(InventoryCloseEvent e) { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onHandSwap(PlayerSwapHandItemsEvent e)  { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onItemHeld(PlayerItemHeldEvent e)       { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onJoin(PlayerJoinEvent e)               { scheduleCheck(e.getPlayer()); }

    private static void scheduleCheck(LivingEntity entity) {
        if (entity == null) return;
        if (entity instanceof Player p && p.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(MinersMight.class),
                () -> checkTrinket(entity), 1L);
    }
}