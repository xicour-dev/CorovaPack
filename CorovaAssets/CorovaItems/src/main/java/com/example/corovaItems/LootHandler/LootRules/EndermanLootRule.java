package com.example.corovaItems.LootHandler.LootRules;

import com.example.corovaItems.LootHandler.DropContext;
import com.example.corovaItems.LootHandler.MobLootRule;
import org.bukkit.entity.Enderman;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class EndermanLootRule extends MobLootRule {

    @Override
    protected boolean appliesTo(DropContext context) {
        return context.getMob() instanceof Enderman;
    }

    @Override
    protected void collectItems(DropContext context, List<ItemStack> drops) {
        // Teleport book drop is handled by TeleportBookLootRule (AbstractItemLootRule)
    }

    @Override
    public boolean overridesVanillaDrops() {
        return false; // Keep vanilla ender pearl drops
    }
}