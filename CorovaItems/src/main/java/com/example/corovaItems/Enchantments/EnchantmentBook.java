package com.example.corovaItems.Enchantments;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.Enchantments.EnchantBooks.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Abstract class for custom enchantment books.
 */
public abstract class EnchantmentBook extends CorovaItems implements Listener {

    private final String enchantId;
    private final int level;
    private final Set<Material> allowedMaterials;

    // =========================================================================
    // Static registry — populated automatically when any EnchantmentBook
    // subclass is constructed (i.e. during registerAllBooks).
    // =========================================================================
    private static final Map<String, Set<Material>> REGISTRY    = new HashMap<>();
    private static final Map<String, String>        CLASS_TO_ID = new HashMap<>();
    private static final Map<String, EnchantmentBook> ID_TO_BOOK = new HashMap<>();

    /**
     * Maps every copper tool/weapon/armor material to its iron equivalent.
     * When a copper item is placed in the anvil, we check the iron counterpart
     * against the enchant's allowedMaterials — so copper items are treated
     * identically to their non-copper counterparts without touching each book class.
     */
    private static final Map<Material, Material> COPPER_TO_EQUIVALENT = new HashMap<>();
    static {
        // Weapons / tools
        COPPER_TO_EQUIVALENT.put(Material.COPPER_SWORD,    Material.IRON_SWORD);
        COPPER_TO_EQUIVALENT.put(Material.COPPER_AXE,      Material.IRON_AXE);
        COPPER_TO_EQUIVALENT.put(Material.COPPER_PICKAXE,  Material.IRON_PICKAXE);
        COPPER_TO_EQUIVALENT.put(Material.COPPER_SHOVEL,   Material.IRON_SHOVEL);
        COPPER_TO_EQUIVALENT.put(Material.COPPER_HOE,      Material.IRON_HOE);
        // Armor
        COPPER_TO_EQUIVALENT.put(Material.COPPER_HELMET,   Material.IRON_HELMET);
        COPPER_TO_EQUIVALENT.put(Material.COPPER_CHESTPLATE, Material.IRON_CHESTPLATE);
        COPPER_TO_EQUIVALENT.put(Material.COPPER_LEGGINGS, Material.IRON_LEGGINGS);
        COPPER_TO_EQUIVALENT.put(Material.COPPER_BOOTS,    Material.IRON_BOOTS);
    }

    public static Set<String> getRegisteredEnchantIds() {
        return REGISTRY.keySet();
    }

    public static String getEnchantIdByClassName(String className) {
        return CLASS_TO_ID.get(className.toLowerCase());
    }

    public static EnchantmentBook getBookById(String enchantId) {
        return ID_TO_BOOK.get(enchantId.toLowerCase());
    }

    public static boolean canEnchantApplyTo(String enchantId, Material material) {
        if (enchantId == null || material == null) return false;
        Set<Material> allowed = REGISTRY.get(enchantId);
        if (allowed == null) return false;
        // Direct match — handles all non-copper materials as before
        if (allowed.contains(material)) return true;
        // Copper fallback: check whether the iron equivalent is allowed
        Material equivalent = COPPER_TO_EQUIVALENT.get(material);
        return equivalent != null && allowed.contains(equivalent);
    }

    // =========================================================================

    protected EnchantmentBook(String displayName, String enchantId, int level,
                              String internalId, Set<Material> allowedMaterials) {
        super(
                applyEnchantmentGradient(enchantId, displayName),
                Material.ENCHANTED_BOOK,
                buildLore(enchantId, level),
                new HashMap<>(),
                internalId
        );
        this.enchantId        = enchantId;
        this.level            = Math.max(1, level);
        this.allowedMaterials = allowedMaterials;

        REGISTRY.put(enchantId, Collections.unmodifiableSet(allowedMaterials));
        CLASS_TO_ID.put(this.getClass().getSimpleName().toLowerCase(), enchantId);
        ID_TO_BOOK.put(enchantId.toLowerCase(), this);
    }

    private static List<String> buildLore(String enchantId, int level) {
        String label = CorovaEnchantments.DISPLAY_NAME.getOrDefault(enchantId, enchantId);
        String roman = CorovaEnchantments.toRoman(Math.max(1, level));
        List<String> lore = new ArrayList<>();
        lore.add(applyEnchantmentGradient(enchantId, label + " " + roman));
        lore.add(org.bukkit.ChatColor.DARK_GRAY + "Apply to weapon using anvil");
        lore.add(org.bukkit.ChatColor.DARK_GRAY + "Click the result slot to apply");

        return lore;
    }
    public static List<String> getEnchantDescription(String enchantId, int level) {
        return new ArrayList<>(); // Descriptions moved to /item info
    }

