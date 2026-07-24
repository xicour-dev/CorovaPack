package com.example.corovaItems.Enchantments;

import com.example.corovaItems.Enchantments.EnchantBooks.*;
import com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.CustomEnchantMutationFormatting;
import com.example.corovaItems.ItemMutations.MutationManager;
import com.example.corovaItems.ItemMutations.MutationType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

/**
 * Central registry and utility class for custom Corova enchantments.
 *
 * NOTE: Multi-target and multi-trigger fixes for Freeze, Music, Snowstorm,
 * and Launch are implemented in their respective book classes using a
 * single-listener pattern. This ensures that weapon enchantments (e.g., Sharpness)
 * are factored into total damage and that right-click or melee effects do
 * not execute multiple times across different level instances.
 */
public final class CorovaEnchantments {

    public static final String LAUNCH_ID          = "launch";
    public static final String TELEPORT_ID        = "teleport";
    public static final String FREEZE_ID          = "freeze";
    public static final String FLIGHT_ID          = "flight";
    public static final String LIGHTNING_ID       = "lightning";
    public static final String POISON_ID          = "poison";
    public static final String WITHER_ID          = "wither";
    public static final String SOUL_PROJECTION_ID = "soulprojection";
    public static final String FANG_STRIKE_ID     = "fangstrike";
    public static final String BEAM_ID            = "beam";
    public static final String EXPLOSIVE_ROUND_ID = "explosiveround";
    public static final String ENDER_MISSILE_ID   = "endermissile";
    public static final String WATER_BALL_ID      = "waterball";
    public static final String NAPALM_ID          = "napalm";
    public static final String STORM_ID           = "storm";
    public static final String ARROW_RAIN_ID      = "arrowrain";
    public static final String WEB_SLING_ID       = "websling";
    /** Restores 1.8-style fishing-rod PvP: hooking an entity pulls them toward you. Fishing Rod only. */
    public static final String HOOK_ID            = "hook";
    // ── Mage system enchantment ───────────────────────────────────────────────
    /** Launches a soul projectile; costs 30 mana, restores 60 on mob hit. Wand-only. */
    public static final String SOUL_EXTRACTION_ID = "soulextraction";
    // ── Pickaxe enchantments ──────────────────────────────────────────────────
    /** Mines entire ore vein when player is sneaking. Pickaxe-only. */
    public static final String VEIN_MINER_ID      = "veinminer";
    /** Right-click to gain +1 block break speed for 30s. 60s cooldown. Pickaxe-only. */
    public static final String HASTE_ID           = "haste";
    // ── New weapon-ability enchantments ───────────────────────────────────────
    /** Throw the enchanted weapon; it bounces and returns like a boomerang. Sword/axe. */
    public static final String BOOMERANG_ID       = "boomerang";
    /** Charge by sneaking to unleash a blinding snowstorm around you. Sword/axe. */
    public static final String SNOWSTORM_ID       = "snowstorm";
    /** Charge by sneaking to play a damaging music blast. Right-click + sneak to pick song. Sword/axe. */
    public static final String MUSIC_ID           = "music";
    /** Grants a double-jump when worn as boots. Boots-only. */
    public static final String DOUBLE_JUMP_ID     = "doublejump";
    /** Right-click to summon a tamed skeleton horse for 3 minutes. 15s cooldown. Sword/axe/trident. */
    public static final String STEED_ID           = "steed";
    // ── Economy enchantment ───────────────────────────────────────────────────
    /** Steals 10% × level of victim's deposited balance on kill. Sword/axe/trident/mace. */
    public static final String PAYDAY_ID          = "payday";
    public static final String THRUST_ID          = "thrust";
    public static final String FLARE_ID           = "flare";
    public static final String MISSILE_ID         = "missile";
    // ── Wand enchantments ─────────────────────────────────────────────────────
    /** Fires a prismatic divine beam down the player's line of sight. Wand-only. */
    public static final String DIVINUM_TRABEM_ID  = "divinumtrabem";
    /** Fires a cosmic ray projectile. Wand-only. */
    public static final String COSMIC_RAY_ID   = "cosmicray";
    public static final String NIGHT_VISION_ID = "nightvision";

