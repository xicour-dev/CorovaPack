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
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Compactor extends CorovaItems implements Listener {

    private static final NamespacedKey MODIFIER_KEY = new NamespacedKey("corova", "compactor_scale");

    public Compactor() {
        super(ChatColor.GRAY + "Compactor", Material.IRON_NUGGET, lore(), null, "compactor");
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(
                ChatColor.GRAY + "Trinket",
                ChatColor.GRAY + "Makes you smaller!",
                ChatColor.GRAY + "Scale: 0.6x"
        );
    }

    @Override
    public ItemStack toItemStack() {
        ItemStack item = super.toItemStack();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            List<String> itemLore = new ArrayList<>(meta.getLore() != null ? meta.getLore() : new ArrayList<>());
            itemLore.add(ChatColor.DARK_GRAY + "UUID: " + UUID.randomUUID());
            meta.setLore(itemLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static void checkTrinket(LivingEntity entity) {
        if (entity == null) return;

        AttributeInstance attr = entity.getAttribute(Attribute.SCALE);
        if (attr == null) return;

        // Remove our modifier if it exists
        attr.removeModifier(MODIFIER_KEY);

        if (entity.isDead() || (entity instanceof Player p && p.getGameMode() == GameMode.CREATIVE)) {
            return;
        }

        ItemStack[] contents = TrinketUtils.getTrinketItems(entity);
        boolean hasTrinket = false;
        for (ItemStack item : contents) {
            if (item == null) continue;
            if (ItemManager.getInstance().isCorovaItem(item, ItemManager.getInstance().getItemById("compactor"))) {
                hasTrinket = true;
                break;
            }
        }

        if (!hasTrinket) return;

        double scaling = TrinketUtils.getCachedScaling(entity, contents);
        if (scaling <= 0) return;

        // Base scale is 1.0. We want 0.6. So we add -0.4.
        // We multiply the reduction by the trinket scaling factor.
        double reduction = -0.4 * scaling;

        AttributeModifier modifier = new AttributeModifier(
                MODIFIER_KEY,
                reduction,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.ANY
        );

        attr.addModifier(modifier);
    }

    @EventHandler public void onInventoryClick(InventoryClickEvent e) { scheduleCheck(e.getWhoClicked()); }
    @EventHandler public void onInventoryClose(InventoryCloseEvent e) { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onHandSwap(PlayerSwapHandItemsEvent e) { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onItemHeld(PlayerItemHeldEvent e) { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onJoin(PlayerJoinEvent e) { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent e) { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onDrop(PlayerDropItemEvent e) { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onPickup(EntityPickupItemEvent e) { if (e.getEntity() instanceof LivingEntity le) scheduleCheck(le); }
    @EventHandler public void onGameModeChange(PlayerGameModeChangeEvent e) { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onDeath(PlayerDeathEvent e) { scheduleCheck(e.getEntity()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        if (e.getPlayer() != null) {
            TrinketUtils.removeEntity(e.getPlayer());
            AttributeInstance attr = e.getPlayer().getAttribute(Attribute.SCALE);
            if (attr != null) {
                attr.removeModifier(MODIFIER_KEY);
            }
        }
    }

    private static void scheduleCheck(org.bukkit.entity.Entity entity) {
        if (!(entity instanceof LivingEntity le)) return;
        checkTrinket(le);
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(Compactor.class), () -> checkTrinket(le), 1L);
    }
}