    public static List<String> getEnchantInfo(String enchantId, int level) {
        List<String> desc = new ArrayList<>();
        switch (enchantId) {
            case CorovaEnchantments.LIGHTNING_ID -> {
                switch (level) {
                    case 1 -> desc.add(org.bukkit.ChatColor.GOLD + "10s cooldown | 1.5× damage");
                    case 2 -> desc.add(org.bukkit.ChatColor.GOLD + "9s cooldown  | 2.5× damage");
                    case 3 -> desc.add(org.bukkit.ChatColor.GOLD + "8s cooldown  | 3.5× damage");
                    case 4 -> desc.add(org.bukkit.ChatColor.GOLD + "7s cooldown  | 4.5× damage");
                    case 5 -> desc.add(org.bukkit.ChatColor.GOLD + "6s cooldown  | 5.5× damage");
                }
            }
            case CorovaEnchantments.BEAM_ID -> {
                switch (level) {
                    case 1 -> desc.add(org.bukkit.ChatColor.GOLD + "Charge: 5 hits | 2.0× damage");
                    case 2 -> desc.add(org.bukkit.ChatColor.GOLD + "Charge: 3 hits | 3.0× damage");
                    case 3 -> desc.add(org.bukkit.ChatColor.GOLD + "Charge: 2 hits | 4.5× damage");
                }
            }
            case CorovaEnchantments.LAUNCH_ID -> {
                switch (level) {
                    case 1 -> desc.add(org.bukkit.ChatColor.GOLD + "Light launch (velocity 0.65)");
                    case 2 -> desc.add(org.bukkit.ChatColor.GOLD + "Standard launch (velocity 1.30)");
                    case 3 -> desc.add(org.bukkit.ChatColor.GOLD + "Heavy launch (velocity 2.60)");
                }
            }
            case CorovaEnchantments.EXPLOSIVE_ROUND_ID -> {
                desc.add(org.bukkit.ChatColor.GOLD + "Explosion damage scales with level & Power.");
                desc.add(org.bukkit.ChatColor.GOLD + "Radius scales with level & Power.");
                desc.add(org.bukkit.ChatColor.GOLD + "Damage scales with bow charge strength.");
            }
            case CorovaEnchantments.ARROW_RAIN_ID -> {
                String countDesc = switch (level) {
                    case 1  -> "Rains 2–4 arrows";
                    case 2  -> "Rains 4–7 arrows";
                    default -> "Rains 7–12 arrows";
                };
                desc.add(org.bukkit.ChatColor.GOLD + countDesc + " on hit.");
            }
            case CorovaEnchantments.STORM_ID -> {
                desc.add(org.bukkit.ChatColor.GOLD + "Lightning & Puddles scale with Bow enchantments.");
            }
            case CorovaEnchantments.MISSILE_ID -> {
                desc.add(org.bukkit.ChatColor.GOLD + "Explosion damage scales with Bow enchantments.");
                if (level > 1) {
                    desc.add(org.bukkit.ChatColor.GOLD + "Allows up to " + level + " missiles loitering at once.");
                }
            }
            case CorovaEnchantments.WEB_SLING_ID -> {
                desc.add(org.bukkit.ChatColor.GOLD + "Shoots webs instead of arrows. Consumes arrows.");
                desc.add(org.bukkit.ChatColor.GOLD + "Webs apply Slowness III for 5s.");
            }
            case CorovaEnchantments.MUSIC_ID -> {
                desc.add(org.bukkit.ChatColor.GOLD + "Sneak to charge a note blast (up to 10 block radius).");
                desc.add(org.bukkit.ChatColor.GOLD + "Kill song plays within 26 blocks.");
            }
            case CorovaEnchantments.HOOK_ID -> {
                desc.add(org.bukkit.ChatColor.GOLD + "Cast at an entity to pull them toward you.");
                desc.add(org.bukkit.ChatColor.GOLD + "3s cooldown | Fishing Rod only.");
            }
            case CorovaEnchantments.DIVINUM_TRABEM_ID -> {
                switch (level) {
                    case 1 -> desc.add(org.bukkit.ChatColor.GOLD + "50% speed | 50% damage | No rings");
                    case 2 -> desc.add(org.bukkit.ChatColor.GOLD + "75% speed | 75% damage | Fewer rings");
                    case 3 -> desc.add(org.bukkit.ChatColor.GOLD + "100% speed | 100% damage | Full rings");
                }
            }
            case CorovaEnchantments.COSMIC_RAY_ID -> {
                switch (level) {
                    case 1 -> desc.add(org.bukkit.ChatColor.GOLD + "Slow | No split");
                    case 2 -> desc.add(org.bukkit.ChatColor.GOLD + "Medium speed | Splits into 2-3");
                    case 3 -> desc.add(org.bukkit.ChatColor.GOLD + "Fast speed | Splits into 5-6");
                    case 4 -> desc.add(org.bukkit.ChatColor.GOLD + "Very fast speed | Splits into 10-12");
                }
            }
            case CorovaEnchantments.FREEZE_ID -> {
                switch (level) {
                    case 1 -> desc.add(org.bukkit.ChatColor.GOLD + "Radius: 5 | Duration: 2s | Speed: 1");
                    case 2 -> desc.add(org.bukkit.ChatColor.GOLD + "Radius: 7 | Duration: 3s | Speed: 1");
                    case 3 -> desc.add(org.bukkit.ChatColor.GOLD + "Radius: 9 | Duration: 4s | Speed: 2 | 1.0× Dmg");
                    case 4 -> desc.add(org.bukkit.ChatColor.GOLD + "Radius: 11 | Duration: 5s | Speed: 2 | 2.0× Dmg");
                    case 5 -> desc.add(org.bukkit.ChatColor.GOLD + "Radius: 14 | Duration: 6s | Speed: 3 | 3.0× Dmg | Post-Slowness");
                }
                desc.add(org.bukkit.ChatColor.GOLD + "10s Cooldown");
            }
            case CorovaEnchantments.SNOWSTORM_ID -> {
                switch (level) {
                    case 1 -> {
                        desc.add(org.bukkit.ChatColor.AQUA + "Blind: 7s | Slow II: 5s | 2.0× Dmg");
                        desc.add(org.bukkit.ChatColor.AQUA + "Speed: 1");
                    }
                    case 2 -> {
                        desc.add(org.bukkit.ChatColor.AQUA + "Blind: 7s | Slow II: 7s | 2.5× Dmg");
                        desc.add(org.bukkit.ChatColor.AQUA + "Speed: 1");
                    }
                    case 3 -> {
                        desc.add(org.bukkit.ChatColor.AQUA + "Blind: 7s | Slow II: 7s | Weak I: 7s | 3.0× Dmg");
                        desc.add(org.bukkit.ChatColor.AQUA + "Speed: 2 | Block-break particles");
                    }
                    case 4 -> {
                        desc.add(org.bukkit.ChatColor.AQUA + "Blind: 7s | Slow III: 7s | Weak II: 7s | 3.5× Dmg");
                        desc.add(org.bukkit.ChatColor.AQUA + "Frostbite: 5s | Speed: 2");
                    }
                    case 5 -> {
                        desc.add(org.bukkit.ChatColor.AQUA + "Blind: 7s | Slow III: 7s | Weak II: 7s | 4.0× Dmg");
                        desc.add(org.bukkit.ChatColor.AQUA + "Frostbite: 7s | Speed: 3");
                    }
                }
                desc.add(org.bukkit.ChatColor.AQUA + "Sneak to charge (up to 10 block radius) | 15s Cooldown");
            }
            case CorovaEnchantments.KNOCKBACK_PROTECTION_ID -> {
                desc.add(org.bukkit.ChatColor.GOLD + "Reduces knockback taken. Blocks up to 90%");
                desc.add(org.bukkit.ChatColor.GOLD + "at Max Level VII across all armor pieces.");
            }
            case CorovaEnchantments.SOUL_FIRE_ASPECT_ID -> {
                switch (level) {
                    case 1 -> desc.add(org.bukkit.ChatColor.GOLD + "2.0× normal fire damage");
                    case 2 -> desc.add(org.bukkit.ChatColor.GOLD + "3.0× normal fire damage");
                    case 3 -> desc.add(org.bukkit.ChatColor.GOLD + "4.0× normal fire damage");
                }
                desc.add(org.bukkit.ChatColor.GOLD + "Ignores fire resistance and protection.");
            }
            case CorovaEnchantments.STEALTH_STEP_ID -> {
                switch (level) {
                    case 1 -> desc.add(org.bukkit.ChatColor.GOLD + "Walking is silent.");
                    case 2 -> desc.add(org.bukkit.ChatColor.GOLD + "Walking and running are silent.");
                }
            }
            case CorovaEnchantments.CRITICAL_ID -> {
                switch (level) {
                    case 1 -> desc.add(org.bukkit.ChatColor.GOLD + "+5% weapon base bonus on jump crits.");
                    case 2 -> desc.add(org.bukkit.ChatColor.GOLD + "+10% weapon base bonus on jump crits.");
                    case 3 -> desc.add(org.bukkit.ChatColor.GOLD + "+15% weapon base bonus on jump crits.");
                    case 4 -> desc.add(org.bukkit.ChatColor.GOLD + "+20% weapon base bonus on jump crits.");
                    case 5 -> desc.add(org.bukkit.ChatColor.GOLD + "+25% weapon base bonus on jump crits.");
                    case 6 -> desc.add(org.bukkit.ChatColor.GOLD + "+30% weapon base bonus on jump crits.");
                }
            }
            case CorovaEnchantments.POISON_ID -> desc.add(org.bukkit.ChatColor.GOLD + "Applies Poison effect for 8s.");
            case CorovaEnchantments.WITHER_ID -> desc.add(org.bukkit.ChatColor.GOLD + "Applies Wither effect for 8s.");
            case CorovaEnchantments.TELEPORT_ID -> desc.add(org.bukkit.ChatColor.GOLD + "Right-click to teleport forward.");
            case CorovaEnchantments.FLIGHT_ID -> desc.add(org.bukkit.ChatColor.GOLD + "Allows flight while holding item.");
            case CorovaEnchantments.HASTE_ID -> desc.add(org.bukkit.ChatColor.GOLD + "Right-click to gain Haste effect.");
            case CorovaEnchantments.VEIN_MINER_ID -> desc.add(org.bukkit.ChatColor.GOLD + "Mines entire ore vein while sneaking.");
            case CorovaEnchantments.BOOMERANG_ID -> desc.add(org.bukkit.ChatColor.GOLD + "Right-click to throw weapon like a boomerang.");
            case CorovaEnchantments.DOUBLE_JUMP_ID -> desc.add(org.bukkit.ChatColor.GOLD + "Allows for a double jump while wearing boots.");
            case CorovaEnchantments.STEED_ID -> desc.add(org.bukkit.ChatColor.GOLD + "Right-click to summon a temporary skeletal steed.");
            case CorovaEnchantments.PAYDAY_ID -> desc.add(org.bukkit.ChatColor.GOLD + "Steals a percentage of the victim's balance on kill.");
            case CorovaEnchantments.THRUST_ID -> desc.add(org.bukkit.ChatColor.GOLD + "Sneak while flying with elytra to gain a boost.");
            case CorovaEnchantments.FLARE_ID -> desc.add(org.bukkit.ChatColor.GOLD + "Right-click to launch a signal flare.");
            case CorovaEnchantments.SOUL_EXTRACTION_ID -> desc.add(org.bukkit.ChatColor.GOLD + "Launches a soul projectile that restores mana on hit.");
            case CorovaEnchantments.FANG_STRIKE_ID -> desc.add(org.bukkit.ChatColor.GOLD + "Right-click to summon Evoker Fangs in a line.");
            case CorovaEnchantments.WATER_BALL_ID -> desc.add(org.bukkit.ChatColor.GOLD + "Right-click to launch a water ball.");
            case CorovaEnchantments.NAPALM_ID -> desc.add(org.bukkit.ChatColor.GOLD + "Arrows create fire on impact.");
            case CorovaEnchantments.SOUL_PROJECTION_ID -> desc.add(org.bukkit.ChatColor.GOLD + "Launches a soul projectile.");
            case CorovaEnchantments.NIGHT_VISION_ID -> desc.add(org.bukkit.ChatColor.GOLD + "Grants Night Vision while holding item.");
            case CorovaEnchantments.ENDER_MISSILE_ID -> desc.add(org.bukkit.ChatColor.GOLD + "Launches an ender missile projectile.");
        }
        return desc;
    }




