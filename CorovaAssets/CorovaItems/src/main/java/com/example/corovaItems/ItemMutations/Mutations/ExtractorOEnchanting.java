package com.example.corovaItems.ItemMutations.Mutations;

import com.example.corovaItems.ItemMutations.Mutation;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import com.example.corovaItems.ItemMutations.MutationUtils;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Set;

public class ExtractorOEnchanting implements Mutation {

    private final MutationManager mutationManager;

    public ExtractorOEnchanting(MutationManager mutationManager) {
        this.mutationManager = mutationManager;
    }

    public Set<MutationCategory> getCategories() {
        return Set.of(MutationCategory.INCREMENTAL, MutationCategory.RECOVERY);
    }

    @Override
    public String getColor() {
        return "#FFD700";
    }

    @Override
    public String getName() {
        return "Extractor o' Enchanting";
    }

    @Override
    public int getMaxLevel() {
        return 2;
    }

    @Override
    public List<String> getLore(int level) {
        List<String> lore = new java.util.ArrayList<>();
        ChatColor color = ChatColor.of(getColor());
        lore.add(color + getName() + " " + MutationUtils.toRoman(level));
        return lore;
    }

    @Override
    public List<String> getDescription(int level) {
        List<String> desc = new java.util.ArrayList<>();
        desc.add(ChatColor.GRAY + "Increases experience dropped from killed");
        desc.add(ChatColor.GRAY + "entities and mined ores.");
        return desc;
    }

    @Override
    public boolean isCompatible(ItemStack item) {
        return MutationUtils.isSword(item)
                || item.getType().name().endsWith("_AXE")
                || item.getType().name().endsWith("_PICKAXE")
                || item.getType().name().endsWith("_HOE")
                || MutationUtils.isScythe(item)
                || MutationUtils.isBow(item);
    }

    @Override
    public MutationType getType() {
        return MutationType.EXTRACTOR_O_ENCHANTING;
    }

    @Override
    public double getWeight() {
        return com.example.corovaItems.ItemMutations.ItemMutations.DEFAULT_WEIGHT;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) {
            return;
        }

        Player player = event.getEntity().getKiller();
        ItemStack weapon = player.getInventory().getItemInMainHand();

        if (weapon == null || weapon.getType().isAir()) {
            return;
        }

        if (mutationManager.hasMutation(weapon, MutationType.EXTRACTOR_O_ENCHANTING)) {
            int level = mutationManager.getMutationLevel(weapon, MutationType.EXTRACTOR_O_ENCHANTING);
            double multiplier = (level == 1) ? 1.5 : 2.0;
            multiplier = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, multiplier, "recovery");
            event.setDroppedExp((int) (event.getDroppedExp() * multiplier));
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (tool == null || tool.getType().isAir() || event.getExpToDrop() <= 0) {
            return;
        }

        // Only apply to ores as requested
        if (!event.getBlock().getType().name().endsWith("_ORE")) {
            return;
        }

        if (mutationManager.hasMutation(tool, MutationType.EXTRACTOR_O_ENCHANTING)) {
            int level = mutationManager.getMutationLevel(tool, MutationType.EXTRACTOR_O_ENCHANTING);
            double multiplier = (level == 1) ? 2.0 : 4.0;
            multiplier = MutationManager.getInstance().getSynergyHandler().applyTrimAmplification(player, this, multiplier, "recovery");
            event.setExpToDrop((int) (event.getExpToDrop() * multiplier));
        }
    }

    @Override
    public void onProcSynergy(org.bukkit.entity.LivingEntity damager, org.bukkit.entity.LivingEntity victim, int level) {
        // No direct combat effect, synergy handled by standard death listener.
    }
}
