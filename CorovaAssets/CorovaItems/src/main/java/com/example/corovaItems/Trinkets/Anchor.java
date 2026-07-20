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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
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

public class Anchor extends CorovaItems implements Listener {

    private static final double KNOCKBACK_RESISTANCE_BONUS = 0.2;
    private static final int MAX_DURABILITY = 500;
    private static final NamespacedKey MODIFIER_KEY = new NamespacedKey("corova", "steadfast_anchor");
    private static final NamespacedKey DURABILITY_KEY = new NamespacedKey("corova", "anchor_durability");

    public Anchor() {
        super(
                ChatColor.BLUE + "Anchor",
                Material.IRON_BLOCK,
                lore(),
                null,
                "steadfastanchor"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(
                ChatColor.GRAY + "Trinket",
                ChatColor.GRAY + "Provides 20% knockback resistance!"
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

        AttributeInstance kr = entity.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (kr == null) return;

        kr.removeModifier(MODIFIER_KEY);

        ItemStack[] items = TrinketUtils.getTrinketItems(entity);
        boolean hasTrinket = false;
        for (ItemStack item : items) {
            if (item == null) continue;
            if (ItemManager.getInstance().isCorovaItem(item, ItemManager.getInstance().getItemById("steadfastanchor"))) {
                hasTrinket = true;
                break;
            }
        }

        if (hasTrinket) {
            double scaling = TrinketUtils.getCachedScaling(entity, items);
            AttributeModifier modifier = new AttributeModifier(
                    MODIFIER_KEY,
                    KNOCKBACK_RESISTANCE_BONUS * scaling,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.ANY
            );
            kr.addModifier(modifier);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity instanceof Player player && player.getGameMode() == GameMode.CREATIVE) return;

        ItemStack anchor = findAnchor(entity);
        if (anchor == null) return;

        ItemMeta meta = anchor.getItemMeta();
        if (meta == null) return;

        int damageTaken = meta.getPersistentDataContainer().getOrDefault(DURABILITY_KEY, PersistentDataType.INTEGER, 0);
        damageTaken += (int) Math.round(event.getDamage());

        if (damageTaken >= MAX_DURABILITY) {
            anchor.setAmount(0);
            if (entity instanceof Player player) {
                player.sendMessage(ChatColor.RED + "Your Steadfast Anchor has broken!");
            }
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);

            Bukkit.getScheduler().runTaskLater(
                    JavaPlugin.getProvidingPlugin(Anchor.class),
                    () -> checkTrinket(entity),
                    1L
            );
        } else {
            meta.getPersistentDataContainer().set(DURABILITY_KEY, PersistentDataType.INTEGER, damageTaken);
            updateLore(meta, damageTaken);
            anchor.setItemMeta(meta);
        }
    }

    private ItemStack findAnchor(LivingEntity entity) {
        for (ItemStack item : TrinketUtils.getTrinketItems(entity)) {
            if (item == null) continue;
            if (ItemManager.getInstance().isCorovaItem(item, ItemManager.getInstance().getItemById("steadfastanchor"))) {
                return item;
            }
        }
        return null;
    }

    private void updateLore(ItemMeta meta, int damageTaken) {
        List<String> lore = meta.getLore();
        if (lore != null) {
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).contains("Durability")) {
                    lore.set(i, ChatColor.GRAY + "Durability: " + damageTaken + "/" + MAX_DURABILITY);
                    break;
                }
            }
            meta.setLore(lore);
        }
    }

    @EventHandler public void onInventoryClick(InventoryClickEvent e) { scheduleCheck(e.getWhoClicked()); }
    @EventHandler public void onInventoryClose(InventoryCloseEvent e) { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onHandSwap(PlayerSwapHandItemsEvent e) { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onItemHeld(PlayerItemHeldEvent e) { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onJoin(PlayerJoinEvent e) { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        if (e.getPlayer() != null) {
            TrinketUtils.removeEntity(e.getPlayer());
        }
    }

    private static void scheduleCheck(org.bukkit.entity.HumanEntity entity) {
        if (!(entity instanceof LivingEntity le)) return;
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(Anchor.class),
                () -> checkTrinket(le), 1L);
    }
}