    @Override public ItemStack getItemStack() { return createEnchantmentBook(JavaPlugin.getProvidingPlugin(this.getClass()), this.level); }
    @Override public ItemStack toItemStack()  { return createEnchantmentBook(JavaPlugin.getProvidingPlugin(this.getClass()), this.level); }

    public ItemStack createBookStack(int level) {
        return createEnchantmentBook(JavaPlugin.getProvidingPlugin(this.getClass()), level);
    }

    private ItemStack createEnchantmentBook(JavaPlugin plugin, int level) {
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta storageMeta = (EnchantmentStorageMeta) item.getItemMeta();
        if (storageMeta == null) {
            Bukkit.getLogger().warning("Failed to get EnchantmentStorageMeta for " + enchantId);
            return item;
        }
        String label = CorovaEnchantments.DISPLAY_NAME.getOrDefault(enchantId, enchantId);
        storageMeta.setDisplayName(applyEnchantmentGradient(enchantId, "Book of " + label));
        storageMeta.setLore(buildLore(enchantId, level));
        storageMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        storageMeta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        NamespacedKey keyId  = new NamespacedKey(plugin, "corova_enchant_book_id");
        NamespacedKey keyLvl = new NamespacedKey(plugin, "corova_enchant_book_level");
        storageMeta.getPersistentDataContainer().set(keyId,  PersistentDataType.STRING,  enchantId);
        storageMeta.getPersistentDataContainer().set(keyLvl, PersistentDataType.INTEGER, level);
        item.setItemMeta(storageMeta);
        return item;
    }

