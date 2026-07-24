package com.example.corovaItems.Misc;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.ItemManager;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public class EnchantedEnderPearl extends CorovaItems implements Listener {

    public EnchantedEnderPearl() {
        super("§dEnchanted Ender Pearl",
                Material.ENDER_PEARL,
                List.of("§7Allows use of teleportation commands such as /tpa, consumes 1 on use!"),
                Map.of(Enchantment.LUCK_OF_THE_SEA, 1),
                "EnchantedEnderPearl");
        ItemManager.getInstance().registerItem(this);
    }

    @Override
    public ItemStack getItemStack() {
        ItemStack item = super.getItemStack();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }
}
