package com.example.corovaItems.Trinkets;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class SpiderEyeTotem extends CorovaItems implements Listener {

    private static final int MAX_DURABILITY = 100;
    private static final NamespacedKey DURABILITY_KEY =
            new NamespacedKey("corova", "spidereyetotem_durability");

    public SpiderEyeTotem() {
        super(
                ChatColor.DARK_GREEN + "Spider Eye Totem",
                Material.SPIDER_EYE,
                lore(),
                null,
                "spidereyetotem"
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(
                ChatColor.GRAY + "Trinket",
                ChatColor.GRAY + "Provides poison resistance!"
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
     * No persistent attribute modifier is needed for this trinket — the effect
     * is applied event-by-event in {@link #onPoisonDamage}.
     *
     * FIX: The original implementation called {@code TrinketUtils.getCachedScaling()}
     * here and discarded the return value, which served no purpose other than
     * wasting a HashMap lookup (and, when called outside a checkEntity() pass,
     * triggering a full inventory scan via {@code applyTrinketScaling}).
     * This method is now a true no-op, matching Backpack and EnchantedQuiver.
     */
    public static void checkTrinket(LivingEntity entity) {
        // Intentionally empty: damage resistance is applied on-hit, not via an attribute.
    }

    @EventHandler
    public void onPoisonDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.POISON) return;
        if (entity instanceof Player player && player.getGameMode() == GameMode.CREATIVE) return;

        ItemStack totem = findTotem(entity);
        if (totem == null) return;

        ItemStack[] items = TrinketUtils.getTrinketItems(entity);
        double scaling = TrinketUtils.applyTrinketScaling(entity, items);
        if (scaling <= 0) return;

        double damage = event.getDamage();
        event.setDamage(damage * (1.0 - scaling));
        if (scaling >= 1.0) {
            event.setCancelled(true);
        }

        ItemMeta meta = totem.getItemMeta();
        if (meta == null) return;

        int instances = meta.getPersistentDataContainer()
                .getOrDefault(DURABILITY_KEY, PersistentDataType.INTEGER, 0) + 1;

        if (instances >= MAX_DURABILITY) {
            totem.setAmount(0);
            if (entity instanceof Player player) {
                player.sendMessage(ChatColor.RED + "Your Spider Eye Totem has broken!");
            }
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
        } else {
            meta.getPersistentDataContainer().set(DURABILITY_KEY, PersistentDataType.INTEGER, instances);
            updateLore(meta, instances);
            totem.setItemMeta(meta);
        }
    }

    private ItemStack findTotem(LivingEntity entity) {
        for (ItemStack item : TrinketUtils.getTrinketItems(entity)) {
            if (item == null) continue;
            if (ItemManager.getInstance().isCorovaItem(item,
                    ItemManager.getInstance().getItemById("spidereyetotem"))) {
                return item;
            }
        }
        return null;
    }

    private void updateLore(ItemMeta meta, int instances) {
        List<String> lore = meta.getLore();
        if (lore != null) {
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).contains("Durability")) {
                    lore.set(i, ChatColor.GRAY + "Durability: " + instances + "/" + MAX_DURABILITY);
                    break;
                }
            }
            meta.setLore(lore);
        }
    }

    @EventHandler public void onInventoryClick(InventoryClickEvent e) { scheduleCheck(e.getWhoClicked()); }
    @EventHandler public void onInventoryClose(InventoryCloseEvent e) { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onHandSwap(PlayerSwapHandItemsEvent e)  { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onItemHeld(PlayerItemHeldEvent e)       { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onJoin(PlayerJoinEvent e)               { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent e){ scheduleCheck(e.getPlayer()); }
    @EventHandler public void onDrop(PlayerDropItemEvent e)           { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof LivingEntity le) scheduleCheck(le);
    }
    @EventHandler public void onGameModeChange(PlayerGameModeChangeEvent e) { scheduleCheck(e.getPlayer()); }
    @EventHandler public void onDeath(PlayerDeathEvent e)             { scheduleCheck(e.getEntity()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        if (e.getPlayer() != null) TrinketUtils.removeEntity(e.getPlayer());
    }

    private static void scheduleCheck(org.bukkit.entity.Entity entity) {
        if (!(entity instanceof LivingEntity le)) return;
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(SpiderEyeTotem.class),
                () -> checkTrinket(le), 1L);
    }
}