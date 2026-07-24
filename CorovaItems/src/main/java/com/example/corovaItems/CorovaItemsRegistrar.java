package com.example.corovaItems;

import com.example.corovaItems.Armor.AmethystBoots;
import com.example.corovaItems.Armor.AmethystChestplate;
import com.example.corovaItems.Armor.AmethystHelmet;
import com.example.corovaItems.Armor.AmethystLeggings;
import com.example.corovaItems.CustomCrafting.*;
import com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.GrindstoneEnchantCleaner;
import com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.SharpnessBuff;
import com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.VanillaEnchantDisplay;
import com.example.corovaItems.ItemProperties.BoatProperties;
import com.example.corovaItems.ItemProperties.DeathDropRule;
import com.example.corovaItems.ItemProperties.SilkTouchSpawners;
import com.example.corovaItems.MageSystem.ManaManager;
import com.example.corovaItems.WeaponProperties.CorovaCombat;
import com.example.corovaItems.WeaponProperties.MaceNerf;
import com.example.corovaItems.Weapons.Scythes.*;
import com.example.corovaItems.Weapons.Wands.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class CorovaItemsRegistrar {
    public static void register(JavaPlugin plugin) {
        // ── MUST be first — initialises the NamespacedKey used by every item's
        //    PersistentDataContainer tag. Nothing should be constructed before this.
        CorovaItems.init(plugin);

        // ── VanillaEnchantDisplay — initialise immediately after CorovaItems so
        //    PDC keys are available before any enchantment is written to an item.
        VanillaEnchantDisplay.init(plugin);
        plugin.getLogger().info("VanillaEnchantDisplay initialised — over-cap enchant lore active.");

        Bukkit.getPluginManager().registerEvents(new GrindstoneEnchantCleaner(), plugin);

        // ── Scythes ───────────────────────────────────────────────────────────
        new WoodenScythe();
        new StoneScythe();
        new GoldenScythe();
        new CopperScythe();
        new IronScythe();
        new DiamondScythe();
        new NetheriteScythe();
        ScytheCrafting.registerRecipes(plugin);
        Bukkit.getPluginManager().registerEvents(new ScytheUpgradeListener(), plugin);

        // ── Wands ─────────────────────────────────────────────────────────────
        new WoodenWand();
        new StoneWand();
        new GoldenWand();
        new CopperWand();
        new IronWand();
        new DiamondWand();
        new NetheriteWand();
        WandCrafting.registerRecipes(plugin);
        Bukkit.getPluginManager().registerEvents(new WandUpgradeListener(), plugin);

        // ── Amethyst Armor ────────────────────────────────────────────────────
        // Items must be registered before recipes, since AmethystArmorCrafting
        // calls CorovaItems.getItemByName() to get each result ItemStack.
        new AmethystHelmet();
        new AmethystChestplate();
        new AmethystLeggings();
        new AmethystBoots();
        AmethystArmorCrafting.registerRecipes(plugin);

        // ── Mage system ───────────────────────────────────────────────────────
        ManaManager.init(plugin);
        Bukkit.getPluginManager().registerEvents(new SilkTouchSpawners(), plugin);

        // ── Global combat override ────────────────────────────────────────────
        CorovaCombat combatSystem = new CorovaCombat(plugin);
        Bukkit.getPluginManager().registerEvents(combatSystem, plugin);
        plugin.getLogger().info("CorovaCombat registered — global no-i-frame + melee cooldown active.");

        // ── Sharpness damage buff (Tiers 1-8, Sharp VI-XIII) ─────────────────
        Bukkit.getPluginManager().registerEvents(new SharpnessBuff(), plugin);
        plugin.getLogger().info("SharpnessBuff registered — Sharpness VI-XIII bonus damage active.");

        // ── Ice boat block climbing ───────────────────────────────────────────
        BoatProperties boatProperties = new BoatProperties(plugin);
        Bukkit.getPluginManager().registerEvents(boatProperties, plugin);
        plugin.getLogger().info("BoatProperties registered — ice boat step-climbing active.");

        // Mace Nerf
        Bukkit.getPluginManager().registerEvents(new MaceNerf(), plugin);

        // ── Death drop rule ─────────────────────────────────────────────────
        // Players keep inventory + XP on all non-PVP deaths; items/XP only drop on PVP kills.
        Bukkit.getPluginManager().registerEvents(new DeathDropRule(), plugin);
        plugin.getLogger().info("DeathDropRule registered — keep-inventory active for non-PVP deaths.");

        // NOTE: MutationManager is instantiated by ItemRegistry.registerAllItems().
        // Do NOT construct it again here — a second call overwrites MutationManager.instance
        // and registers a second (mismatched) set of listeners, breaking all proc checks.
    }
}