    public String getEnchantId()              { return enchantId; }
    public int getLevel()                     { return level; }
    public Set<Material> getAllowedMaterials() { return allowedMaterials; }

    /**
     * Resolves the base weapon damage for an entity, including all attribute
     * modifiers (e.g., base damage, item modifiers like Netherite, etc.).
     * Enchantments like Sharpness are NOT included in this attribute value;
     * they are added during the damage event.
     */
    public static double getWeaponDamage(LivingEntity entity) {
        if (entity == null) return 1.0;
        AttributeInstance attr = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        return (attr != null) ? attr.getValue() : 1.0;
    }

    public boolean canApplyTo(Material material) { return allowedMaterials.contains(material); }
    public boolean canApplyTo(ItemStack stack) {
        if (stack == null) return false;
        return canApplyTo(stack.getType());
    }

    public static void registerAllBooks(JavaPlugin plugin) {
        // ── Original single-level books ───────────────────────────────────────
        Bukkit.getPluginManager().registerEvents(new WitherBook(),         plugin);
        Bukkit.getPluginManager().registerEvents(new PoisonBook(),         plugin);
        Bukkit.getPluginManager().registerEvents(new TeleportBook(),       plugin);
        Bukkit.getPluginManager().registerEvents(new SoulProjection(),     plugin);
        Bukkit.getPluginManager().registerEvents(new NightVisionBook(),    plugin);
        // Freeze — single listener instance only; level is read from weapon at runtime.
        Bukkit.getPluginManager().registerEvents(new FreezeBook(1), plugin);
        for (int i = 2; i <= 5; i++) {
            new FreezeBook(i);
        }
        Bukkit.getPluginManager().registerEvents(new FlightBook(),         plugin);
        Bukkit.getPluginManager().registerEvents(new FangStrike(),         plugin);
        Bukkit.getPluginManager().registerEvents(new ExplosiveRoundBook(), plugin);
        Bukkit.getPluginManager().registerEvents(new EnderMissileBook(),   plugin);
        Bukkit.getPluginManager().registerEvents(new WaterBallBook(),      plugin);
        Bukkit.getPluginManager().registerEvents(new NapalmBook(),         plugin);
        Bukkit.getPluginManager().registerEvents(new StormBook(),          plugin);
        // Arrow Rain — single listener instance only; level is read from the weapon at runtime
        Bukkit.getPluginManager().registerEvents(new ArrowRainBook(),      plugin);
        // Still instantiate levels 2 and 3 so their book items can be created/given,
        // but do NOT register them as listeners.
        new ArrowRainBook(2);
        new ArrowRainBook(3);
        // ── Mage system ────────────────────────────────────────────────────────
        Bukkit.getPluginManager().registerEvents(new SoulExtractionBook(), plugin);
        // ── Pickaxe books ──────────────────────────────────────────────────────
        Bukkit.getPluginManager().registerEvents(new VeinMinerBook(),      plugin);
        Bukkit.getPluginManager().registerEvents(new HasteBook(),          plugin);
        // ── Weapon-ability books ───────────────────────────────────────────────
        Bukkit.getPluginManager().registerEvents(new BoomerangBook(),      plugin);
        // SnowStorm — single listener instance only; level is read from weapon at runtime.
        Bukkit.getPluginManager().registerEvents(new SnowStormBook(1),    plugin);
        for (int i = 2; i <= 5; i++) {
            new SnowStormBook(i);
        }
        // Music — single listener instance only; level is read from weapon at runtime.
        Bukkit.getPluginManager().registerEvents(new MusicBook(1), plugin);
        for (int i = 2; i <= MusicBook.MAX_LEVEL; i++) {
            new MusicBook(i);
        }
        Bukkit.getPluginManager().registerEvents(new DoubleJumpBook(),     plugin);
        Bukkit.getPluginManager().registerEvents(new SteedBook(1),         plugin);
        Bukkit.getPluginManager().registerEvents(new SteedBook(2),         plugin);
        Bukkit.getPluginManager().registerEvents(new SteedBook(3),         plugin);
        // ── Economy book ───────────────────────────────────────────────────────
        Bukkit.getPluginManager().registerEvents(new PaydayBook(),         plugin);
        // ── Elytra / Bow books ─────────────────────────────────────────────────
        Bukkit.getPluginManager().registerEvents(new ThrustBook(),         plugin);
        Bukkit.getPluginManager().registerEvents(new Flare(),              plugin);
        Bukkit.getPluginManager().registerEvents(new Missile(),            plugin);
        // ── Fishing rod books ──────────────────────────────────────────────────
        Bukkit.getPluginManager().registerEvents(new HookBook(),           plugin);
        // ── Wand books ─────────────────────────────────────────────────────────
        Bukkit.getPluginManager().registerEvents(new DivinumTrabemBook(1), plugin);
        Bukkit.getPluginManager().registerEvents(new DivinumTrabemBook(2), plugin);
        Bukkit.getPluginManager().registerEvents(new DivinumTrabemBook(3), plugin);
        Bukkit.getPluginManager().registerEvents(new CosmicRay(1),         plugin);
        Bukkit.getPluginManager().registerEvents(new CosmicRay(2),         plugin);
        Bukkit.getPluginManager().registerEvents(new CosmicRay(3),         plugin);
        Bukkit.getPluginManager().registerEvents(new CosmicRay(4),         plugin);

        // ── Multi-level books (I, II, III) ─────────────────────────────────────
        // Launch — single listener instance only; level is read from weapon at runtime.
        Bukkit.getPluginManager().registerEvents(new LaunchBook(1),        plugin);
        new LaunchBook(2);
        new LaunchBook(3);
        // Beam
        Bukkit.getPluginManager().registerEvents(new BeamBook(1),          plugin);
        Bukkit.getPluginManager().registerEvents(new BeamBook(2),          plugin);
        Bukkit.getPluginManager().registerEvents(new BeamBook(3),          plugin);
        // Lightning
        Bukkit.getPluginManager().registerEvents(new LightningBook(1),     plugin);
        Bukkit.getPluginManager().registerEvents(new LightningBook(2),     plugin);
        Bukkit.getPluginManager().registerEvents(new LightningBook(3),     plugin);
        Bukkit.getPluginManager().registerEvents(new LightningBook(4),     plugin);
        Bukkit.getPluginManager().registerEvents(new LightningBook(5),     plugin);
        // Web Slinger
        Bukkit.getPluginManager().registerEvents(new WebSlingBook(1),      plugin);
        Bukkit.getPluginManager().registerEvents(new WebSlingBook(2),      plugin);
        Bukkit.getPluginManager().registerEvents(new WebSlingBook(3),      plugin);

        // Knockback Protection
        for (int i = 1; i <= 7; i++) {
            Bukkit.getPluginManager().registerEvents(new KnockbackProtectionBook(i), plugin);
        }
        // Soul Fire Aspect
        for (int i = 1; i <= 3; i++) {
            Bukkit.getPluginManager().registerEvents(new SoulFireAspectBook(i), plugin);
        }
        // Stealth Step — single listener instance only; level is read from boots at runtime.
        StealthStepBook stealthStep = new StealthStepBook(1);
        Bukkit.getPluginManager().registerEvents(stealthStep, plugin);
        stealthStep.initProtocolLib(plugin);
        new StealthStepBook(2);
        // Critical — single listener instance only; level is read from the weapon at runtime
        Bukkit.getPluginManager().registerEvents(new CriticalBook(), plugin);
        // Still instantiate levels 2–6 so their book items can be created/given,
        // but do NOT register them as listeners.
        new CriticalBook(2);
        new CriticalBook(3);
        new CriticalBook(4);
        new CriticalBook(5);
        new CriticalBook(6);

        ThrustBook.startTask(plugin);
        Missile.startTask(plugin);
    }

