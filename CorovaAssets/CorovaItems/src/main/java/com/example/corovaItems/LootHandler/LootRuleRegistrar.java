package com.example.corovaItems.LootHandler;

import com.example.corovaItems.LootHandler.LootRules.EnderGiantLootRule;
import com.example.corovaItems.LootHandler.LootRules.EndermanLootRule;
import org.bukkit.plugin.java.JavaPlugin;

public class LootRuleRegistrar {

    public static void registerAll(DropHandler dropHandler, JavaPlugin plugin) {
        // Custom Mob Loot Rules
        dropHandler.registerRule(new EnderGiantLootRule());
        dropHandler.registerRule(new EndermanLootRule());
    }
}
