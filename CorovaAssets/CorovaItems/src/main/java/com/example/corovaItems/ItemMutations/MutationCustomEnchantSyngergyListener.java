package com.example.corovaItems.ItemMutations;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Arrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class MutationCustomEnchantSyngergyListener implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey explodeKey;

    public MutationCustomEnchantSyngergyListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.explodeKey = new NamespacedKey(plugin, "synergy_explode");
    }

}
