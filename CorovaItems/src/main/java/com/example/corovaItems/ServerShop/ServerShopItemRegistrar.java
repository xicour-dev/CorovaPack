package com.example.corovaItems.ServerShop;

import com.example.corovaGuard.ServerShopChestNetworks;
import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Registers CorovaItems' custom food/trinket/misc items into CorovaGuard's
 * server shop pools.
 *
 * <p>CorovaGuard's {@code ServerShopChestNetworks} ships {@code FOOD}
 * pre-populated with vanilla food, and {@code TRINKETS} /
 * {@code ENCHANTED_ENDER_PEARL} completely empty - it has no idea what a
 * "trinket" or an "Enchanted Ender Pearl" is, and it must never be taught:
 * CorovaGuard cannot import CorovaItems, because CorovaItems already depends
 * on CorovaGuard, and the reverse import would create a dependency loop.</p>
 *
 * <p>This class lives inside the CorovaItems module instead, which is
 * allowed to import CorovaGuard freely. It calls CorovaGuard's already
 * decoupled {@link ServerShopChestNetworks#registerPoolItem} hook (the same
 * pattern CorovaGuard already uses for {@code ShopChestChecker} and
 * {@code PlotCurrencyHook}), so CorovaGuard's own classes stay completely
 * untouched.</p>
 *
 * <p>Call {@link #registerAll(ServerShopChestNetworks, JavaPlugin)} once,
 * from CorovaCore, right after {@code new CorovaGuard(this)} and after all
 * CorovaItems items have finished registering with {@link ItemManager}.</p>
 */
public final class ServerShopItemRegistrar {

    private ServerShopItemRegistrar() {}

    /**
     * Wires all of CorovaItems' custom shop offerings into {@code shopNetworks}.
     *
     * @param shopNetworks the CorovaGuard server shop instance to register into
     * @param plugin       used only for logging (via {@link JavaPlugin#getLogger()})
     */
    public static void registerAll(ServerShopChestNetworks shopNetworks, JavaPlugin plugin) {
        // ── Food ──────────────────────────────────────────────────────────
        // These always carry a custom display name, so
        // ServerShopChestHandler#isIronPriced automatically prices them in
        // diamonds rather than the iron ingots everyday food uses.
        // Amounts/prices below are a reasonable starting point, not a
        // balance pass - tune freely.
        registerFoodPoolItem(shopNetworks, plugin, "luckymushroom", 1, 64);
        registerFoodPoolItem(shopNetworks, plugin, "enchantedglisteringmelon", 1, 20);
        registerFoodPoolItem(shopNetworks, plugin, "enchantedgoldencarrot", 1, 40);

        // ── Enchanted Ender Pearl ────────────────────────────────────────
        // Sold in its own dedicated mode, 3 per purchase for 6 diamonds.
        CorovaItems enderPearl = ItemManager.getInstance().getItemById("EnchantedEnderPearl");
        if (enderPearl != null) {
            // EnchantedEnderPearl overrides getItemStack() (not toItemStack()
            // like the rest of these), so that's the one that actually
            // applies its HIDE_ENCHANTS flag.
            ItemStack pearlStack = enderPearl.getItemStack();
            pearlStack.setAmount(3);
            shopNetworks.registerPoolItem(
                    ServerShopChestNetworks.ServerShopMode.ENCHANTED_ENDER_PEARL, pearlStack, 6);
        } else {
            plugin.getLogger().warning("[ServerShop] Could not find CorovaItems item "
                    + "'EnchantedEnderPearl' - the shop won't offer it.");
        }

        // ── Trinkets ─────────────────────────────────────────────────────
        // Placeholder prices only - a rough pass based on each trinket's
        // power level, not a balanced economy. Tune freely.
        registerTrinketPoolItem(shopNetworks, plugin, "backpack", 40);
        registerTrinketPoolItem(shopNetworks, plugin, "compactor", 20);
        registerTrinketPoolItem(shopNetworks, plugin, "energyformula", 30);
        registerTrinketPoolItem(shopNetworks, plugin, "blockreachextender", 30);
        registerTrinketPoolItem(shopNetworks, plugin, "steadfastanchor", 25);
        registerTrinketPoolItem(shopNetworks, plugin, "minersmight", 35);
        registerTrinketPoolItem(shopNetworks, plugin, "totemofdying", 35);
        registerTrinketPoolItem(shopNetworks, plugin, "swiftstrike", 35);
        registerTrinketPoolItem(shopNetworks, plugin, "spidereyetotem", 20);
        registerTrinketPoolItem(shopNetworks, plugin, "enchantedquiver", 30);
        registerTrinketPoolItem(shopNetworks, plugin, "bloodsugar", 25);
        registerTrinketPoolItem(shopNetworks, plugin, "manatrinket", 30);
        registerTrinketPoolItem(shopNetworks, plugin, "densearmorplating", 25);
        registerTrinketPoolItem(shopNetworks, plugin, "blazingpower", 25);
    }

    /** Looks up a CorovaItems item by its registered id and adds it to the FOOD pool. */
    private static void registerFoodPoolItem(ServerShopChestNetworks shopNetworks, JavaPlugin plugin,
                                             String itemId, int amount, long price) {
        CorovaItems item = ItemManager.getInstance().getItemById(itemId);
        if (item == null) {
            plugin.getLogger().warning("[ServerShop] Could not find CorovaItems item '"
                    + itemId + "' - the shop won't offer it.");
            return;
        }
        ItemStack stack = item.toItemStack();
        stack.setAmount(amount);
        shopNetworks.registerPoolItem(ServerShopChestNetworks.ServerShopMode.FOOD, stack, price);
    }

    /** Looks up a CorovaItems trinket by its registered id and adds it (1 per purchase) to the TRINKETS pool. */
    private static void registerTrinketPoolItem(ServerShopChestNetworks shopNetworks, JavaPlugin plugin,
                                                String itemId, long price) {
        CorovaItems item = ItemManager.getInstance().getItemById(itemId);
        if (item == null) {
            plugin.getLogger().warning("[ServerShop] Could not find CorovaItems item '"
                    + itemId + "' - the shop won't offer it.");
            return;
        }
        shopNetworks.registerPoolItem(ServerShopChestNetworks.ServerShopMode.TRINKETS, item.toItemStack(), price);
    }
}