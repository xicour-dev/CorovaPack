package com.example.corovaItems.Enchantments.GreaterEnchantmentSystem;

import org.bukkit.ChatColor;
import org.bukkit.Registry;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles display of vanilla enchantments by hiding the real enchantments
 * and replacing them with gray lore lines that match vanilla's style.
 */
public final class VanillaEnchantDisplay {

    public static final int DISPLAY_CAP = 10;
    private static final String PDC_KEY_PREFIX = "true_ench_level_";
    private static final String LORE_COLOR = "§7";

    // Legacy markers for cleanup
    private static final String[] OLD_MARKERS = {
            "§r§0⁎", "§f§f§f§r", "§7§r§7§r", "§0§1§2§r", "\u200B\u200C\u200D", "§7§8§7"
    };

    // Truly unique signature for our managed lore lines.
    private static final String SIGNATURE = "§f§0§f§0§r";
    private static final String PREVIEW_SIGNATURE = "§f§0§f§1§r";

    private static JavaPlugin plugin;

    private VanillaEnchantDisplay() {}

    public static void init(JavaPlugin owningPlugin) {
        plugin = owningPlugin;
    }

    /**
     * Applies a batch of enchantments, persists their true levels, applies flags,
     * and refreshes the lore display in a single operation.
     */
    public static void applyBatch(ItemStack item, Map<Enchantment, Integer> enchants) {
        if (item == null || enchants == null) return;
        ensureInit();

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            Enchantment enchant = entry.getKey();
            int trueLevel = entry.getValue();

            if (trueLevel <= 0) {
                removePdcEntry(meta, enchant);
                if (meta instanceof EnchantmentStorageMeta bookMeta) {
                    bookMeta.removeStoredEnchant(enchant);
                } else {
                    meta.removeEnchant(enchant);
                }
            } else {
                storeTrueLevel(meta, enchant, trueLevel);
                int clamped = Math.min(trueLevel, DISPLAY_CAP);
                if (meta instanceof EnchantmentStorageMeta bookMeta) {
                    bookMeta.addStoredEnchant(enchant, clamped, true);
                } else {
                    meta.addEnchant(enchant, clamped, true);
                }
            }
        }

        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        if (meta instanceof EnchantmentStorageMeta) {
            meta.addItemFlags(ItemFlag.HIDE_STORED_ENCHANTS);
        }
        item.setItemMeta(meta);

