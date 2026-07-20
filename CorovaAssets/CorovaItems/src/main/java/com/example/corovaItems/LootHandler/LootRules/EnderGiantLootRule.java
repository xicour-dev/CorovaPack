package com.example.corovaItems.LootHandler.LootRules;

import com.example.corovaItems.ItemManager;
import com.example.corovaItems.LootHandler.DropContext;
import com.example.corovaItems.LootHandler.MobLootRule;
import org.bukkit.entity.Enderman;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class EnderGiantLootRule extends MobLootRule {

    private static final String ENDER_GIANT_TAG = "corovacore_endergiant";

    @Override
    protected boolean appliesTo(DropContext context) {
        return (context.getMob() instanceof Enderman)
                && context.getMob().getScoreboardTags().contains(ENDER_GIANT_TAG);
    }

    @Override
    protected void collectItems(DropContext context, List<ItemStack> drops) {
        ItemStack pearl = ItemManager.getInstance().getItemById("EnchantedEnderPearl").getItemStack();
        if (pearl != null) drops.add(pearl);
    }

    @Override
    public int getPriority() {
        return 10;
    }
}