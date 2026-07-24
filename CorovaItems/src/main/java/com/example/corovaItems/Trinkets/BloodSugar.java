package com.example.corovaItems.Trinkets;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

public class BloodSugar extends CorovaItems implements Listener {

    private static final double BONUS_HEALTH = 10.0;
    private static final int MAX_DURABILITY = 1800;
    private static final NamespacedKey MODIFIER_KEY   = new NamespacedKey("corova", "bloodsugar_health");
    private static final NamespacedKey DURABILITY_KEY = new NamespacedKey("corova", "bloodsugar_durability");

    public BloodSugar() {
        super(
                ChatColor.RED + "Blood Sugar",
                Material.REDSTONE,
                lore(),
                null,
                "bloodsugar"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(
                ChatColor.GRAY + "Trinket",
                ChatColor.GRAY + "Increases max HP by " + (int) BONUS_HEALTH + "!"
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

    /**
     * Applies or removes the BloodSugar health bonus using an AttributeModifier.
     *
     * Using ADD_NUMBER on the modifier means we never touch the entity's base value,
     * so the tier health bonus set by VanillaMobBuffs is always preserved.
     * Mobs (non-players) are healed to their new maximum only on first application
     * so they spawn at full health. Players are never healed by this method.
     */
    public static void checkTrinket(LivingEntity entity) {
        if (entity == null || entity.isDead()) return;

        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        if (health == null) return;

        // Check whether the modifier was already present before removing it,
        // so we know whether this is a fresh application or a re-check.
        boolean wasAlreadyApplied = health.getModifiers().stream()
                .anyMatch(m -> m.getKey().equals(MODIFIER_KEY));

        // Always remove the old modifier first so we can re-apply cleanly.
        health.removeModifier(MODIFIER_KEY);

        ItemStack[] items = TrinketUtils.getTrinketItems(entity);
        boolean hasTrinket = false;
        for (ItemStack item : items) {
            if (item == null) continue;
            if (ItemManager.getInstance().isCorovaItem(item, ItemManager.getInstance().getItemById("bloodsugar"))) {
                hasTrinket = true;
                break;
            }
        }

        if (hasTrinket) {
            double scaling = TrinketUtils.getCachedScaling(entity, items);
            AttributeModifier modifier = new AttributeModifier(
                    MODIFIER_KEY,
                    BONUS_HEALTH * scaling,
                    AttributeModifier.Operation.ADD_NUMBER,
                    EquipmentSlotGroup.ANY
            );
            health.addModifier(modifier);

            // Only heal mobs (never players) to their new max, and only on first
            // application so they spawn at full health without getting free heals.
            if (!wasAlreadyApplied && !(entity instanceof Player)) {
                entity.setHealth(health.getValue());
            }
        }

        // Clamp current health downward if we just removed the modifier and the
        // entity's HP is now above their reduced maximum.
        if (entity.getHealth() > health.getValue()) {
            entity.setHealth(Math.max(1.0, health.getValue()));
        }
    }

    // -----------------------------------------------------------------------
    // Durability: tracks total damage taken while carrying the trinket.
    // -----------------------------------------------------------------------

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (entity instanceof Player player && player.getGameMode() == org.bukkit.GameMode.CREATIVE) return;

        ItemStack bloodSugar = findBloodSugar(entity);
        if (bloodSugar == null) return;

        ItemMeta meta = bloodSugar.getItemMeta();
        if (meta == null) return;

        int damageTaken = meta.getPersistentDataContainer()
                .getOrDefault(DURABILITY_KEY, PersistentDataType.INTEGER, 0);
        damageTaken += (int) Math.round(event.getDamage());

        if (damageTaken >= MAX_DURABILITY) {
            bloodSugar.setAmount(0);
            if (entity instanceof Player player) {
                player.sendMessage(ChatColor.RED + "Your Blood Sugar has broken!");
            }
            entity.getWorld().playSound(entity.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1f, 1f);

            Bukkit.getScheduler().runTaskLater(
                    JavaPlugin.getProvidingPlugin(BloodSugar.class),
                    () -> checkTrinket(entity), 1L);
        } else {
            meta.getPersistentDataContainer().set(DURABILITY_KEY, PersistentDataType.INTEGER, damageTaken);
            updateLore(meta, damageTaken);
            bloodSugar.setItemMeta(meta);
        }
    }

    private ItemStack findBloodSugar(LivingEntity entity) {
        for (ItemStack item : TrinketUtils.getTrinketItems(entity)) {
            if (item == null) continue;
            if (ItemManager.getInstance().isCorovaItem(item, ItemManager.getInstance().getItemById("bloodsugar"))) {
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

    // -----------------------------------------------------------------------
    // Inventory / session watchers
    // -----------------------------------------------------------------------

    @EventHandler public void onInventoryClick(InventoryClickEvent e)  { scheduleCheck(e.getWhoClicked()); }
    @EventHandler public void onInventoryClose(InventoryCloseEvent e)  { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onHandSwap(PlayerSwapHandItemsEvent e)   { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onItemHeld(PlayerItemHeldEvent e)        { scheduleCheck(e.getPlayer()); }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(BloodSugar.class),
                () -> checkTrinket(e.getPlayer()), 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (e.getPlayer() != null) TrinketUtils.removeEntity(e.getPlayer());
    }

    private static void scheduleCheck(org.bukkit.entity.HumanEntity entity) {
        if (!(entity instanceof LivingEntity le)) return;
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(BloodSugar.class),
                () -> checkTrinket(le), 1L);
    }
}