package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaGuard.CorovaGuard;
import com.example.corovaItems.CurrencyHook;
import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.ItemManager;
import com.example.corovateams.CorovaTeam;
import com.example.corovateams.CorovaTeams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.SimpleDateFormat;
import java.util.*;

public class PaydayBook extends EnchantmentBook {

    private static final int MAX_LEVEL = 5;
    public static final int UPGRADE_COST = 10;

    // Tracks cosmetic paper drops so they cannot be picked up
    private static final List<Item> moneyDrops = new ArrayList<>();
    // Prevents duplicate death processing within the same tick
    private static final Set<UUID> processedDeaths = new HashSet<>();

    private final JavaPlugin plugin;

    public PaydayBook() {
        this(1);
    }

    public PaydayBook(int level) {
        super(
                "Book of Payday",
                CorovaEnchantments.PAYDAY_ID,
                level,
                "book_payday",
                allowedMaterials()
        );
        this.plugin = JavaPlugin.getProvidingPlugin(this.getClass());
        ItemManager.getInstance().registerItem(this);
    }

    private static Set<Material> allowedMaterials() {
        return Set.of(
                Material.WOODEN_SWORD,
                Material.STONE_SWORD,
                Material.IRON_SWORD,
                Material.GOLDEN_SWORD,
                Material.DIAMOND_SWORD,
                Material.NETHERITE_SWORD,
                Material.WOODEN_AXE,
                Material.STONE_AXE,
                Material.IRON_AXE,
                Material.GOLDEN_AXE,
                Material.DIAMOND_AXE,
                Material.NETHERITE_AXE,
                Material.TRIDENT,
                Material.MACE
        );
    }

