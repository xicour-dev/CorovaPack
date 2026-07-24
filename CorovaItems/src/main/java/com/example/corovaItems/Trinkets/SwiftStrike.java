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
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
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

public class SwiftStrike extends CorovaItems implements Listener {

    private static final int MAX_DURABILITY = 1800;
    public static NamespacedKey MODIFIER_KEY;
    private static NamespacedKey DURABILITY_KEY;
    private static NamespacedKey HAS_SWIFT_STRIKE_KEY;
    private static boolean taskStarted = false;

    public SwiftStrike() {
        super(ChatColor.WHITE + "Swift Strike", Material.FEATHER, lore(), null, "swiftstrike");

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(SwiftStrike.class);

        if (MODIFIER_KEY == null) {
            MODIFIER_KEY = new NamespacedKey(plugin, "swiftstrike_speed_boost");
        }

        if (DURABILITY_KEY == null) {
            DURABILITY_KEY = new NamespacedKey(plugin, "swiftstrike_durability");
        }

        if (HAS_SWIFT_STRIKE_KEY == null) {
            HAS_SWIFT_STRIKE_KEY = new NamespacedKey("corovaitems", "has_swift_strike");
        }

        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(
                ChatColor.GRAY + "Trinket",
                ChatColor.GRAY + "+40% Attack speed!",
                ChatColor.GRAY + "Does not work on hoes or scythes."
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

            List<String> itemLore = new ArrayList<>(meta.getLore() != null ? meta.getLore() : new ArrayList<>());
            itemLore.add(ChatColor.DARK_GRAY + "UUID: " + java.util.UUID.randomUUID());
            itemLore.add(ChatColor.GRAY + "Durability: 0/" + MAX_DURABILITY);
            meta.setLore(itemLore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static void checkTrinket(LivingEntity entity) {
        if (entity == null) {
            return;
        }

        AttributeInstance attr = entity.getAttribute(Attribute.ATTACK_SPEED);
        if (attr == null) {
            return;
        }

        // Remove our modifier if it exists
        attr.removeModifier(MODIFIER_KEY);

        if (entity.isDead() || (entity instanceof Player p && p.getGameMode() == GameMode.CREATIVE)) {
            if (!(entity instanceof Player)) {
                entity.getPersistentDataContainer().remove(HAS_SWIFT_STRIKE_KEY);
            }
            return;
        }

        // Equipment check
        boolean hasTrinket = false;
        ItemStack[] contents = TrinketUtils.getTrinketItems(entity);
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) continue;
            if (ItemManager.getInstance().isCorovaItem(item, ItemManager.getInstance().getItemById("swiftstrike"))) {
                hasTrinket = true;
                break;
            }
        }

        if (!(entity instanceof Player)) {
            if (hasTrinket && !isExcluded(entity)) {
                entity.getPersistentDataContainer().set(HAS_SWIFT_STRIKE_KEY, PersistentDataType.BYTE, (byte) 1);
            } else {
                entity.getPersistentDataContainer().remove(HAS_SWIFT_STRIKE_KEY);
            }
        }

        if (!hasTrinket) {
            return;
        }

        if (isExcluded(entity)) {
            return;
        }

        if (entity instanceof Player) {
            double scaling = TrinketUtils.getCachedScaling(entity, contents);
            if (scaling <= 0) return;

            // multiplier = 0.4 means +40%
            double multiplier = 0.4 * scaling;

            AttributeModifier modifier = new AttributeModifier(
                    MODIFIER_KEY,
                    multiplier,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                    EquipmentSlotGroup.ANY
            );

            attr.addModifier(modifier);
        }
    }

    private static boolean isExcluded(LivingEntity entity) {
        if (entity.getEquipment() == null) return false;
        ItemStack mainHand = entity.getEquipment().getItemInMainHand();

        // Fists are not excluded
        if (mainHand == null || mainHand.getType().isAir()) {
            return false;
        }

        Material material = mainHand.getType();
        String name = material.name();

        // Exclude hoes
        if (name.endsWith("_HOE")) {
            return true;
        }

        // Exclude scythes via PDC tag
        if (mainHand.hasItemMeta()) {
            NamespacedKey scytheKey = new NamespacedKey("corovaitems", "is_scythe");
            if (mainHand.getItemMeta().getPersistentDataContainer().has(scytheKey, PersistentDataType.BYTE)) {
                return true;
            }
        }

        // Exclude scythes via ItemManager (backup)
        CorovaItems scytheItem = ItemManager.getInstance().getItemById("scythe");
        if (scytheItem != null && ItemManager.getInstance().isCorovaItem(mainHand, scytheItem)) {
            return true;
        }

        return false;
    }

    @EventHandler
    public void onEntityDealDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity entity)) return;
        if (entity instanceof Player p && p.getGameMode() == GameMode.CREATIVE) return;

        ItemStack trinket = findSwiftStrike(entity);
        if (trinket == null) return;

        ItemMeta meta = trinket.getItemMeta();
        if (meta == null) return;

        int currentDurability = meta.getPersistentDataContainer().getOrDefault(DURABILITY_KEY, PersistentDataType.INTEGER, 0);
        int damage = Math.max(1, (int) Math.round(event.getFinalDamage()));
        int newDurability = currentDurability + damage;

        if (newDurability >= MAX_DURABILITY) {
            trinket.setAmount(trinket.getAmount() - 1);
            if (entity instanceof Player player) {
                player.sendMessage(ChatColor.RED + "Your Swift Strike trinket has broken!");
            }
            entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);

            checkTrinket(entity);
            Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(SwiftStrike.class), () -> checkTrinket(entity), 1L);
        } else {
            meta.getPersistentDataContainer().set(DURABILITY_KEY, PersistentDataType.INTEGER, newDurability);
            updateLore(meta, newDurability);
            trinket.setItemMeta(meta);
        }
    }

    private void updateLore(ItemMeta meta, int durability) {
        List<String> lore = meta.getLore();
        if (lore != null) {
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).contains("Durability:")) {
                    lore.set(i, ChatColor.GRAY + "Durability: " + durability + "/" + MAX_DURABILITY);
                    break;
                }
            }
            meta.setLore(lore);
        }
    }

    private ItemStack findSwiftStrike(LivingEntity entity) {
        for (ItemStack item : TrinketUtils.getTrinketItems(entity)) {
            if (item == null || item.getType().isAir()) continue;
            if (ItemManager.getInstance().isCorovaItem(item, ItemManager.getInstance().getItemById("swiftstrike"))) {
                return item;
            }
        }
        return null;
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
            AttributeInstance attr = e.getPlayer().getAttribute(Attribute.ATTACK_SPEED);
            if (attr != null) {
                Collection<AttributeModifier> modifiers = new ArrayList<>(attr.getModifiers());
                for (AttributeModifier mod : modifiers) {
                    if (mod.getKey().equals(MODIFIER_KEY)) {
                        attr.removeModifier(mod);
                    }
                }
            }
        }
    }

    private static void scheduleCheck(org.bukkit.entity.Entity entity) {
        if (!(entity instanceof LivingEntity le)) return;
        checkTrinket(le);
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(SwiftStrike.class), () -> checkTrinket(le), 1L);
    }
}