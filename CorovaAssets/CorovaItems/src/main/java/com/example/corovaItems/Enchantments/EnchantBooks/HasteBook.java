package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.ItemManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class HasteBook extends EnchantmentBook implements Listener {

    private static final long EFFECT_DURATION_MS = 30_000L;
    private static final long COOLDOWN_DURATION_MS = 60_000L;
    private static final double MINING_SPEED_BONUS = 1.0;
    private static final String MODIFIER_NAME = "corova_haste_bonus";

    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> activeEffects = new HashMap<>();

    public HasteBook() {
        this(1);
    }

    public HasteBook(int level) {
        super(
                "Book of Haste",
                CorovaEnchantments.HASTE_ID,
                level,
                "book_haste",
                allowedMaterialsStatic()
        );
        ItemManager.getInstance().registerItem(this);
    }

    private static Set<Material> allowedMaterialsStatic() {
        return Set.of(
                Material.WOODEN_PICKAXE,
                Material.STONE_PICKAXE,
                Material.IRON_PICKAXE,
                Material.GOLDEN_PICKAXE,
                Material.DIAMOND_PICKAXE,
                Material.NETHERITE_PICKAXE
        );
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        // PlayerInteractEvent fires once per hand — ignore the off-hand to prevent double triggers
        if (event.getHand() != EquipmentSlot.HAND) return;

        if (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (!CorovaEnchantments.hasEnchant(hand, CorovaEnchantments.HASTE_ID)) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Already active — tell the player how long is left
        Long activeUntil = activeEffects.get(uuid);
        if (activeUntil != null && activeUntil > now) {
            long remaining = (activeUntil - now) / 1000 + 1;
            String enchantName = EnchantmentBook.applyEnchantmentGradient(CorovaEnchantments.HASTE_ID, CorovaEnchantments.DISPLAY_NAME.getOrDefault(CorovaEnchantments.HASTE_ID, "Haste"));
            player.sendMessage(enchantName + ChatColor.YELLOW + " is already active for " + remaining + " more second(s)!");
            return;
        }

        // On cooldown
        Long expiration = cooldowns.get(uuid);
        if (expiration != null && expiration > now) {
            long remaining = (expiration - now) / 1000 + 1;
            String enchantName = EnchantmentBook.applyEnchantmentGradient(CorovaEnchantments.HASTE_ID, CorovaEnchantments.DISPLAY_NAME.getOrDefault(CorovaEnchantments.HASTE_ID, "Haste"));
            player.sendMessage(enchantName + ChatColor.RED + " is on cooldown for " + remaining + " more second(s)!");
            return;
        }

        // Apply the mining speed attribute modifier
        applyHaste(player);

        // Schedule removal after 30 seconds
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(this.getClass());
        new BukkitRunnable() {
            @Override
            public void run() {
                removeHaste(player);
                cooldowns.put(uuid, System.currentTimeMillis() + COOLDOWN_DURATION_MS);
                activeEffects.remove(uuid);
                if (player.isOnline()) {
                    String enchantName = EnchantmentBook.applyEnchantmentGradient(CorovaEnchantments.HASTE_ID, CorovaEnchantments.DISPLAY_NAME.getOrDefault(CorovaEnchantments.HASTE_ID, "Haste"));
                    player.sendMessage(enchantName + ChatColor.GRAY + " has worn off. Cooldown: 60 seconds.");
                }
            }
        }.runTaskLater(plugin, EFFECT_DURATION_MS / 50); // ticks

        activeEffects.put(uuid, now + EFFECT_DURATION_MS);
        String enchantName = EnchantmentBook.applyEnchantmentGradient(CorovaEnchantments.HASTE_ID, CorovaEnchantments.DISPLAY_NAME.getOrDefault(CorovaEnchantments.HASTE_ID, "Haste"));
        player.sendMessage(enchantName + ChatColor.YELLOW + " activated! +1 Mining Speed for 30 seconds.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        removeHaste(event.getPlayer());
        cooldowns.remove(uuid);
        activeEffects.remove(uuid);
    }

    private void applyHaste(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (attr == null) return;

        // Remove any existing modifier from us first to avoid stacking
        attr.getModifiers().stream()
                .filter(m -> m.getName().equals(MODIFIER_NAME))
                .forEach(attr::removeModifier);

        AttributeModifier modifier = new AttributeModifier(
                new org.bukkit.NamespacedKey("corovaitems", MODIFIER_NAME),
                MINING_SPEED_BONUS,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlotGroup.ANY
        );
        attr.addModifier(modifier);
    }

    private void removeHaste(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.BLOCK_BREAK_SPEED);
        if (attr == null) return;
        attr.getModifiers().stream()
                .filter(m -> m.getName().equals(MODIFIER_NAME))
                .forEach(attr::removeModifier);
    }
}