    // =========================================================================
    // Gradient Logic
    // =========================================================================

    public static String applyEnchantmentGradient(String enchantId, String text) {
        Color[] colors = getEnchantmentColors(enchantId);
        if (colors.length == 0) return text;
        if (colors.length == 1) return net.md_5.bungee.api.ChatColor.of(toHex(colors[0])) + text;

        StringBuilder sb = new StringBuilder();
        int length = text.length();
        for (int i = 0; i < length; i++) {
            float ratio = (float) i / (float) (length - 1 == 0 ? 1 : length - 1);
            Color interpolated = interpolate(ratio, colors);
            sb.append(net.md_5.bungee.api.ChatColor.of(toHex(interpolated))).append(text.charAt(i));
        }
        return sb.toString();
    }

    public static Component applyEnchantmentGradientComponent(String enchantId, String text) {
        return LegacyComponentSerializer.legacySection().deserialize(applyEnchantmentGradient(enchantId, text));
    }

    private static Color interpolate(float ratio, Color... colors) {
        if (ratio <= 0) return colors[0];
        if (ratio >= 1) return colors[colors.length - 1];

        float segmentSize = 1.0f / (colors.length - 1 == 0 ? 1 : colors.length - 1);
        int segment = (int) (ratio / segmentSize);
        float segmentRatio = (ratio % segmentSize) / segmentSize;

        if (segment >= colors.length - 1) return colors[colors.length - 1];

        Color c1 = colors[segment];
        Color c2 = colors[segment + 1];

        return Color.fromRGB(
                (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * segmentRatio),
                (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * segmentRatio),
                (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * segmentRatio)
        );
    }

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static Color[] getEnchantmentColors(String enchantId) {
        return switch (enchantId) {
            case CorovaEnchantments.POISON_ID -> new Color[]{Color.fromRGB(124, 252, 0), Color.fromRGB(50, 205, 50), Color.fromRGB(0, 100, 0)};
            case CorovaEnchantments.WITHER_ID -> new Color[]{Color.fromRGB(100, 100, 100), Color.fromRGB(70, 70, 70), Color.fromRGB(100, 100, 100)};
            case CorovaEnchantments.LIGHTNING_ID -> new Color[]{Color.fromRGB(255, 255, 0), Color.fromRGB(255, 255, 255), Color.fromRGB(255, 165, 0)};
            case CorovaEnchantments.FREEZE_ID -> new Color[]{Color.fromRGB(0, 255, 255), Color.fromRGB(175, 238, 238), Color.fromRGB(255, 255, 255)};
            case CorovaEnchantments.TELEPORT_ID -> new Color[]{Color.fromRGB(147, 112, 219), Color.fromRGB(255, 0, 255), Color.fromRGB(120, 60, 180)};
            case CorovaEnchantments.FLIGHT_ID -> new Color[]{Color.fromRGB(0, 191, 255), Color.fromRGB(240, 248, 255), Color.fromRGB(135, 206, 250)};
            case CorovaEnchantments.LAUNCH_ID -> new Color[]{Color.fromRGB(192, 192, 192), Color.fromRGB(255, 255, 255), Color.fromRGB(128, 128, 128)};
            case CorovaEnchantments.FANG_STRIKE_ID -> new Color[]{Color.fromRGB(180, 180, 180), Color.fromRGB(160, 110, 60), Color.fromRGB(100, 100, 100)};
            case CorovaEnchantments.BEAM_ID -> new Color[]{Color.fromRGB(255, 105, 180), Color.fromRGB(255, 20, 147), Color.fromRGB(199, 21, 133)};
            case CorovaEnchantments.EXPLOSIVE_ROUND_ID -> new Color[]{Color.fromRGB(255, 69, 0), Color.fromRGB(255, 80, 80), Color.fromRGB(100, 50, 50)};
            case CorovaEnchantments.ENDER_MISSILE_ID -> new Color[]{Color.fromRGB(180, 100, 255), Color.fromRGB(130, 50, 200), Color.fromRGB(150, 80, 255)};
            case CorovaEnchantments.WATER_BALL_ID -> new Color[]{Color.fromRGB(0, 0, 255), Color.fromRGB(0, 255, 255), Color.fromRGB(30, 144, 255)};
            case CorovaEnchantments.NAPALM_ID -> new Color[]{Color.fromRGB(255, 0, 0), Color.fromRGB(255, 165, 0), Color.fromRGB(255, 255, 0)};
            case CorovaEnchantments.STORM_ID -> new Color[]{Color.fromRGB(50, 100, 200), Color.fromRGB(100, 150, 255), Color.fromRGB(192, 192, 192)};
            case CorovaEnchantments.ARROW_RAIN_ID -> new Color[]{Color.fromRGB(255, 215, 0), Color.fromRGB(255, 255, 0), Color.fromRGB(255, 140, 0)};
            case CorovaEnchantments.WEB_SLING_ID -> new Color[]{Color.fromRGB(255, 255, 255), Color.fromRGB(220, 220, 220), Color.fromRGB(211, 211, 211)};
            case CorovaEnchantments.HOOK_ID -> new Color[]{Color.fromRGB(180, 120, 70), Color.fromRGB(160, 160, 160), Color.fromRGB(100, 150, 150)};
            case CorovaEnchantments.SOUL_EXTRACTION_ID -> new Color[]{Color.fromRGB(0, 255, 255), Color.fromRGB(0, 139, 139), Color.fromRGB(50, 100, 255)};
            case CorovaEnchantments.VEIN_MINER_ID -> new Color[]{Color.fromRGB(139, 69, 19), Color.fromRGB(160, 82, 45), Color.fromRGB(165, 42, 42)};
            case CorovaEnchantments.HASTE_ID -> new Color[]{Color.fromRGB(255, 255, 0), Color.fromRGB(255, 215, 0), Color.fromRGB(255, 165, 0)};
            case CorovaEnchantments.BOOMERANG_ID -> new Color[]{Color.fromRGB(222, 184, 135), Color.fromRGB(139, 69, 19), Color.fromRGB(160, 82, 45)};
            case CorovaEnchantments.SNOWSTORM_ID -> new Color[]{Color.fromRGB(255, 255, 255), Color.fromRGB(240, 248, 255), Color.fromRGB(0, 255, 255)};
            case CorovaEnchantments.MUSIC_ID -> new Color[]{Color.fromRGB(255, 20, 147), Color.fromRGB(147, 112, 219), Color.fromRGB(0, 191, 255)};
            case CorovaEnchantments.DOUBLE_JUMP_ID -> new Color[]{Color.fromRGB(255, 255, 255), Color.fromRGB(0, 255, 255), Color.fromRGB(32, 178, 170)};
            case CorovaEnchantments.STEED_ID -> new Color[]{Color.fromRGB(245, 245, 220), Color.fromRGB(139, 69, 19), Color.fromRGB(139, 69, 19)};
            case CorovaEnchantments.PAYDAY_ID -> new Color[]{Color.fromRGB(255, 215, 0), Color.fromRGB(50, 205, 50), Color.fromRGB(0, 255, 127)};
            case CorovaEnchantments.THRUST_ID -> new Color[]{Color.fromRGB(255, 69, 0), Color.fromRGB(255, 255, 0), Color.fromRGB(255, 215, 0)};
            case CorovaEnchantments.FLARE_ID -> new Color[]{Color.fromRGB(255, 0, 0), Color.fromRGB(255, 255, 0), Color.fromRGB(255, 255, 255)};
            case CorovaEnchantments.MISSILE_ID -> new Color[]{Color.fromRGB(100, 100, 100), Color.fromRGB(255, 0, 0), Color.fromRGB(100, 50, 50)};
            case CorovaEnchantments.DIVINUM_TRABEM_ID -> new Color[]{Color.fromRGB(255, 200, 220), Color.fromRGB(200, 255, 210), Color.fromRGB(200, 190, 255)};
            case CorovaEnchantments.COSMIC_RAY_ID -> new Color[]{Color.fromRGB(200, 100, 255), Color.fromRGB(150, 50, 250), Color.fromRGB(120, 0, 200)};
            case CorovaEnchantments.KNOCKBACK_PROTECTION_ID -> new Color[]{Color.fromRGB(150, 170, 190), Color.fromRGB(80, 80, 80), Color.fromRGB(100, 150, 150)};
            case CorovaEnchantments.SOUL_FIRE_ASPECT_ID -> new Color[]{Color.fromRGB(0, 191, 255), Color.fromRGB(30, 144, 255), Color.fromRGB(0, 255, 255)};
            case CorovaEnchantments.STEALTH_STEP_ID -> new Color[]{Color.fromRGB(0, 0, 0), Color.fromRGB(47, 79, 79), Color.fromRGB(105, 105, 105)};
            case CorovaEnchantments.CRITICAL_ID -> new Color[]{Color.fromRGB(255, 0, 0), Color.fromRGB(128, 0, 0), Color.fromRGB(220, 20, 60)};
            case CorovaEnchantments.SOUL_PROJECTION_ID -> new Color[]{Color.fromRGB(0, 255, 255), Color.fromRGB(0, 0, 139), Color.fromRGB(0, 128, 128)};
            case CorovaEnchantments.NIGHT_VISION_ID -> new Color[]{Color.fromRGB(25, 25, 112), Color.fromRGB(50, 205, 50), Color.fromRGB(0, 100, 0)};
            default -> new Color[]{Color.fromRGB(128, 128, 128), Color.fromRGB(255, 255, 255)};
        };
    }
}