    public static final String KNOCKBACK_PROTECTION_ID = "knockbackprotection";
    public static final String SOUL_FIRE_ASPECT_ID     = "soulfireaspect";
    public static final String STEALTH_STEP_ID         = "stealthstep";
    public static final String CRITICAL_ID             = "critical";

    public static NamespacedKey KEY_ENCHANT_ID;
    public static NamespacedKey KEY_ENCHANT_LVL;
    public static NamespacedKey KEY_ENCHANT_2_ID;
    public static NamespacedKey KEY_ENCHANT_2_LVL;

    public static final Map<String, String> DISPLAY_NAME = new HashMap<>();

    static {
        DISPLAY_NAME.put(LAUNCH_ID,          "Launcher");
        DISPLAY_NAME.put(TELEPORT_ID,        "Teleport");
        DISPLAY_NAME.put(FREEZE_ID,          "Freeze");
        DISPLAY_NAME.put(FLIGHT_ID,          "Flight");
        DISPLAY_NAME.put(LIGHTNING_ID,       "Lightning");
        DISPLAY_NAME.put(POISON_ID,          "Poison");
        DISPLAY_NAME.put(WITHER_ID,          "Wither");
        DISPLAY_NAME.put(SOUL_PROJECTION_ID, "Soul Projection");
        DISPLAY_NAME.put(FANG_STRIKE_ID,     "Fang Strike");
        DISPLAY_NAME.put(BEAM_ID,            "Beam");
        DISPLAY_NAME.put(EXPLOSIVE_ROUND_ID, "Explosive Round");
        DISPLAY_NAME.put(ENDER_MISSILE_ID,   "Ender Missile");
        DISPLAY_NAME.put(WATER_BALL_ID,      "Water Ball");
        DISPLAY_NAME.put(NAPALM_ID,          "Napalm");
        DISPLAY_NAME.put(STORM_ID,           "Storm");
        DISPLAY_NAME.put(ARROW_RAIN_ID,      "Arrow Rain");
        DISPLAY_NAME.put(WEB_SLING_ID,       "Web Slinger");
        DISPLAY_NAME.put(HOOK_ID,            "Hook");
        DISPLAY_NAME.put(SOUL_EXTRACTION_ID, "Soul Extraction");
        DISPLAY_NAME.put(VEIN_MINER_ID,      "Vein Miner");
        DISPLAY_NAME.put(HASTE_ID,           "Haste");
        // ── New ───────────────────────────────────────────────────────────────
        DISPLAY_NAME.put(BOOMERANG_ID,       "Boomerang");
        DISPLAY_NAME.put(SNOWSTORM_ID,       "Snowstorm");
        DISPLAY_NAME.put(MUSIC_ID,           "Music");
        DISPLAY_NAME.put(DOUBLE_JUMP_ID,     "Double Jump");
        DISPLAY_NAME.put(STEED_ID,           "Steed");
        // ── Economy ───────────────────────────────────────────────────────────
        DISPLAY_NAME.put(PAYDAY_ID,          "Payday");
        DISPLAY_NAME.put(THRUST_ID,          "Thrust");
        DISPLAY_NAME.put(FLARE_ID,           "Flare");
        DISPLAY_NAME.put(MISSILE_ID,         "Missile");
        // ── Wand enchantments ─────────────────────────────────────────────────
        DISPLAY_NAME.put(DIVINUM_TRABEM_ID,  "Divinum Trabem");
        DISPLAY_NAME.put(COSMIC_RAY_ID,      "Cosmic Ray");
        DISPLAY_NAME.put(NIGHT_VISION_ID,    "Night Vision");

        DISPLAY_NAME.put(KNOCKBACK_PROTECTION_ID, "Knockback Protection");
        DISPLAY_NAME.put(SOUL_FIRE_ASPECT_ID,     "Soul Fire Aspect");
        DISPLAY_NAME.put(STEALTH_STEP_ID,         "Stealth Step");
        DISPLAY_NAME.put(CRITICAL_ID,             "Critical");
    }

