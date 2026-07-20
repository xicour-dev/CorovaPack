package com.example.corovaItems;

import com.example.corovaItems.WeaponProperties.SwordBlocking;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public abstract class CorovaItems {

    private static NamespacedKey COROVA_ITEM_KEY;
    private static final Map<String, CorovaItems> registeredItems = new HashMap<>();

    public static void init(JavaPlugin plugin) {
        COROVA_ITEM_KEY = new NamespacedKey(plugin, "corova_item_id");
    }

    public static void register(CorovaItems item) {
        registeredItems.put(item.getInternalId().toLowerCase(), item);
        ItemManager.getInstance().registerItem(item); // Keep ItemManager in sync
    }

    private final String name;
    private final Material[] materials;
    private final List<String> lore;
    private final Map<Enchantment, Integer> enchantments;
    private final String internalId;

    // Resource-pack model selector, e.g. "corova:weapons/wooden_scythe".
    // This is the STRING key that assets/corova/items/<base_item>.json
    // matches against via its minecraft:select case, NOT a legacy
    // numeric CustomModelData id. Pass the generated CorovaCustomModelData
    // constant here from each subclass -- never a hand-typed string.
    private final String customModelDataKey;
    private final Map<Attribute, AttributeModifier> attributes;

    protected CorovaItems(String name,
                          Material material,
                          List<String> lore,
                          Map<Enchantment, Integer> enchantments,
                          String internalId) {
        this(name, new Material[]{material}, lore, enchantments, internalId, null, Collections.emptyMap());
    }

    protected CorovaItems(String name,
                          Material[] materials,
                          List<String> lore,
                          Map<Enchantment, Integer> enchantments,
                          String internalId) {
        this(name, materials, lore, enchantments, internalId, null, Collections.emptyMap());
    }

    protected CorovaItems(String name,
                          Material[] materials,
                          List<String> lore,
                          Map<Enchantment, Integer> enchantments,
                          String internalId,
                          String customModelDataKey,
                          Map<Attribute, AttributeModifier> attributes) {
        this.name = name;
        this.materials = materials;
        this.lore = lore;
        this.enchantments = enchantments;
        this.internalId = internalId;
        this.customModelDataKey = customModelDataKey;
        this.attributes = attributes;
        register(this);
    }

    public String getInternalId() { return internalId; }
    public String getName() { return name; }
    public Material[] getMaterials() { return materials; }
    public List<String> getLore() { return lore; }
    public Map<Enchantment, Integer> getEnchantments() { return enchantments; }
    public String getCustomModelDataKey() { return customModelDataKey; }
    public Map<Attribute, AttributeModifier> getAttributes() { return attributes; }

    public ItemStack getItemStack() {
        return toItemStack();
    }

    public ItemStack toItemStack() {
        return createItemStack(materials[0]);
    }

    public ItemStack createItemStack(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(
                    Component.text(name)
                            .decoration(TextDecoration.ITALIC, false)
            );
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            if (enchantments != null) enchantments.forEach((ench, lvl) -> meta.addEnchant(ench, lvl, true));
            if (attributes != null) attributes.forEach(meta::addAttributeModifier);
            meta.getPersistentDataContainer().set(COROVA_ITEM_KEY, PersistentDataType.STRING, internalId);
            item.setItemMeta(meta);
        }
        if (customModelDataKey != null) {
            item.setData(
                    DataComponentTypes.CUSTOM_MODEL_DATA,
                    CustomModelData.customModelData().addString(customModelDataKey).build()
            );
        }
        postProcess(item);
        return item;
    }

    public ItemStack[] getFullSet() {
        ItemStack[] set = new ItemStack[materials.length];
        for (int i = 0; i < materials.length; i++) {
            set[i] = createItemStack(materials[i]);
        }
        return set;
    }

    protected void postProcess(ItemStack item) {
        Material type = item.getType();
        if (type == Material.WOODEN_SWORD ||
                type == Material.STONE_SWORD ||
                type == Material.COPPER_SWORD ||
                type == Material.IRON_SWORD ||
                type == Material.GOLDEN_SWORD ||
                type == Material.DIAMOND_SWORD ||
                type == Material.NETHERITE_SWORD) {
            SwordBlocking.processSword(item);
        }
    }

    public boolean isThisItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String id = meta.getPersistentDataContainer().get(COROVA_ITEM_KEY, PersistentDataType.STRING);
        return internalId.equalsIgnoreCase(id);
    }

    public static CorovaItems getItemByName(String name) {
        return registeredItems.get(name.toLowerCase());
    }

    public static Set<String> getAllItemNames() {
        return Collections.unmodifiableSet(registeredItems.keySet());
    }
}