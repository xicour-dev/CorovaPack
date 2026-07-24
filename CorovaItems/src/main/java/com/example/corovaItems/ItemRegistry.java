package com.example.corovaItems;

import com.example.corovaItems.ItemMutations.MutationDiscoveryListener;
import com.example.corovaItems.ItemMutations.MutationManager;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.reflections.Reflections;

import java.util.Set;

public class ItemRegistry {

    public static void registerAllItems(JavaPlugin plugin) {
        Reflections reflections = new Reflections("com.example.corovaItems");
        Set<Class<? extends CorovaItems>> items = reflections.getSubTypesOf(CorovaItems.class);

        for (Class<? extends CorovaItems> itemClass : items) {
            if (java.lang.reflect.Modifier.isAbstract(itemClass.getModifiers())) {
                continue;
            }
            if (com.example.corovaItems.Enchantments.EnchantmentBook.class.isAssignableFrom(itemClass)) {
                continue;
            }
            try {
                CorovaItems item = itemClass.getDeclaredConstructor().newInstance();
                if (item instanceof Listener listenerItem) {
                    Bukkit.getPluginManager().registerEvents(listenerItem, plugin);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to register CorovaItem: " + itemClass.getSimpleName());
            }
        }

        // Reuse existing MutationManager (created in CorovaCore before EnchantmentBook)
        // — never construct a second one or its Listener will fire every event twice.
        MutationManager mutationManager = MutationManager.getInstance();
        if (mutationManager == null) mutationManager = new MutationManager(plugin);
        Bukkit.getPluginManager().registerEvents(new MutationDiscoveryListener(mutationManager), plugin);
    }

    public static CorovaItems getItem(String internalId) {
        return CorovaItems.getItemByName(internalId);
    }
}