package com.example.corovaItems.LootHandler;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import java.util.function.Function;

/**
 * LootListener has been merged into {@link DropHandler}.
 */
public class LootListener implements Listener {
    @Deprecated
    public static void setTierProvider(Function<Player, Integer> provider) {
        DropHandler.setTierProvider(provider);
    }
}