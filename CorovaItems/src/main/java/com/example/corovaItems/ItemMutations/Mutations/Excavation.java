//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class Excavation implements Mutation {
    private static final Random RANDOM = new Random();
    private final MutationManager mutationManager;

    public Excavation(MutationManager mutationManager) {
        this.mutationManager = mutationManager;
    }


    @Override
    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL);
    }

    @Override
    public String getColor() {
        return "#FFD700";
    }

    @Override
    public String getName() {
        return "Excavation";
    }

    public int getMaxLevel() {
        return 2;
    }

    public MutationType getType() {
        return MutationType.EXCAVATION;
    }

    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }

    public List<String> getLore(int level) {
        List<String> lore = new java.util.ArrayList<>();
        ChatColor color = ChatColor.of(getColor());
        lore.add(color + getName() + " " + MutationUtils.toRoman(level));
        return lore;
    }

    @Override
    public List<String> getDescription(int level) {
        List<String> desc = new java.util.ArrayList<>();
        if (level >= 2) {
            desc.add(ChatColor.GRAY + "Mining blocks has a chance to");
            desc.add(ChatColor.GRAY + "uncover rare minerals.");
        } else {
            desc.add(ChatColor.GRAY + "Mining blocks has a small chance");
            desc.add(ChatColor.GRAY + "to uncover hidden minerals.");
        }
        return desc;
    }


    public void onProc(LivingEntity damager, LivingEntity victim, int level, EntityDamageByEntityEvent event) {
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack pickaxe = player.getInventory().getItemInMainHand();
        if (pickaxe != null && !pickaxe.getType().isAir()) {
            if (pickaxe.getType().name().endsWith("_PICKAXE")) {
                if (this.mutationManager.hasMutation(pickaxe, MutationType.EXCAVATION)) {
                    int level = this.mutationManager.getMutationLevel(pickaxe, MutationType.EXCAVATION);
                    Block block = event.getBlock();
                    List<ExcavationDrop> drops = getDrops(block.getType(), level);
                    if (!drops.isEmpty()) {
                        com.example.corovaItems.ArmorTrims.PlayerTrimProfile profile = com.example.corovaItems.ArmorTrims.TrimManager.getInstance().getProfile(player);
                        double bonusChance = com.example.corovaItems.ArmorTrims.TrimCalculator.getAmplification(getCategories(), profile, "incremental");
                        for(ExcavationDrop drop : drops) {
                            if (RANDOM.nextDouble() < (drop.chance + bonusChance)) {
                                block.getWorld().dropItemNaturally(block.getLocation().add((double)0.5F, (double)0.5F, (double)0.5F), new ItemStack(drop.material, drop.amount));
                            }
                        }

                    }
                }
            }
        }
    }

    private static List<ExcavationDrop> getDrops(Material blockType, int level) {
        List var10000;
        switch (blockType) {
            case STONE:
            case COBBLESTONE:
                var10000 = level >= 2 ? List.of(new ExcavationDrop(Material.IRON_NUGGET, 1, 0.07)) : List.of(new ExcavationDrop(Material.IRON_NUGGET, 1, 0.04));
                break;
            case DEEPSLATE:
            case COBBLED_DEEPSLATE:
                var10000 = level >= 2 ? List.of(new ExcavationDrop(Material.IRON_NUGGET, 1, 0.06), new ExcavationDrop(Material.IRON_INGOT, 1, 0.02)) : List.of(new ExcavationDrop(Material.IRON_NUGGET, 1, 0.04), new ExcavationDrop(Material.IRON_INGOT, 1, 0.01));
                break;
            case GRAVEL:
                var10000 = level >= 2 ? List.of(new ExcavationDrop(Material.IRON_NUGGET, 1, 0.13)) : List.of(new ExcavationDrop(Material.IRON_NUGGET, 1, 0.08));
                break;
            case SAND:
            case RED_SAND:
                var10000 = level >= 2 ? List.of(new ExcavationDrop(Material.GOLD_NUGGET, 1, 0.05)) : List.of(new ExcavationDrop(Material.GOLD_NUGGET, 1, 0.03));
                break;
            case SANDSTONE:
            case CHISELED_SANDSTONE:
            case CUT_SANDSTONE:
            case SMOOTH_SANDSTONE:
            case RED_SANDSTONE:
            case CHISELED_RED_SANDSTONE:
            case CUT_RED_SANDSTONE:
            case SMOOTH_RED_SANDSTONE:
                var10000 = level >= 2 ? List.of(new ExcavationDrop(Material.GOLD_NUGGET, 1, 0.04)) : List.of(new ExcavationDrop(Material.GOLD_NUGGET, 1, 0.02));
                break;
            case NETHERRACK:
                var10000 = level >= 2 ? List.of(new ExcavationDrop(Material.GOLD_NUGGET, 1, 0.1), new ExcavationDrop(Material.ANCIENT_DEBRIS, 1, 0.002)) : List.of(new ExcavationDrop(Material.GOLD_NUGGET, 1, 0.06), new ExcavationDrop(Material.ANCIENT_DEBRIS, 1, 0.001));
                break;
            case BLACKSTONE:
            case GILDED_BLACKSTONE:
                var10000 = level >= 2 ? List.of(new ExcavationDrop(Material.GOLD_NUGGET, 1, 0.07)) : List.of(new ExcavationDrop(Material.GOLD_NUGGET, 1, 0.04));
                break;
            case BASALT:
            case SMOOTH_BASALT:
                var10000 = level >= 2 ? List.of(new ExcavationDrop(Material.GOLD_NUGGET, 1, 0.05)) : List.of(new ExcavationDrop(Material.GOLD_NUGGET, 1, 0.03));
                break;
            case MAGMA_BLOCK:
                var10000 = level >= 2 ? List.of(new ExcavationDrop(Material.GOLD_NUGGET, 1, 0.08)) : List.of(new ExcavationDrop(Material.GOLD_NUGGET, 1, 0.05));
                break;
            case NETHER_BRICKS:
            case CRACKED_NETHER_BRICKS:
            case CHISELED_NETHER_BRICKS:
                var10000 = level >= 2 ? List.of(new ExcavationDrop(Material.QUARTZ, 1, 0.08)) : List.of(new ExcavationDrop(Material.QUARTZ, 1, 0.05));
                break;
            case END_STONE:
            case END_STONE_BRICKS:
                var10000 = level >= 2 ? List.of(new ExcavationDrop(Material.EMERALD, 1, 0.02), new ExcavationDrop(Material.DIAMOND, 1, 0.01)) : List.of(new ExcavationDrop(Material.EMERALD, 1, 0.01), new ExcavationDrop(Material.DIAMOND, 1, 0.005));
                break;
            case OBSIDIAN:
            case CRYING_OBSIDIAN:
                var10000 = level >= 2 ? List.of(new ExcavationDrop(Material.DIAMOND, 1, 0.04)) : List.of(new ExcavationDrop(Material.DIAMOND, 1, 0.02));
                break;
            case TERRACOTTA:
            case WHITE_TERRACOTTA:
            case ORANGE_TERRACOTTA:
            case MAGENTA_TERRACOTTA:
            case LIGHT_BLUE_TERRACOTTA:
            case YELLOW_TERRACOTTA:
            case LIME_TERRACOTTA:
            case PINK_TERRACOTTA:
            case GRAY_TERRACOTTA:
            case LIGHT_GRAY_TERRACOTTA:
            case CYAN_TERRACOTTA:
            case PURPLE_TERRACOTTA:
            case BLUE_TERRACOTTA:
            case BROWN_TERRACOTTA:
            case GREEN_TERRACOTTA:
            case RED_TERRACOTTA:
            case BLACK_TERRACOTTA:
                var10000 = level >= 2 ? List.of(new ExcavationDrop(Material.IRON_NUGGET, 1, 0.05)) : List.of(new ExcavationDrop(Material.IRON_NUGGET, 1, 0.03));
                break;
            case PACKED_ICE:
            case BLUE_ICE:
                var10000 = level >= 2 ? List.of(new ExcavationDrop(Material.DIAMOND, 1, 0.02)) : List.of(new ExcavationDrop(Material.DIAMOND, 1, 0.01));
                break;
            default:
                var10000 = Collections.emptyList();
        }

        return var10000;
    }

    private static class ExcavationDrop {
        final Material material;
        final int amount;
        final double chance;

        ExcavationDrop(Material material, int amount, double chance) {
            this.material = material;
            this.amount = amount;
            this.chance = chance;
        }
    }
}
