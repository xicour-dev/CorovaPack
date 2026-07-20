package com.example.corovaItems.Trinkets;

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

public class BlazingPower extends CorovaItems implements Listener {

    private static final double ATTACK_DAMAGE_BONUS = 0.1;
    private static final int MAX_DURABILITY = 1800;
    // Using an AttributeModifier (ADD_SCALAR) so that it acts as a base attack multiplier.
    private static final NamespacedKey MODIFIER_KEY  = new NamespacedKey("corova", "blazingpower_damage");
    private static final NamespacedKey DURABILITY_KEY = new NamespacedKey("corova", "blazingpower_durability");

    public BlazingPower() {
        super(ChatColor.RED + "Blazing Power", Material.BLAZE_POWDER, lore(), null, "blazingpower");
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(
                ChatColor.GRAY + "Trinket",
                ChatColor.GRAY + "+10% attack damage!",
                ChatColor.GRAY + "Stacks with strength potions!"
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

    /**
     * Applies or removes the BlazingPower attack damage bonus using an AttributeModifier.
     *
     * Using ADD_NUMBER means the entity's base attack value (including any bonus
     * from VanillaMobBuffs tier scaling) is preserved.  We simply add or remove
     * the flat +2 on top of it.
     */
    public static void checkTrinket(LivingEntity entity) {
        if (entity == null || entity.isDead()) return;

        AttributeInstance attack = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attack == null) return;

        // Always remove old modifier first for a clean re-apply.
        attack.removeModifier(MODIFIER_KEY);

        ItemStack[] items = TrinketUtils.getTrinketItems(entity);
        boolean hasTrinket = false;
        for (ItemStack item : items) {
            if (item == null) continue;
            if (ItemManager.getInstance().isCorovaItem(item, ItemManager.getInstance().getItemById("blazingpower"))) {
                hasTrinket = true;
                break;
            }
        }

        if (hasTrinket) {
            double scaling = TrinketUtils.getCachedScaling(entity, items);
            AttributeModifier modifier = new AttributeModifier(
                    MODIFIER_KEY,
                    ATTACK_DAMAGE_BONUS * scaling,
                    AttributeModifier.Operation.ADD_SCALAR,
                    EquipmentSlotGroup.ANY
            );
            attack.addModifier(modifier);
        }
        // No else branch needed — removing the modifier already restores the original base.
    }

    // -----------------------------------------------------------------------
    // Durability: tracks total damage dealt while carrying the trinket.
    // -----------------------------------------------------------------------

    @EventHandler
    public void onEntityDealDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity entity)) return;
        if (entity instanceof Player player && player.getGameMode() == GameMode.CREATIVE) return;

        ItemStack blazingPower = findBlazingPower(entity);
        if (blazingPower == null) return;

        ItemMeta meta = blazingPower.getItemMeta();
        if (meta == null) return;

        int damageDealt = meta.getPersistentDataContainer()
                .getOrDefault(DURABILITY_KEY, PersistentDataType.INTEGER, 0);
        damageDealt += (int) Math.round(event.getFinalDamage());

        if (damageDealt >= MAX_DURABILITY) {
            blazingPower.setAmount(0);
            if (entity instanceof Player player) {
                player.sendMessage(ChatColor.RED + "Your Blazing Power has broken!");
            }
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);

            Bukkit.getScheduler().runTaskLater(
                    JavaPlugin.getProvidingPlugin(BlazingPower.class),
                    () -> checkTrinket(entity), 1L);
        } else {
            meta.getPersistentDataContainer().set(DURABILITY_KEY, PersistentDataType.INTEGER, damageDealt);
            updateLore(meta, damageDealt);
            blazingPower.setItemMeta(meta);
        }
    }

    private ItemStack findBlazingPower(LivingEntity entity) {
        for (ItemStack item : TrinketUtils.getTrinketItems(entity)) {
            if (item == null) continue;
            if (ItemManager.getInstance().isCorovaItem(item, ItemManager.getInstance().getItemById("blazingpower"))) {
                return item;
            }
        }
        return null;
    }

    private void updateLore(ItemMeta meta, int damageDealt) {
        List<String> lore = meta.getLore();
        if (lore != null) {
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).contains("Durability")) {
                    lore.set(i, ChatColor.GRAY + "Durability: " + damageDealt + "/" + MAX_DURABILITY);
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
    @EventHandler public void onJoin(PlayerJoinEvent e)                { scheduleCheck(e.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (e.getPlayer() != null) TrinketUtils.removeEntity(e.getPlayer());
    }

    private static void scheduleCheck(org.bukkit.entity.HumanEntity entity) {
        if (!(entity instanceof LivingEntity le)) return;
        if (le instanceof Player p && p.getGameMode() == GameMode.CREATIVE) return;
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(BlazingPower.class),
                () -> checkTrinket(le), 1L);
    }
}