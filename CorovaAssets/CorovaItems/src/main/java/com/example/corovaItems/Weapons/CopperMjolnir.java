package com.example.corovaItems.Weapons;

import com.example.corovaItems.CorovaItems;
import com.example.corovaItems.Enchantments.CorovaEnchantments;
import com.example.corovaItems.ItemManager;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Map;

public class CopperMjolnir extends CorovaItems {

    public CopperMjolnir() {
        super(
                "§6Copper Mjolnir",
                new Material[]{Material.matchMaterial("COPPER_AXE")},
                Arrays.asList(
                        "§8Calls the power of the storm"
                ),
                Map.of(
                        Enchantment.SHARPNESS, 7
                ),
                "copper_mjolnir"
        );

        ItemManager.getInstance().registerItem(this);
        // No events registered — LightningBook's onRightClick already handles
        // the ability for any item that has the lightning enchant in its PDC,
        // including this one.
    }

    // Write Lightning I into PDC slot 1 on item creation.
    // LightningBook.onRightClick checks hasEnchant() on the held item — since
    // this stores the enchant properly in the PDC, it will match and the
    // right-click ability fires through that handler automatically.

    @Override
    public ItemStack getItemStack() {
        ItemStack item = super.getItemStack();
        return CorovaEnchantments.applyEnchant(item, CorovaEnchantments.LIGHTNING_ID, 1, 1);
    }

    @Override
    public ItemStack toItemStack() {
        ItemStack item = super.toItemStack();
        return CorovaEnchantments.applyEnchant(item, CorovaEnchantments.LIGHTNING_ID, 1, 1);
    }
}