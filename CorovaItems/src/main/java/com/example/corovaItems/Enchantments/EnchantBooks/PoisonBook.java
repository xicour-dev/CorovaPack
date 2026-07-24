package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaGuard.CorovaGuard;
import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovateams.CorovaTeam;
import com.example.corovateams.CorovaTeams;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;

public class PoisonBook extends EnchantmentBook {

    private static final int MAX_LEVEL = 3;
    public static final int UPGRADE_COST = 10; // XP levels per upgrade

    public PoisonBook() {
        this(1);
    }

    public PoisonBook(int level) {
        super(
                "Book of Poison",
                CorovaEnchantments.POISON_ID,
                level,
                "book_poison",
                allowedMaterials()
        );
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
                Material.MACE,
                Material.BOW,
                Material.CROSSBOW
        );
    }

    // Convert level to Roman numerals
    private static String toRoman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            default -> String.valueOf(level);
        };
    }

    public static void triggerEffect(LivingEntity damager, LivingEntity target, int level) {
        if (target instanceof Player victim && CorovaGuard.getInstance().isPlayerInSafeZone(victim)) {
            return;
        }

        // Apply poison effect (duration: 8 seconds, level matches enchant level)
        // Poison effect levels are 0-indexed, so we subtract 1
        target.addPotionEffect(new PotionEffect(
                PotionEffectType.POISON,
                8 * 20, // 8 seconds
                level - 1 // Effect amplifier (0 = Poison I)
        ));

        ItemStack weapon = (damager.getEquipment() != null) ? damager.getEquipment().getItemInMainHand() : null;
        MutationManager.getInstance().getSynergyHandler().handleEnchantSynergy(CorovaEnchantments.POISON_ID, damager, target, weapon, level);

        // Green particle effect
        Particle.DustOptions greenDust = new Particle.DustOptions(Color.fromRGB(0, 255, 0), 1.5f);
        target.getWorld().spawnParticle(
                Particle.DUST,
                target.getLocation().add(0, 1, 0),
                40,
                0.4, 0.6, 0.4,
                0,
                greenDust
        );

        // Play splash potion sound
        target.getWorld().playSound(
                target.getLocation(),
                Sound.ENTITY_SPLASH_POTION_BREAK,
                1f,
                1f
        );
    }

    // Handle poison effect on hit
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (!(event.getDamager() instanceof LivingEntity damager)) return;

        ItemStack weapon = null;

        // Get weapon from attacker
        if (damager instanceof Player player) {
            weapon = player.getInventory().getItemInMainHand();
            if (target instanceof Player victim) {
                if (CorovaGuard.getInstance().isPlayerInSafeZone(victim)) {
                    CorovaGuard.sendSafeZoneMessage(player);
                    return;
                }

                CorovaTeams teamsInstance = CorovaTeams.getInstance();
                if (teamsInstance != null) {
                    CorovaTeam attackerTeam = teamsInstance.getTeamManager().getTeamByPlayer(player.getUniqueId());
                    CorovaTeam victimTeam = teamsInstance.getTeamManager().getTeamByPlayer(victim.getUniqueId());

                    if (attackerTeam != null && attackerTeam.equals(victimTeam) && !attackerTeam.hasFriendlyFire()) {
                        return;
                    }
                }
            }
        } else {
            try {
                if (damager.getEquipment() != null) {
                    weapon = damager.getEquipment().getItemInMainHand();
                }
            } catch (Exception ignored) {}
        }

        if (target instanceof Player victim && CorovaGuard.getInstance().isPlayerInSafeZone(victim)) {
            return;
        }


        if (weapon == null || weapon.getType() == Material.AIR) return;

        // Check if weapon has poison enchantment
        if (!CorovaEnchantments.hasEnchant(weapon, getEnchantId())) return;

        // Get the poison level from the weapon
        int poisonLevel = CorovaEnchantments.getEnchantLevel(weapon);
        if (poisonLevel <= 0) return;

        triggerEffect(damager, target, poisonLevel);
    }

    public static ItemStack getUpgradedBook(ItemStack book1, ItemStack book2, org.bukkit.NamespacedKey keyId, org.bukkit.NamespacedKey keyLvl) {
        if (book1.getType() != Material.ENCHANTED_BOOK || book2.getType() != Material.ENCHANTED_BOOK) {
            return null;
        }

        ItemMeta meta1 = book1.getItemMeta();
        ItemMeta meta2 = book2.getItemMeta();

        if (!(meta1 instanceof EnchantmentStorageMeta bookMeta1) || !(meta2 instanceof EnchantmentStorageMeta bookMeta2)) {
            return null;
        }

        String id1 = bookMeta1.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
        String id2 = bookMeta2.getPersistentDataContainer().get(keyId, PersistentDataType.STRING);

        if (id1 == null || !id1.equals(id2) || !id1.equals(CorovaEnchantments.POISON_ID)) {
            return null;
        }

        Integer level1 = bookMeta1.getPersistentDataContainer().get(keyLvl, PersistentDataType.INTEGER);
        Integer level2 = bookMeta2.getPersistentDataContainer().get(keyLvl, PersistentDataType.INTEGER);

        if (level1 == null || !level1.equals(level2) || level1 >= MAX_LEVEL) {
            return null;
        }

        int newLevel = level1 + 1;
        PoisonBook upgradedBook = new PoisonBook(newLevel);
        return upgradedBook.getItemStack();
    }
}
