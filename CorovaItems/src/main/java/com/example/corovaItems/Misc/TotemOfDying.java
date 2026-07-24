package com.example.corovaItems.Misc;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class TotemOfDying extends CorovaItems implements Listener {

    // Totem of Dying provides an attack damage bonus when held in the offhand.
    // Compatible with both Players and LivingEntities (mobs).
    private static final double ATTACK_DAMAGE_BONUS = 6.0;
    private static final int MAX_DURABILITY = 500;
    private static final NamespacedKey DURABILITY_KEY = new NamespacedKey("corova", "totemofdying_durability");
    private static final NamespacedKey MODIFIER_KEY = new NamespacedKey("corova", "totem_of_dying_bonus");

    public TotemOfDying() {
        super(ChatColor.GOLD + "Totem of Dying", Material.END_CRYSTAL, lore(), null, "totemofdying");
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(ChatColor.GRAY + "Totem",
                ChatColor.GRAY + "+6 attack damage when held in offhand!",
                ChatColor.GRAY + "Uses durability on attack."
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

            List<String> itemLore = meta.getLore() != null ? meta.getLore() : new ArrayList<>();
            itemLore.add(ChatColor.DARK_GRAY + "UUID: " + java.util.UUID.randomUUID());
            itemLore.add(ChatColor.GRAY + "Durability: 0/" + MAX_DURABILITY);
            meta.setLore(itemLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static void checkTotem(LivingEntity entity) {
        if (entity == null || entity.isDead()) {
            return;
        }

        AttributeInstance attack = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attack == null) return;

        attack.removeModifier(MODIFIER_KEY);

        boolean hasTotem = false;
        if (entity.getEquipment() != null) {
            ItemStack offHand = entity.getEquipment().getItemInOffHand();
            if (offHand != null && ItemManager.getInstance().isCorovaItem(offHand, ItemManager.getInstance().getItemById("totemofdying"))) {
                hasTotem = true;
            }
        }

        if (hasTotem) {
            AttributeModifier modifier = new AttributeModifier(
                    MODIFIER_KEY,
                    ATTACK_DAMAGE_BONUS,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.OFFHAND
            );
            attack.addModifier(modifier);
        }
    }

    // --- Durability Handling ---
    @EventHandler
    public void onEntityDealDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity entity)) return;

        // Skip creative mode
        if (entity instanceof Player player && player.getGameMode() == GameMode.CREATIVE) return;

        // Check if entity has Totem of Dying in offhand
        ItemStack totemOfDying = findTotemOfDying(entity);
        if (totemOfDying == null) return;

        ItemMeta meta = totemOfDying.getItemMeta();
        if (meta == null) return;

        // Update total damage dealt
        int damageDealt = meta.getPersistentDataContainer().getOrDefault(DURABILITY_KEY, PersistentDataType.INTEGER, 0);
        damageDealt += (int) Math.round(event.getFinalDamage());

        // Check if the totem should break
        if (damageDealt >= MAX_DURABILITY) {
            totemOfDying.setAmount(0); // Break the item
            if (entity instanceof Player player) {
                player.sendMessage(ChatColor.RED + "Your Totem of Dying has broken!");
            }
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);

            // Trigger attribute recheck since totem is gone
            Bukkit.getScheduler().runTaskLater(
                    JavaPlugin.getProvidingPlugin(TotemOfDying.class),
                    () -> checkTotem(entity),
                    1L
            );
        } else {
            meta.getPersistentDataContainer().set(DURABILITY_KEY, PersistentDataType.INTEGER, damageDealt);
            updateLore(meta, damageDealt);
            totemOfDying.setItemMeta(meta);
        }
    }

    private ItemStack findTotemOfDying(LivingEntity entity) {
        if (entity.getEquipment() == null) return null;
        ItemStack offHand = entity.getEquipment().getItemInOffHand();
        if (offHand != null && ItemManager.getInstance().isCorovaItem(offHand, ItemManager.getInstance().getItemById("totemofdying"))) {
            return offHand;
        }
        return null;
    }

    private void updateLore(ItemMeta meta, int damageDealt) {
        List<String> lore = meta.getLore();
        if (lore != null) {
            // Find and update the durability line
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).contains("Durability")) {
                    lore.set(i, ChatColor.GRAY + "Durability: " + damageDealt + "/" + MAX_DURABILITY);
                    break;
                }
            }
            meta.setLore(lore);
        }
    }

    // Inventory watchers
    @EventHandler public void onInventoryClick(InventoryClickEvent e) { scheduleCheck(e.getWhoClicked()); }
    @EventHandler public void onInventoryClose(InventoryCloseEvent e) { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onHandSwap(PlayerSwapHandItemsEvent e) { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onItemHeld(PlayerItemHeldEvent e) { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onJoin(PlayerJoinEvent e) { scheduleCheck(e.getPlayer()); }

    @EventHandler
    public void onEntityPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof LivingEntity le) {
            scheduleCheck(le);
        }
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof LivingEntity le) {
            scheduleCheck(le);
        }
    }

    private static void scheduleCheck(org.bukkit.entity.Entity entity) {
        if (!(entity instanceof LivingEntity le)) return;
        if (le instanceof Player p && p.getGameMode() == org.bukkit.GameMode.CREATIVE) return;
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(TotemOfDying.class),
                () -> checkTotem(le), 1L);
    }
}
