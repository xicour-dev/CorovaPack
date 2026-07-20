package com.example.corovaItems.LOLPVP;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class SpellBook extends CorovaItems implements Listener {

    private final Map<UUID, Integer> mana = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final int MAX_MANA = 5;
    private static final long REGEN_DELAY_TICKS = 80L * 5; // 20 seconds for full regen
    private final NamespacedKey spellbookKey;
    private final JavaPlugin plugin;

    public SpellBook() {
        super(
                ChatColor.AQUA + "Spell Book",
                Material.BOOK,
                lore(),
                enchantments(),
                "spellbook"
        );
        this.plugin = JavaPlugin.getProvidingPlugin(SpellBook.class);
        this.spellbookKey = new NamespacedKey(plugin, "spellbook_projectile");
        ItemManager.getInstance().registerItem(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (isThisItem(item) && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            UUID playerId = player.getUniqueId();

            if (cooldowns.containsKey(playerId)) {
                player.sendMessage(Component.text("You are out of mana! Please wait for it to regenerate.", NamedTextColor.RED));
                return;
            }

            int currentMana = mana.getOrDefault(playerId, MAX_MANA);
            if (currentMana > 0) {
                throwSpellBook(player);
                currentMana--;
                mana.put(playerId, currentMana);
                updateManaLore(item, currentMana);

                if (currentMana == 0) {
                    cooldowns.put(playerId, System.currentTimeMillis());
                    player.sendMessage(Component.text("You are out of mana! Please wait for it to regenerate.", NamedTextColor.RED));

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            mana.put(playerId, MAX_MANA);
                            cooldowns.remove(playerId);
                            updateManaLore(item, MAX_MANA);
                            player.sendMessage(Component.text("Your mana has been replenished!", NamedTextColor.GREEN));
                        }
                    }.runTaskLater(plugin, REGEN_DELAY_TICKS);
                }
            }
        }
    }

    private void throwSpellBook(Player player) {
        Item item = player.getWorld().dropItem(player.getEyeLocation(), new ItemStack(Material.BOOK));
        item.setVelocity(player.getEyeLocation().getDirection().multiply(1.5));
        item.setPickupDelay(Integer.MAX_VALUE);
        item.setOwner(player.getUniqueId());
        item.getPersistentDataContainer().set(spellbookKey, PersistentDataType.BYTE, (byte) 1);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (item.isDead()) {
                    cancel();
                    return;
                }

                item.getWorld().spawnParticle(Particle.CRIT, item.getLocation(), 1, 0, 0, 0, 0);

                for (LivingEntity entity : item.getWorld().getNearbyLivingEntities(item.getLocation(), 1)) {
                    if (!entity.getUniqueId().equals(player.getUniqueId())) {
                        onHit(entity, item);
                        cancel();
                        return;
                    }
                }

                if (item.isOnGround()) {
                    item.remove();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void onHit(LivingEntity victim, Item spellbook) {
        victim.damage(2.0);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1f);
        spellbook.remove();
    }

    private void updateManaLore(ItemStack item, int currentMana) {
        ItemMeta meta = item.getItemMeta();
        List<String> lore = meta.getLore();
        if (lore != null && !lore.isEmpty()) {
            lore.set(0, ChatColor.GRAY + "Evil Spells: " + currentMana);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }

    private static List<String> lore() {
        return Arrays.asList(
                ChatColor.GRAY + "Evil Spells: 5",
                ChatColor.DARK_GRAY + "A magic spellbook!"
        );
    }

    private static Map<Enchantment, Integer> enchantments() {
        return Collections.singletonMap(Enchantment.LOOTING, 1);
    }
}
