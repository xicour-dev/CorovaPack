package com.example.corovaItems.Enchantments.EnchantBooks;

import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.Enchantments.EnchantmentBook;
import org.bukkit.Material;
import org.bukkit.event.Listener;

import java.util.Set;

public class NightVisionBook extends EnchantmentBook implements Listener {

    public NightVisionBook() {
        this(1);
    }

    public NightVisionBook(int level) {
        super(
                "Book of Night Vision",
                CorovaEnchantments.NIGHT_VISION_ID,
                level,
                "book_night_vision",
                allowedMaterials()
        );
    }

    private static Set<Material> allowedMaterials() {
        return Set.of(
                Material.LEATHER_HELMET,
                Material.CHAINMAIL_HELMET,
                Material.IRON_HELMET,
                Material.GOLDEN_HELMET,
                Material.DIAMOND_HELMET,
                Material.NETHERITE_HELMET,
                Material.TURTLE_HELMET
        );
    }
}