    private static String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }

    public static void triggerEffect(LivingEntity damager, LivingEntity target, int level) {
        if (!(target instanceof Player victim)) return;

        // Safe-zone / team checks
        if (CorovaGuard.getInstance().isPlayerInSafeZone(victim)) {
            if (damager instanceof Player attacker) CorovaGuard.sendSafeZoneMessage(attacker);
            return;
        }
        CorovaTeams teamsInstance = CorovaTeams.getInstance();
        if (teamsInstance != null) {
            CorovaTeam attackerTeam = teamsInstance.getTeamManager().getTeamByPlayer(damager.getUniqueId());
            CorovaTeam victimTeam   = teamsInstance.getTeamManager().getTeamByPlayer(victim.getUniqueId());
            if (attackerTeam != null && attackerTeam.equals(victimTeam) && !attackerTeam.hasFriendlyFire()) return;
        }

        spawnFireworkStatic(victim.getLocation());

        List<Item> currentDrops = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Item paper = victim.getWorld().dropItemNaturally(
                    victim.getLocation().add(0, 1, 0),
                    new ItemStack(Material.PAPER)
            );
            moneyDrops.add(paper);
            currentDrops.add(paper);
        }

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(PaydayBook.class);
        new BukkitRunnable() {
            @Override public void run() {
                for (Item item : currentDrops) item.remove();
                moneyDrops.removeAll(currentDrops);
            }
        }.runTaskLater(plugin, 20L);
    }

    // ── Cosmetic paper scatter on hit ─────────────────────────────────────────

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!CorovaEnchantments.hasEnchant(weapon, CorovaEnchantments.PAYDAY_ID)) return;

        triggerEffect(attacker, victim, CorovaEnchantments.getEnchantLevel(weapon));
    }

    @EventHandler
    public void onItemPickup(PlayerPickupItemEvent event) {
        if (moneyDrops.contains(event.getItem())) event.setCancelled(true);
    }

    // ── Kill logic: steal 10% per enchant level, award trophy ────────────────

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;

        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (!CorovaEnchantments.hasEnchant(weapon, CorovaEnchantments.PAYDAY_ID)) return;

        UUID victimId = victim.getUniqueId();
        if (processedDeaths.contains(victimId)) return;
        processedDeaths.add(victimId);

        // Steal: 10% × enchant level, capped at 50% (level 5)
        int enchantLevel = CorovaEnchantments.getEnchantLevel(weapon);
        double stealPercent = Math.min(enchantLevel * 0.10, 0.50);

        long victimBalance = CurrencyHook.Registry.getBalance(victim);
        long stolen = (long) Math.floor(victimBalance * stealPercent);

        if (stolen > 0) {
            CurrencyHook.Registry.removeBalance(victim, stolen);
            CurrencyHook.Registry.addBalance(killer, stolen);

            String enchantName = EnchantmentBook.applyEnchantmentGradient(CorovaEnchantments.PAYDAY_ID, "Payday");
            killer.sendMessage(org.bukkit.ChatColor.GREEN + "" + enchantName + org.bukkit.ChatColor.GREEN + "! You stole "
                    + CurrencyHook.Registry.format(stolen) + org.bukkit.ChatColor.GREEN + " from " + victim.getName() + "!");
            victim.sendMessage(org.bukkit.ChatColor.RED + victim.getName() + " stole "
                    + CurrencyHook.Registry.format(stolen) + org.bukkit.ChatColor.RED + " from your balance (" + enchantName + org.bukkit.ChatColor.RED + ")!");
        }

        Bukkit.broadcast(Component.text("It's payday! " + killer.getName()
                + " collected from " + victim.getName() + "!", NamedTextColor.GREEN));

        // Kill certificate
        ItemStack trophy = new ItemStack(Material.PAPER);
        ItemMeta meta = trophy.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Kill: " + victim.getName(), NamedTextColor.WHITE));
            String date = new SimpleDateFormat("MM-dd-yyyy").format(new Date());
            meta.lore(Collections.singletonList(Component.text(
                    "Trophy for killing " + victim.getName() + " on " + date, NamedTextColor.DARK_GRAY)));
            trophy.setItemMeta(meta);
        }
        killer.getInventory().addItem(trophy);

        new BukkitRunnable() {
            @Override public void run() { processedDeaths.remove(victimId); }
        }.runTaskLater(plugin, 5L);
    }

    // ── Firework helper ───────────────────────────────────────────────────────

    private void spawnFirework(org.bukkit.Location location) {
        spawnFireworkStatic(location);
    }

    private static void spawnFireworkStatic(org.bukkit.Location location) {
        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(PaydayBook.class);
        Firework fw = location.getWorld().spawn(location, Firework.class);
        FireworkMeta fwm = fw.getFireworkMeta();
        fwm.setPower(0);
        fwm.addEffect(FireworkEffect.builder()
                .withColor(Color.GREEN)
                .withFade(Color.YELLOW)
                .with(FireworkEffect.Type.BURST)
                .build());
        fw.setFireworkMeta(fwm);
        new BukkitRunnable() {
            @Override public void run() { fw.detonate(); }
        }.runTaskLater(plugin, 2L);
    }

    // ── Anvil book-upgrade support ────────────────────────────────────────────

    public static ItemStack getUpgradedBook(ItemStack book1, ItemStack book2,
                                            org.bukkit.NamespacedKey keyId,
                                            org.bukkit.NamespacedKey keyLvl) {
        if (book1.getType() != Material.ENCHANTED_BOOK || book2.getType() != Material.ENCHANTED_BOOK) return null;

        ItemMeta meta1 = book1.getItemMeta();
        ItemMeta meta2 = book2.getItemMeta();
        if (!(meta1 instanceof EnchantmentStorageMeta bookMeta1)
                || !(meta2 instanceof EnchantmentStorageMeta bookMeta2)) return null;

        String id1 = bookMeta1.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
        String id2 = bookMeta2.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
        if (id1 == null || !id1.equals(id2) || !id1.equals(CorovaEnchantments.PAYDAY_ID)) return null;

        Integer level1 = bookMeta1.getPersistentDataContainer().get(keyLvl, PersistentDataType.INTEGER);
        Integer level2 = bookMeta2.getPersistentDataContainer().get(keyLvl, PersistentDataType.INTEGER);
        if (level1 == null || !level1.equals(level2) || level1 >= MAX_LEVEL) return null;

        return new PaydayBook(level1 + 1).getItemStack();
    }
}