    private CorovaEnchantments() {}

    public static void init(JavaPlugin plugin) {
        if (plugin == null) throw new IllegalArgumentException("plugin cannot be null");
        KEY_ENCHANT_ID    = new NamespacedKey(plugin, "corova_enchant_id");
        KEY_ENCHANT_LVL   = new NamespacedKey(plugin, "corova_enchant_level");
        KEY_ENCHANT_2_ID  = new NamespacedKey(plugin, "corova_enchant_2_id");
        KEY_ENCHANT_2_LVL = new NamespacedKey(plugin, "corova_enchant_2_level");
    }

    private static void ensureInit() {
        if (KEY_ENCHANT_ID == null || KEY_ENCHANT_LVL == null
                || KEY_ENCHANT_2_ID == null || KEY_ENCHANT_2_LVL == null) {
            throw new IllegalStateException(
                    "CorovaEnchantments not initialized. Call CorovaEnchantments.init(plugin) in onEnable.");
        }
    }

    public static ItemStack applyEnchant(ItemStack stack, String enchantId, int level) {
        return applyEnchant(stack, enchantId, level, 1);
    }

    public static ItemStack applyEnchant(ItemStack stack, String enchantId, int level, int slot) {
        ensureInit();
        if (stack == null || enchantId == null) return stack;
        if (level <= 0) level = 1;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey idKey  = (slot == 2) ? KEY_ENCHANT_2_ID  : KEY_ENCHANT_ID;
        NamespacedKey lvlKey = (slot == 2) ? KEY_ENCHANT_2_LVL : KEY_ENCHANT_LVL;
        pdc.set(idKey,  PersistentDataType.STRING,  enchantId);
        pdc.set(lvlKey, PersistentDataType.INTEGER, level);

        // If applying to slot 1 and the extra slot mutation is missing, ensure slot 2 is cleared.
        if (slot == 1 && !MutationManager.getInstance().hasMutation(stack, MutationType.EXTRA_CUSTOM_ENCHANT_SLOT)) {
            pdc.remove(KEY_ENCHANT_2_ID);
            pdc.remove(KEY_ENCHANT_2_LVL);
        }

        // Lore rebuilding is now handled incrementally by CustomEnchantMutationFormatting
        // and VanillaEnchantDisplay to ensure canonical order and intelligent spacing.
        CustomEnchantMutationFormatting.rebuildLore(stack, meta);
        com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.VanillaEnchantDisplay.refreshDisplay(stack, meta);

        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Intelligently applies an enchant by selecting an appropriate slot.
     * Updates the level if the enchant already exists, otherwise fills slot 1
     * (or slot 2 if unlocked and available). Overwrites slot 1 as a fallback.
     */
    public static ItemStack applyEnchantAutoSlot(ItemStack stack, String enchantId, int level) {
        ensureInit();
        if (stack == null || enchantId == null) return stack;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String id1 = pdc.get(KEY_ENCHANT_ID, PersistentDataType.STRING);
        String id2 = pdc.get(KEY_ENCHANT_2_ID, PersistentDataType.STRING);

        boolean hasExtraSlot = MutationManager.getInstance().hasMutation(stack, MutationType.EXTRA_CUSTOM_ENCHANT_SLOT);

        int slot = 1;
        if (enchantId.equalsIgnoreCase(id1)) {
            slot = 1;
        } else if (hasExtraSlot && enchantId.equalsIgnoreCase(id2)) {
            slot = 2;
        } else if (id1 == null) {
            slot = 1;
        } else if (id2 == null && hasExtraSlot) {
            slot = 2;
        } else {
            slot = 1; // Overwrite first slot if both are full or second is locked
        }
        return applyEnchant(stack, enchantId, level, slot);
    }

    /**
     * Intelligently applies an enchant by selecting an appropriate slot.
     * Delegates to applyEnchantAutoSlot for backward compatibility and ease of use.
     * @return the updated ItemStack
     */
    public static ItemStack addEnchant(ItemStack stack, String enchantId, int level) {
        return applyEnchantAutoSlot(stack, enchantId, level);
    }

    /**
     * Rebuilds lore in canonical order (base → custom enchants → mutations).
     * Delegates to {@link CustomEnchantMutationFormatting#rebuildLore} so that
     * applying an enchant after a mutation never pushes the enchant below it.
     *
     * @param meta the ItemMeta whose PDC already has the enchant data written
     */
    public static void refreshLore(ItemMeta meta) {
        if (meta == null) return;
        // Legacy signature: reconstruct a minimal temp stack for PDC access.
        org.bukkit.inventory.ItemStack tempStack = new org.bukkit.inventory.ItemStack(
                org.bukkit.Material.AIR);
        CustomEnchantMutationFormatting.rebuildLore(tempStack, meta);
        com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.VanillaEnchantDisplay.refreshDisplay(tempStack, meta);
    }

    /**
     * Preferred overload when both the stack and its meta are available.
     * Enchant data must already be written into {@code meta}'s PDC before calling.
     */
    public static void refreshLore(ItemStack stack, ItemMeta meta) {
        if (stack == null || meta == null) return;
        CustomEnchantMutationFormatting.rebuildLore(stack, meta);
        com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.VanillaEnchantDisplay.refreshDisplay(stack, meta);
    }

    public static ItemStack removeEnchant(ItemStack stack) {
        ensureInit();
        if (stack == null) return stack;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(KEY_ENCHANT_ID);   pdc.remove(KEY_ENCHANT_LVL);
        pdc.remove(KEY_ENCHANT_2_ID); pdc.remove(KEY_ENCHANT_2_LVL);

        CustomEnchantMutationFormatting.rebuildLore(stack, meta);
        com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.VanillaEnchantDisplay.refreshDisplay(stack, meta);

        stack.setItemMeta(meta);
        return stack;
    }

    public static ItemStack removeEnchant(ItemStack stack, String enchantId) {
        ensureInit();
        if (stack == null || enchantId == null) return stack;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String id1 = pdc.get(KEY_ENCHANT_ID, PersistentDataType.STRING);
        String id2 = pdc.get(KEY_ENCHANT_2_ID, PersistentDataType.STRING);

        boolean changed = false;
        if (enchantId.equalsIgnoreCase(id1)) {
            pdc.remove(KEY_ENCHANT_ID);
            pdc.remove(KEY_ENCHANT_LVL);
            changed = true;
        } else if (enchantId.equalsIgnoreCase(id2)) {
            pdc.remove(KEY_ENCHANT_2_ID);
            pdc.remove(KEY_ENCHANT_2_LVL);
            changed = true;
        }

        if (changed) {
            CustomEnchantMutationFormatting.rebuildLore(stack, meta);
            com.example.corovaItems.Enchantments.GreaterEnchantmentSystem.VanillaEnchantDisplay.refreshDisplay(stack, meta);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static boolean hasAnyCustomEnchant(ItemStack stack) {
        ensureInit();
        if (stack == null) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.has(KEY_ENCHANT_ID, PersistentDataType.STRING)
                || pdc.has(KEY_ENCHANT_2_ID, PersistentDataType.STRING);
    }

    public static String getEnchantId(ItemStack stack) {
        ensureInit();
        if (stack == null) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String id = pdc.get(KEY_ENCHANT_ID, PersistentDataType.STRING);
        if (id == null) id = pdc.get(KEY_ENCHANT_2_ID, PersistentDataType.STRING);
        return id;
    }

    public static int getEnchantLevel(ItemStack stack) {
        ensureInit();
        if (stack == null) return 0;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return 0;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer lvl = pdc.get(KEY_ENCHANT_LVL, PersistentDataType.INTEGER);
        if (lvl == null) lvl = pdc.get(KEY_ENCHANT_2_LVL, PersistentDataType.INTEGER);
        return lvl == null ? 0 : Math.max(1, lvl);
    }

    public static int getEnchantLevel(ItemStack stack, String enchantId) {
        return getAllCustomEnchants(stack).getOrDefault(enchantId, 0);
    }

    public static double getSynergyMultiplier(String enchantId, int level) {
        if (enchantId == null) return 0.0;
        return switch (enchantId) {
            case LIGHTNING_ID, BEAM_ID, FANG_STRIKE_ID, SOUL_PROJECTION_ID -> 3.0;
            default -> 0.0;
        };
    }

    public static Map<String, Integer> getAllCustomEnchants(ItemStack stack) {
        ensureInit();
        Map<String, Integer> res = new HashMap<>();
        if (stack == null || !stack.hasItemMeta()) return res;
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String id1 = pdc.get(KEY_ENCHANT_ID, PersistentDataType.STRING);
        if (id1 != null) res.put(id1, pdc.getOrDefault(KEY_ENCHANT_LVL, PersistentDataType.INTEGER, 1));

        // Slot 2 is only accessible if the extra slot mutation is present.
        if (MutationManager.getInstance().hasMutation(stack, MutationType.EXTRA_CUSTOM_ENCHANT_SLOT)) {
            String id2 = pdc.get(KEY_ENCHANT_2_ID, PersistentDataType.STRING);
            if (id2 != null) res.put(id2, pdc.getOrDefault(KEY_ENCHANT_2_LVL, PersistentDataType.INTEGER, 1));
        }
        return res;
    }

    public static boolean hasEnchant(ItemStack stack, String enchantId) {
        for (String id : getAllCustomEnchants(stack).keySet())
            if (id.equalsIgnoreCase(enchantId)) return true;
        return false;
    }

    public static String toRoman(int num) {
        if (num <= 0) return "I";
        int[]    vals   = {1000,900,500,400,100,90,50,40,10,9,5,4,1};
        String[] romans = {"M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vals.length; i++)
            while (num >= vals[i]) { num -= vals[i]; sb.append(romans[i]); }
        return sb.toString();
    }

    public static int getMaxLevel(String enchantId) {
        if (enchantId == null) return 1;
        return switch (enchantId) {
            case POISON_ID, WITHER_ID -> 3;
            case KNOCKBACK_PROTECTION_ID -> 7;
            case PAYDAY_ID            -> 5;
            case FREEZE_ID            -> 5;
            case SNOWSTORM_ID         -> 5;
            case EXPLOSIVE_ROUND_ID, THRUST_ID, FLARE_ID, MISSILE_ID -> 3;
            case SOUL_FIRE_ASPECT_ID -> 3;
            case CRITICAL_ID -> 6;
            case SOUL_PROJECTION_ID -> 2;
            case STEALTH_STEP_ID    -> 2;
            case NAPALM_ID     -> 3;
            case WATER_BALL_ID -> 1;
            case DIVINUM_TRABEM_ID -> 3;
            case COSMIC_RAY_ID     -> 4;
            case STORM_ID      -> StormBook.MAX_LEVEL;
            case ARROW_RAIN_ID -> ArrowRainBook.MAX_LEVEL;
            case STEED_ID      -> SteedBook.MAX_LEVEL;
            case FLIGHT_ID     -> 2;
            // ── Multi-level enchants ──────────────────────────────────────────
            case BEAM_ID, LAUNCH_ID -> 3;
            case WEB_SLING_ID -> WebSlingBook.MAX_LEVEL;
            case MUSIC_ID     -> MusicBook.MAX_LEVEL;
            case LIGHTNING_ID -> 5;
            case HOOK_ID -> 1;
            default -> 1;
        };
    }

    public static boolean shouldSilenceStealthStep(LivingEntity entity, boolean isFall) {
        if (entity == null || entity.getEquipment() == null) return false;
        ItemStack boots = entity.getEquipment().getBoots();
        if (boots == null) return false;

        int level = getEnchantLevel(boots, STEALTH_STEP_ID);
        if (level <= 0) return false;

        if (isFall) {
            return level >= 2;
        } else {
            if (level >= 2) return true;
            boolean sprinting = entity instanceof Player p && p.isSprinting();
            return !sprinting;
        }
    }

    public static int getUpgradeCost(String enchantId) {
        if (enchantId == null) return 10;
        return switch (enchantId) {
            case SOUL_PROJECTION_ID -> SoulProjection.UPGRADE_COST;
            case POISON_ID          -> PoisonBook.UPGRADE_COST;
            case WITHER_ID          -> WitherBook.UPGRADE_COST;
            case NAPALM_ID          -> NapalmBook.UPGRADE_COST;
            case STORM_ID           -> StormBook.UPGRADE_COST;
            case STEED_ID               -> SteedBook.UPGRADE_COST;
            case ARROW_RAIN_ID      -> ArrowRainBook.UPGRADE_COST;
            case WEB_SLING_ID       -> 10;
            case FLIGHT_ID          -> FlightBook.UPGRADE_COST;
            case PAYDAY_ID          -> PaydayBook.UPGRADE_COST;
            case FLARE_ID, MISSILE_ID, DIVINUM_TRABEM_ID, WATER_BALL_ID, COSMIC_RAY_ID -> 10;
            case KNOCKBACK_PROTECTION_ID, SOUL_FIRE_ASPECT_ID, STEALTH_STEP_ID, CRITICAL_ID, FREEZE_ID -> 10;
            default -> 10;
        };
    }

    public static class MergeResult {
        public final int cost;
        public final boolean changed;

        public MergeResult(int cost, boolean changed) {
            this.cost = cost;
            this.changed = changed;
        }
    }

    public static MergeResult mergeEnchants(ItemStack left, ItemStack right) {
        ensureInit();
        int totalCost = 0;
        boolean changed = false;

        Map<String, Integer> rightEnchants = getAllCustomEnchants(right);
        for (Map.Entry<String, Integer> entry : rightEnchants.entrySet()) {
            String id = entry.getKey();
            int rightLvl = entry.getValue();

            ItemMeta leftMeta = left.getItemMeta();
            PersistentDataContainer leftPdc = leftMeta.getPersistentDataContainer();
            String id1 = leftPdc.get(KEY_ENCHANT_ID, PersistentDataType.STRING);
            Integer lvl1 = leftPdc.get(KEY_ENCHANT_LVL, PersistentDataType.INTEGER);
            String id2 = leftPdc.get(KEY_ENCHANT_2_ID, PersistentDataType.STRING);
            Integer lvl2 = leftPdc.get(KEY_ENCHANT_2_LVL, PersistentDataType.INTEGER);

            if (id.equals(id1)) {
                int maxLvl = getMaxLevel(id);
                if (rightLvl == lvl1 && lvl1 < maxLvl) {
                    applyEnchant(left, id, lvl1 + 1, 1);
                    totalCost += getUpgradeCost(id);
                    changed = true;
                } else if (rightLvl > lvl1) {
                    applyEnchant(left, id, rightLvl, 1);
                    totalCost += 1; // Base cost for applying a higher level
                    changed = true;
                }
            } else if (id.equals(id2)) {
                int maxLvl = getMaxLevel(id);
                if (rightLvl == lvl2 && lvl2 < maxLvl) {
                    applyEnchant(left, id, lvl2 + 1, 2);
                    totalCost += getUpgradeCost(id);
                    changed = true;
                } else if (rightLvl > lvl2) {
                    applyEnchant(left, id, rightLvl, 2);
                    totalCost += 1;
                    changed = true;
                }
            } else {
                // Not present on left item - try to add or replace.
                if (id1 == null) {
                    applyEnchant(left, id, rightLvl, 1);
                    totalCost += 1;
                    changed = true;
                } else if (MutationManager.getInstance().hasMutation(left, MutationType.EXTRA_CUSTOM_ENCHANT_SLOT)) {
                    if (id2 == null) {
                        applyEnchant(left, id, rightLvl, 2);
                        totalCost += 1;
                        changed = true;
                    } else {
                        // Both slots full, replace slot 1 (default priority)
                        applyEnchant(left, id, rightLvl, 1);
                        totalCost += 1;
                        changed = true;
                    }
                } else {
                    // No extra slot, replace slot 1
                    applyEnchant(left, id, rightLvl, 1);
                    totalCost += 1;
                    changed = true;
                }
            }
        }

        return new MergeResult(totalCost, changed);
    }
}