package com.example.corovaItems.ItemMutations;

import org.bukkit.Bukkit;
import org.bukkit.entity.Arrow;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.AbstractArrow.PickupStatus;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class MutationUtils {
    public static ItemStack getBowInHand(LivingEntity entity, ItemStack bow) {
        if (entity.getEquipment() == null) {
            return null;
        } else {
            ItemStack mainHandItem = entity.getEquipment().getItemInMainHand();
            ItemStack offHandItem = entity.getEquipment().getItemInOffHand();
            if (mainHandItem != null && mainHandItem.isSimilar(bow)) {
                return mainHandItem;
            } else {
                return offHandItem != null && offHandItem.isSimilar(bow) ? offHandItem : null;
            }
        }
    }

    public static void scheduleArrowShot(JavaPlugin plugin, LivingEntity shooter, Arrow originalArrow, long delay) {
        scheduleArrowShot(plugin, shooter, originalArrow, delay, false);
    }

    public static void scheduleArrowShot(JavaPlugin plugin, LivingEntity shooter, Arrow originalArrow, long delay, boolean explode) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> fireExtraArrow(shooter, originalArrow, explode), delay);
    }

    public static void fireExtraArrow(LivingEntity shooter, Arrow originalArrow) {
        fireExtraArrow(shooter, originalArrow, false);
    }

    public static void fireExtraArrow(LivingEntity shooter, Arrow originalArrow, boolean explode) {
        ItemStack bow = com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.BowDamageScaling.getBowFromArrow(originalArrow, shooter instanceof org.bukkit.entity.Player ? (org.bukkit.entity.Player) shooter : null);
        if (bow == null && shooter.getEquipment() != null) {
            ItemStack main = shooter.getEquipment().getItemInMainHand();
            if (isBow(main)) bow = main;
            else {
                ItemStack off = shooter.getEquipment().getItemInOffHand();
                if (isBow(off)) bow = off;
            }
        }

        if (bow != null && com.example.corovaItems.Enchantments.CorovaEnchantments.hasEnchant(bow, com.example.corovaItems.Enchantments.CorovaEnchantments.WEB_SLING_ID)) {
            boolean venomous = MutationManager.getInstance().getSynergyHandler().isVenomous(bow);
            boolean withered = MutationManager.getInstance().getSynergyHandler().isWithered(bow);
            double damage = originalArrow.getDamage() * originalArrow.getVelocity().length();
            com.example.corovaItems.Enchantments.EnchantBooks.WebSlingBook.spawnWeb(shooter, originalArrow.getVelocity(), damage, venomous, withered, bow);
            return;
        }

        Arrow newArrow = (Arrow)shooter.launchProjectile(Arrow.class, originalArrow.getVelocity());
        newArrow.setShooter(shooter);
        newArrow.setPickupStatus(PickupStatus.DISALLOWED);
        newArrow.setDamage(originalArrow.getDamage());
        newArrow.setFireTicks(originalArrow.getFireTicks());
        newArrow.setCritical(originalArrow.isCritical());
        newArrow.setPierceLevel(originalArrow.getPierceLevel());
        if (explode) {
            com.example.corovaItems.Enchantments.EnchantBooks.ExplosiveRoundBook.applyExplosiveRound(bow, newArrow);
        }
    }

    public static MutationType getWeightedRandom(List<MutationType> pool, Map<MutationType, Mutation> mutations, Random random) {
        if (pool == null || pool.isEmpty()) return null;

        double totalWeight = 0.0;
        for (MutationType type : pool) {
            Mutation m = mutations.get(type);
            if (m != null) {
                totalWeight += m.getWeight();
            }
        }

        if (totalWeight <= 0) return pool.get(random.nextInt(pool.size()));

        double target = random.nextDouble() * totalWeight;
        double current = 0.0;
        for (MutationType type : pool) {
            Mutation m = mutations.get(type);
            if (m != null) {
                current += m.getWeight();
                if (current >= target) {
                    return type;
                }
            }
        }
        return pool.get(pool.size() - 1);
    }


    public static double getWeaponDamage(ItemStack item, LivingEntity attacker, LivingEntity victim) {
        double damage = 1.0;
        org.bukkit.attribute.AttributeInstance attr = attacker.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
        if (attr != null) {
            damage = attr.getValue();
        }
        if (item != null && item.hasItemMeta()) {
            // Sharpness: (0.3 * level + 0.2) up to level 5, then 0.15 per level above 5.
            int sharpness = com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.VanillaEnchantDisplay.getTrueLevel(item, org.bukkit.enchantments.Enchantment.SHARPNESS);
            if (sharpness > 0) {
                if (sharpness <= 5) {
                    damage += (0.3 * sharpness + 0.2);
                } else {
                    damage += (0.3 * 5 + 0.2) + (sharpness - 5) * 0.15;
                }
            }

            if (victim != null) {
                // Smite: 1.5 per level up to level 5, then 1.0 per level above 5.
                int smite = com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.VanillaEnchantDisplay.getTrueLevel(item, org.bukkit.enchantments.Enchantment.SMITE);
                if (smite > 0 && com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.BowDamageScaling.isUndead(victim)) {
                    if (smite <= 5) {
                        damage += 1.5 * smite;
                    } else {
                        damage += 1.5 * 5 + (smite - 5) * 1.0;
                    }
                }

                // Bane of Arthropods: 1.5 per level up to level 5, then 1.0 per level above 5.
                int bane = com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.VanillaEnchantDisplay.getTrueLevel(item, org.bukkit.enchantments.Enchantment.BANE_OF_ARTHROPODS);
                if (bane > 0 && com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.BowDamageScaling.isArthropod(victim)) {
                    if (bane <= 5) {
                        damage += 1.5 * bane;
                    } else {
                        damage += 1.5 * 5 + (bane - 5) * 1.0;
                    }
                }
            }
        }
        return damage;
    }

    public static void appendSynergyLore(ItemStack item, List<String> lore) {
        if (item == null || !item.hasItemMeta()) return;
        MutationManager mm = MutationManager.getInstance();
        if (mm == null) return;
        SynergyHandler sh = mm.getSynergyHandler();
        if (sh == null) return;

        // Custom Enchants
        Map<String, Integer> enchants = com.example.corovaItems.Enchantments.CorovaEnchantments.getAllCustomEnchants(item);
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            sh.appendEnchantSynergyLore(entry.getKey(), entry.getValue(), item, lore);
        }

        // Mutations
        Map<MutationType, Integer> mutations = mm.getMutations(item);
        for (Map.Entry<MutationType, Integer> entry : mutations.entrySet()) {
            sh.appendMutationSynergyLore(entry.getKey(), item, lore);
        }
    }

    public static void stripLoreComponents(List<String> lore) {
        if (lore == null) return;
        lore.removeIf(line ->
                line.trim().isEmpty()
                        || line.startsWith("§eSynergy:")
                        || line.startsWith("§b[")
                        || line.startsWith("§8[")
                        || line.startsWith("§6✦")
                        || line.startsWith("§e◆")
                        || line.startsWith("§6Active Set Bonuses:")
                        || line.startsWith("§6Adaptable Set Bonus:")
                        || line.startsWith("§7(Scales based on piece count)")
                        || line.startsWith("§6Set Bonuses:")
                        || line.startsWith("§6Total Trim Buffs:")
                        || line.contains("Trim]")
                        || line.startsWith(" §7- ")
                        || line.startsWith(" §7Passive:")
                        || line.startsWith(" §7Mutation:")
                        || line.startsWith(" §7Full Set:")
                        || line.startsWith(" §74-Set:")
        );
    }

    public static void stripSynergyLoreOnly(List<String> lore) {
        if (lore == null) return;
        lore.removeIf(line -> line.startsWith("§eSynergy:"));
    }

    public static boolean isArmor(ItemStack item) {
        if (item == null) return false;
        String n = item.getType().name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")
                || item.getType() == Material.TURTLE_HELMET
                || item.getType() == Material.ELYTRA;
    }

    public static boolean isSword(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getType().name().endsWith("_SWORD");
    }

    public static boolean isBow(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getType() == Material.BOW || item.getType() == Material.CROSSBOW;
    }

    public static boolean isAxe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        return item.getType().name().endsWith("_AXE");
    }

    public static boolean isScythe(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return false;
        String customId = item.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey("corovaitems", "id"), PersistentDataType.STRING);
        return customId != null && customId.endsWith("scythe");
    }

    public static boolean isTurtleShell(ItemStack item) {
        return item != null && item.getType() == Material.TURTLE_HELMET;
    }

    public static String toRoman(int num) {
        if (num <= 0) return "I";
        int[]    vals   = {1000,900,500,400,100,90,50,40,10,9,5,4,1};
        String[] romans = {"M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vals.length; i++)
            while (num >= vals[i]) { num -= vals[i]; sb.append(romans[i]); }
        return sb.toString();
    }

    public static boolean isFriendly(org.bukkit.entity.LivingEntity damager, org.bukkit.entity.LivingEntity victim) {
        if (damager == null || victim == null) return false;
        if (damager.equals(victim)) return true;
        if (damager instanceof org.bukkit.entity.Player && victim instanceof org.bukkit.entity.Player) {
            if (com.example.corovateams.CorovaTeams.getInstance() != null) {
                com.example.corovateams.TeamManager tm = com.example.corovateams.CorovaTeams.getInstance().getTeamManager();
                com.example.corovateams.CorovaTeam damagerTeam = tm.getTeamByPlayer(damager.getUniqueId());
                com.example.corovateams.CorovaTeam victimTeam = tm.getTeamByPlayer(victim.getUniqueId());
                if (damagerTeam != null && damagerTeam.equals(victimTeam) && !damagerTeam.hasFriendlyFire()) {
                    return true;
                }
            }
        }
        return false;
    }
}