        refreshDisplay(item);
    }

    /**
     * Applies an enchantment and immediately syncs the display.
     */
    public static void applyWithDisplay(ItemStack item, Enchantment enchant, int trueLevel) {
        Map<Enchantment, Integer> map = new HashMap<>();
        map.put(enchant, trueLevel);
        applyBatch(item, map);
    }

    public static int getTrueLevel(ItemStack item, Enchantment enchant) {
        if (item == null || !item.hasItemMeta() || enchant == null) return 0;
        ensureInit();

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey key = pdcKey(enchant);

        Integer stored = pdc.get(key, PersistentDataType.INTEGER);
        if (stored != null && stored > 0) return stored;

        if (meta instanceof EnchantmentStorageMeta bookMeta) {
            return bookMeta.getStoredEnchantLevel(enchant);
        }
        return meta.getEnchantLevel(enchant);
    }

    /**
     * Identifies lore lines managed by this system.
     */
    public static boolean isOverLevelLoreLine(String line) {
        if (line == null) return false;

        // 1. Check for the current signature, anchored at the START of the line.
        // buildLoreLine() always places SIGNATURE as the literal first characters
        // of any line this system writes — so a startsWith check is both sufficient
        // and necessary here.
        //
        // CRITICAL: this must NOT be a "contains anywhere" check. Custom enchant
        // lines colored via EnchantmentBook#applyEnchantmentGradient use Bungee's
        // hex format (§x§R§R§G§G§B§B per character), which densely packs §<hexdigit>
        // sequences throughout the string. The 4-character sequence "§f§0§f§0" can
        // and does occur PURELY BY COINCIDENCE in the middle of such a string
        // whenever two adjacent color channels happen to land near 0xf0/0x0f (e.g.
        // "Web Slinger III" with a white-ish gradient). A "contains" check matches
        // that coincidence and silently deletes an unrelated custom enchant line.
        if (line.startsWith(SIGNATURE) || line.startsWith(PREVIEW_SIGNATURE)) return true;

        // Legacy markers are short, low-entropy sequences (no hex-digit runs), so a
        // "contains" check for these remains safe in practice. NOTE: "§7§8§7" is a
        // 3-pair hex-style sequence and is theoretically not immune to the same
        // coincidental-collision risk as the SIGNATURE check above — if false
        // positives are ever observed on lines containing this exact substring,
        // anchor this one with startsWith too (legacy items only ever needed it at
        // the front of the line in the original format).
        for (String old : OLD_MARKERS) {
            if (line.contains(old)) return true;
        }

        // 2. Legacy fallback for lines written before the signature scheme existed:
        // match ONLY if the leading text (ignoring color/format codes) is an exact
        // vanilla enchantment name followed by a Roman numeral.
        //
        // IMPORTANT: this must NOT be a generic "ends in Roman numeral + has a
        // gray/white color code" check. Custom enchant lines built by
        // EnchantmentBook#applyEnchantmentGradient are colored using Bungee's
        // hex format (§x§R§R§G§G§B§B per character), which means the RAW STRING
        // almost always contains literal "§7", "§8", or "§f" substrings purely as
        // hex-digit byproducts of the gradient — completely unrelated to whether
        // the line is actually gray/white. A generic substring check on §7/§8/§f
        // therefore matches most custom enchant lines (e.g. "Storm III", "Lightning V")
        // and silently deletes them every time refreshDisplay() runs, even though
        // CustomEnchantMutationFormatting just correctly added them moments earlier.
        //
        // Comparing against the actual set of vanilla enchantment display names
        // eliminates this false-positive class entirely.
        String plain = ChatColor.stripColor(line).trim();
        if (plain.isEmpty()) return false;

        String[] parts = plain.split(" ");
        if (parts.length >= 2) {
            String potentialRoman = parts[parts.length - 1];
            if (isRoman(potentialRoman)) {
                String namePart = plain.substring(0, plain.length() - potentialRoman.length()).trim();
                return vanillaEnchantNames().contains(namePart.toLowerCase());
            }
        }

        return false;
    }

    /**
     * Set of every vanilla enchantment's formatted display name (lowercase),
     * used by {@link #isOverLevelLoreLine} to distinguish genuine vanilla enchant
     * lore lines from custom enchant lines that happen to end in a Roman numeral.
     * Built lazily from {@link Enchantment#values()} so it stays in sync with
     * whatever vanilla enchantments exist on the running server version.
     */
    private static Set<String> VANILLA_ENCHANT_NAMES_CACHE;

    private static Set<String> vanillaEnchantNames() {
        if (VANILLA_ENCHANT_NAMES_CACHE == null) {
            Set<String> names = new HashSet<>();
            for (Enchantment e : Registry.ENCHANTMENT) {
                names.add(formatEnchantName(e).toLowerCase());
            }
            VANILLA_ENCHANT_NAMES_CACHE = names;
        }
        return VANILLA_ENCHANT_NAMES_CACHE;
    }

    private static boolean isRoman(String s) {
        return s.matches("^[IVXLCDM]+$");
    }

    public static void refreshDisplay(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        refreshDisplay(item, meta);
        item.setItemMeta(meta);
    }

    public static void refreshDisplay(ItemStack item, ItemMeta meta) {
        if (item == null || meta == null) return;
        ensureInit();

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

        // Also remove preview lore lines.
        lore.removeIf(line -> line.contains(PREVIEW_SIGNATURE));

        lore.removeIf(VanillaEnchantDisplay::isOverLevelLoreLine);

        Map<Enchantment, Integer> enchants = getAllTrueLevels(item, meta);

        if (!enchants.isEmpty()) {
            List<Enchantment> sorted = enchants.keySet().stream()
                    .sorted(Comparator.comparingInt(VanillaEnchantDisplay::getVanillaSortPriority)
                            .thenComparing(VanillaEnchantDisplay::formatEnchantName))
                    .collect(Collectors.toList());

            for (int i = sorted.size() - 1; i >= 0; i--) {
                Enchantment enchant = sorted.get(i);
                int level = enchants.get(enchant);
                lore.add(0, buildLoreLine(enchant, level));
            }
        }

        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        if (meta instanceof EnchantmentStorageMeta) {
            meta.addItemFlags(ItemFlag.HIDE_STORED_ENCHANTS);
        }
    }

    public static Map<Enchantment, Integer> getAllTrueLevels(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return new HashMap<>();
        return getAllTrueLevels(item, item.getItemMeta());
    }

    public static Map<Enchantment, Integer> getAllTrueLevels(ItemStack item, ItemMeta meta) {
        Map<Enchantment, Integer> result = new HashMap<>();
        if (item == null || meta == null) return result;
        ensureInit();

        Map<Enchantment, Integer> baseEnchants;
        if (meta instanceof EnchantmentStorageMeta bookMeta) {
            baseEnchants = bookMeta.getStoredEnchants();
        } else {
            baseEnchants = meta.getEnchants();
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        for (Map.Entry<Enchantment, Integer> entry : baseEnchants.entrySet()) {
            Enchantment enchant = entry.getKey();
            NamespacedKey key = pdcKey(enchant);
            Integer stored = pdc.get(key, PersistentDataType.INTEGER);
            result.put(enchant, (stored != null && stored > 0) ? stored : entry.getValue());
        }

        return result;
    }

    private static String buildLoreLine(Enchantment enchant, int trueLevel) {
        String displayName = formatEnchantName(enchant);
        String roman = toRoman(trueLevel);

        // SIGNATURE must be the literal first characters so startsWith() in
        // isOverLevelLoreLine() reliably identifies this line after any number
        // of NBT round-trips.
        //
        // The old implementation appended the enchant key char-by-char with §
        // before each character (e.g. §m§i§n§e§c§r...). The §r that appears
        // inside "minecraft" is a valid Bukkit reset code — Bukkit's lore
        // serializer strips or resets it on save/load, so the string that comes
        // BACK from NBT is different from the one we wrote. startsWith(SIGNATURE)
        // then fails on the deserialized line and the lore is never removed.
        //
        // Fix: write only SIGNATURE + the visible display text. The vanilla-name
        // + roman-numeral fallback in isOverLevelLoreLine() handles any pre-fix
        // lines that lack the signature.
        return SIGNATURE + LORE_COLOR + displayName + " " + roman;
    }

    static void storeTrueLevel(ItemMeta meta, Enchantment enchant, int trueLevel) {
        meta.getPersistentDataContainer().set(pdcKey(enchant), PersistentDataType.INTEGER, trueLevel);
    }

    private static void removePdcEntry(ItemMeta meta, Enchantment enchant) {
        meta.getPersistentDataContainer().remove(pdcKey(enchant));
    }

    /**
     * Public variant of {@link #removePdcEntry} used by
     * {@link GrindstoneEnchantCleaner} to wipe stale true-level PDC keys
     * from a grindstone result item without going through a full applyBatch.
     *
     * @param meta   the ItemMeta to modify (caller must call item.setItemMeta after)
     * @param enchant the enchantment whose PDC key should be removed
     */
    public static void removeTrueLevelFromMeta(ItemMeta meta, Enchantment enchant) {
        if (meta == null || enchant == null) return;
        ensureInit();
        removePdcEntry(meta, enchant);
    }

    /**
     * Returns the JavaPlugin this class was initialised with.
     * Used by {@link GrindstoneEnchantCleaner} to build PDC NamespacedKeys
     * without duplicating the key-prefix logic.
     */
    public static JavaPlugin getPlugin() {
        ensureInit();
        return plugin;
    }

    private static NamespacedKey pdcKey(Enchantment enchant) {
        String localKey = PDC_KEY_PREFIX + enchant.getKey().getKey().replace(':', '_');
        return new NamespacedKey(plugin, localKey);
    }

    private static int getVanillaSortPriority(Enchantment e) {
        String key = e.getKey().getKey();
        if (key.contains("protection")) return 10;
        if (key.contains("sharpness") || key.contains("smite") || key.contains("bane")) return 20;
        if (key.contains("power") || key.contains("punch")) return 25;
        if (key.contains("efficiency") || key.contains("silk") || key.contains("fortune")) return 30;
        if (key.contains("unbreaking") || key.contains("mending")) return 100;
        if (e.isCursed()) return 200;
        return 50;
    }

    private static String formatEnchantName(Enchantment enchant) {
        String key = enchant.getKey().getKey();
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');

            if (part.equalsIgnoreCase("of") || part.equalsIgnoreCase("the")) {
                sb.append(part.toLowerCase());
            } else {
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    public static String toRoman(int num) {
        if (num <= 0) return "I";
        int[]    vals   = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] romans = { "M","CM",  "D", "CD", "C","XC", "L","XL", "X","IX","V","IV","I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vals.length; i++) {
            while (num >= vals[i]) {
                num -= vals[i];
                sb.append(romans[i]);
            }
        }
        return sb.toString();
    }

    /**
     * Adds temporary lore to an item in an enchanting table to preview high-level
     * enchantments using Roman numerals.
     */
    public static void addPreviewLore(ItemStack item, Map<Integer, List<EnchantmentOffer>> offers) {
        if (item == null || offers == null || offers.isEmpty()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> line.contains(PREVIEW_SIGNATURE));

        boolean hasHighLevel = false;
        for (List<EnchantmentOffer> slotOffers : offers.values()) {
            if (slotOffers == null) continue;
            for (EnchantmentOffer offer : slotOffers) {
                if (offer.getEnchantmentLevel() > 10) {
                    hasHighLevel = true;
                    break;
                }
            }
        }

        if (hasHighLevel) {
            lore.add(PREVIEW_SIGNATURE + "§d§lEnchantment Preview:");
            for (int i = 0; i < 3; i++) {
                List<EnchantmentOffer> slotOffers = offers.get(i);
                if (slotOffers == null || slotOffers.isEmpty()) continue;
                EnchantmentOffer primary = slotOffers.get(0);
                String name = formatEnchantName(primary.getEnchantment());
                String roman = toRoman(primary.getEnchantmentLevel());
                lore.add(PREVIEW_SIGNATURE + " §5" + (i + 1) + ". §7" + name + " " + roman);
            }
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    /**
     * Removes temporary preview lore from an item.
     */
    public static void removePreviewLore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return;

        List<String> lore = new ArrayList<>(meta.getLore());
        if (lore.removeIf(line -> line.contains(PREVIEW_SIGNATURE))) {
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
    }

    private static void ensureInit() {
        if (plugin == null) {
            throw new IllegalStateException("VanillaEnchantDisplay not initialised.");
        }
    }
}