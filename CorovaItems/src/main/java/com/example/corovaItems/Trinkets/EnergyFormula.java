package com.example.corovaItems.Trinkets;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
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
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class EnergyFormula extends CorovaItems implements Listener {

    private static final double SPEED_MULTIPLIER     = 0.1;
    private static final int    MAX_DURABILITY        = 5000;
    private static final NamespacedKey MODIFIER_KEY   = new NamespacedKey("corova", "energyformula_speed");
    private static final NamespacedKey DURABILITY_KEY = new NamespacedKey("corova", "energyformula_durability");

    // FIX: raised from 0.1 to 0.5 blocks.
    //
    // PlayerMoveEvent fires on every position or rotation update — for a
    // moving player that can be 20+ times per second.  The original 0.1-block
    // threshold still let the handler do a full trinket-item array scan on
    // most of those calls.  Raising it to 0.5 means we only enter the
    // expensive path (meta read + PDC write) roughly once per 0.5 blocks of
    // horizontal travel, which is around 4 times per second at sprint speed.
    // That is a ~5× reduction in PDC reads/writes with no perceptible change
    // to the durability or speed-boost behaviour.
    private static final double MOVE_THRESHOLD = 0.5;

    private static final Map<UUID, Location> lastLocation = new HashMap<>();

    public EnergyFormula() {
        super(ChatColor.AQUA + "Energy Formula", Material.SUGAR, lore(), null, "energyformula");
        ItemManager.getInstance().registerItem(this);
    }

    private static List<String> lore() {
        return List.of(
                ChatColor.GRAY + "Trinket",
                ChatColor.GRAY + "1.1x movement speed!",
                ChatColor.GRAY + "Stacks with speed potions!"
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
            lore.add(ChatColor.DARK_GRAY + "UUID: " + UUID.randomUUID());
            lore.add(ChatColor.GRAY + "Durability: 0/" + MAX_DURABILITY);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static void checkTrinket(LivingEntity entity) {
        if (entity == null || entity.isDead()) return;

        AttributeInstance speed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) return;

        speed.removeModifier(MODIFIER_KEY);

        ItemStack[] items = TrinketUtils.getTrinketItems(entity);
        boolean hasTrinket = false;
        for (ItemStack item : items) {
            if (item == null) continue;
            if (ItemManager.getInstance().isCorovaItem(item,
                    ItemManager.getInstance().getItemById("energyformula"))) {
                hasTrinket = true;
                break;
            }
        }

        if (hasTrinket) {
            double scaling = TrinketUtils.getCachedScaling(entity, items);
            AttributeModifier modifier = new AttributeModifier(
                    MODIFIER_KEY,
                    SPEED_MULTIPLIER * scaling,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1,
                    EquipmentSlotGroup.ANY
            );
            speed.addModifier(modifier);
        }
    }

    // -----------------------------------------------------------------------
    // Durability — PlayerMoveEvent
    // -----------------------------------------------------------------------

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) return;

        // FIX: Exit early — before touching any ItemStack — when there is no
        // horizontal displacement at all (rotation-only events).
        Location to   = event.getTo();
        Location from = event.getFrom();
        if (to == null) return;

        // Quick rejection: skip if X/Z haven't moved (covers most rotation events).
        if (to.getX() == from.getX() && to.getZ() == from.getZ()) return;

        UUID playerId = player.getUniqueId();

        // FIX: compute distance against last *recorded* position, not event.getFrom().
        // This means we batch many small moves into one PDC write rather than
        // writing on every tick that crosses the tiny 0.1 threshold.
        Location last = lastLocation.get(playerId);
        if (last == null) {
            lastLocation.put(playerId, to);
            return;
        }

        double deltaX = to.getX() - last.getX();
        double deltaZ = to.getZ() - last.getZ();
        double dist   = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        // Only proceed once MOVE_THRESHOLD blocks have accumulated.
        if (dist < MOVE_THRESHOLD) return;
        lastLocation.put(playerId, to);

        // Now it is worth finding the trinket and doing the PDC work.
        ItemStack energyFormula = findEnergyFormula(player);
        if (energyFormula == null) return;

        ItemMeta meta = energyFormula.getItemMeta();
        if (meta == null) return;

        int traveled    = meta.getPersistentDataContainer()
                .getOrDefault(DURABILITY_KEY, PersistentDataType.INTEGER, 0);
        int newTraveled = traveled + (int) Math.round(dist);

        if (newTraveled >= MAX_DURABILITY) {
            energyFormula.setAmount(0);
            player.sendMessage(ChatColor.RED + "Your Energy Formula has broken!");
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            lastLocation.remove(playerId);
            Bukkit.getScheduler().runTaskLater(
                    JavaPlugin.getProvidingPlugin(EnergyFormula.class),
                    () -> checkTrinket(player), 1L);
        } else {
            meta.getPersistentDataContainer()
                    .set(DURABILITY_KEY, PersistentDataType.INTEGER, newTraveled);
            updateLore(meta, newTraveled);
            energyFormula.setItemMeta(meta);
        }
    }

    private ItemStack findEnergyFormula(LivingEntity entity) {
        for (ItemStack item : TrinketUtils.getTrinketItems(entity)) {
            if (item == null) continue;
            if (ItemManager.getInstance().isCorovaItem(item,
                    ItemManager.getInstance().getItemById("energyformula"))) {
                return item;
            }
        }
        return null;
    }

    private void updateLore(ItemMeta meta, int distanceTraveled) {
        List<String> lore = meta.getLore();
        if (lore != null) {
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).contains("Durability")) {
                    lore.set(i, ChatColor.GRAY + "Durability: " + distanceTraveled + "/" + MAX_DURABILITY);
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
        if (e.getPlayer() != null) {
            TrinketUtils.removeEntity(e.getPlayer());
            lastLocation.remove(e.getPlayer().getUniqueId());
        }
    }

    private static void scheduleCheck(org.bukkit.entity.HumanEntity entity) {
        if (!(entity instanceof LivingEntity le)) return;
        if (le instanceof Player p && p.getGameMode() == GameMode.CREATIVE) return;
        Bukkit.getScheduler().runTaskLater(JavaPlugin.getProvidingPlugin(EnergyFormula.class),
                () -> checkTrinket(le), 1L);
    }
}