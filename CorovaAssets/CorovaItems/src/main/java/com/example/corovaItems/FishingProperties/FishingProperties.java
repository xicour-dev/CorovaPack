package com.example.corovaItems.FishingProperties;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces all vanilla fishing drops with a fully custom weighted loot table.
 *
 * Register in your registrar:
 *   FishingProperties fishing = new FishingProperties(plugin);
 *   fishing.addCorovaLoot(myItem.getItemStack(), 6, "My Item");
 *   Bukkit.getPluginManager().registerEvents(fishing, plugin);
 *
 * CorovaItems and cases are injected from outside (e.g. CorovaItemsRegistrar
 * in the permissions module) to avoid circular module dependencies.
 */
public class FishingProperties implements Listener {

    private final List<FishChance> lootTable = new ArrayList<>();
    private final JavaPlugin plugin;

    public FishingProperties(JavaPlugin plugin) {
        this.plugin = plugin;
        buildDefaultLootTable();
    }

    // ── Loot table setup ──────────────────────────────────────────────────────

    private void buildDefaultLootTable() {
        // ── Fish ─────────────────────────────────────────────────────────────
        addLoot(new FishChance(new ItemStack(Material.COD),           40, 1, 3, "Cod",            FishChance.Category.FISH));
        addLoot(new FishChance(new ItemStack(Material.SALMON),        30, 1, 2, "Salmon",         FishChance.Category.FISH));
        addLoot(new FishChance(new ItemStack(Material.TROPICAL_FISH), 15, 1, 1, "Tropical Fish",  FishChance.Category.FISH));
        addLoot(new FishChance(new ItemStack(Material.PUFFERFISH),    10, 1, 1, "Pufferfish",     FishChance.Category.FISH));

        // ── Junk ─────────────────────────────────────────────────────────────
        addLoot(new FishChance(new ItemStack(Material.BONE),          12, 1, 2, "Bone",           FishChance.Category.JUNK));
        addLoot(new FishChance(new ItemStack(Material.STRING),        10, 1, 2, "String",         FishChance.Category.JUNK));
        addLoot(new FishChance(new ItemStack(Material.LEATHER_BOOTS),  5, 1, 1, "Old Boots",      FishChance.Category.JUNK));
        addLoot(new FishChance(new ItemStack(Material.LILY_PAD),       4, 1, 1, "Lily Pad",       FishChance.Category.JUNK));
        addLoot(new FishChance(new ItemStack(Material.INK_SAC),        3, 1, 3, "Ink Sac",        FishChance.Category.JUNK));

        // ── Treasure ─────────────────────────────────────────────────────────
        addLoot(new FishChance(new ItemStack(Material.NAUTILUS_SHELL),   4, 1, 1, "Nautilus Shell",   FishChance.Category.TREASURE));
        addLoot(new FishChance(new ItemStack(Material.NAME_TAG),         2, 1, 1, "Name Tag",         FishChance.Category.TREASURE));
        addLoot(new FishChance(new ItemStack(Material.HEART_OF_THE_SEA), 1, 1, 1, "Heart of the Sea", FishChance.Category.TREASURE));
    }

    /**
     * Adds a CorovaItem or case to the fishing loot table.
     * Call this from whichever module has access to the item — typically
     * CorovaItemsRegistrar in the permissions module, which depends on both
     * corovaItems and corovaPermissions and can safely reference both.
     *
     * @param item   The ItemStack to award (cloned internally by FishChance).
     * @param weight Relative weight — higher = more common.
     * @param label  Display name shown in the action bar on catch.
     */
    public void addCorovaLoot(ItemStack item, int weight, String label) {
        addLoot(new FishChance(item, weight, 1, 1, label, FishChance.Category.COROVA_ITEM));
    }

    /**
     * Adds any loot entry to the table directly.
     */
    public void addLoot(FishChance entry) {
        lootTable.add(entry);
        plugin.getLogger().fine("[FishingProperties] Added loot: "
                + entry.getDisplayLabel()
                + " (weight=" + entry.getWeight() + ", "
                + String.format("%.1f%%", FishUtils.chancePercent(entry, lootTable)) + ")");
    }

    /** Returns the live loot table so other systems can inspect or mutate it. */
    public List<FishChance> getLootTable() {
        return lootTable;
    }

    // ── Event handling ────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        Player   player  = event.getPlayer();
        Location hookLoc = event.getHook().getLocation();

        if (event.getCaught() instanceof Item caughtEntity) {
            caughtEntity.remove();
        }
        event.setExpToDrop(0);

        FishChance chosen = FishUtils.pickWeightedRandom(lootTable, player);
        if (chosen == null) return;

        ItemStack reward = FishUtils.resolveItem(chosen);

        var leftover = player.getInventory().addItem(reward);
        if (!leftover.isEmpty()) {
            hookLoc.getWorld().dropItemNaturally(hookLoc, leftover.values().iterator().next());
        }

        sendCatchMessage(player, chosen);
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 1f);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void sendCatchMessage(Player player, FishChance entry) {
        NamedTextColor color = switch (entry.getCategory()) {
            case FISH        -> NamedTextColor.AQUA;
            case JUNK        -> NamedTextColor.GRAY;
            case TREASURE    -> NamedTextColor.GOLD;
            case COROVA_ITEM -> NamedTextColor.LIGHT_PURPLE;
            case CUSTOM      -> NamedTextColor.LIGHT_PURPLE;
        };

        player.sendActionBar(
                Component.text("✦ Caught: ", NamedTextColor.WHITE)
                        .append(Component.text(entry.getDisplayLabel(), color)
                                .decoration(TextDecoration.BOLD, false))
        );
    }
}