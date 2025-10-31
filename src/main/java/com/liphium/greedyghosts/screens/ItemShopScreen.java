package com.liphium.greedyghosts.screens;

import com.cryptomorin.xseries.XMaterial;
import com.liphium.greedyghosts.GreedyGhosts;
import de.badgames.pluginCore.inventory.CClickEvent;
import de.badgames.pluginCore.inventory.CItem;
import de.badgames.pluginCore.inventory.CScreen;
import de.badgames.pluginCore.util.InventoryUtil;
import de.badgames.pluginCore.util.ItemStackBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;

public class ItemShopScreen extends CScreen {

    public static final int SCREEN_ID = 4;

    public ItemShopScreen() {
        super(SCREEN_ID, Component.text("Item shop", NamedTextColor.GOLD, TextDecoration.BOLD), 1, true);

        background();

        // All the blocks and items
        setItem(0, itemWithPrice(XMaterial.GOLDEN_APPLE, "Golden apple", 2, 1));
        setItem(1, itemWithPrice(XMaterial.COARSE_DIRT, "Dirt", 2, 4));
        setItem(2, itemWithPrice(XMaterial.OAK_PLANKS, "Planks", 4, 4));
        setItem(3, itemWithPrice(XMaterial.COBBLESTONE, "Cobblestone", 5, 4));
        setItem(4, spacer());

        // All the traps
        setItem(5, itemWithPrice(XMaterial.GLOWSTONE_DUST, "Glow Trap", 5, 1));
        setItem(6, itemWithPrice(XMaterial.GREEN_DYE, "Poison Trap", 5, 1));
        setItem(7, itemWithPrice(XMaterial.LEATHER, "Armor Trap", 7, 1));
        setItem(8, itemWithPrice(XMaterial.WHITE_DYE, "Web Trap", 8, 1));
    }

    private static final ItemStack item = new ItemStackBuilder(XMaterial.BLACK_STAINED_GLASS_PANE).withName(Component.text("§r")).buildStack();

    public static CItem spacer() {
        return new CItem(item).notClickable();
    }

    public static CItem itemWithPrice(XMaterial material, String name, NamedTextColor color, int price, int amount) {
        return itemWithPrice(material, Component.text(name, color).content(), price, amount);
    }

    public static CItem itemWithPriceCustom(ItemStack sold, int price) {
        return itemWithPriceCustom(XMaterial.matchXMaterial(sold.getType()), ((TextComponent)sold.getItemMeta().displayName()).content(), price, sold);
    }

    public static CItem itemWithPrice(XMaterial material, String name, int price, int amount) {
        return new ItemStackBuilder(material).withName(name)
                .withLore("§7Price: §6" + price)
                .withAmount(amount)
                .buildCItem()
                .onClick(event -> buyFunction(event, new ItemStackBuilder(material).withName(name).withAmount(amount).buildStack(), price));
    }

    public static CItem itemWithPriceCustom(XMaterial material, String name, int price, ItemStack sold) {
        return new ItemStackBuilder(material).withName(name)
                .withLore("§7Price: §6" + price)
                .withBukkitEnchantments(sold.getEnchantments())
                .buildCItem().onClick(event -> buyFunction(event, sold, price));
    }

    public static void buyFunction(CClickEvent event, ItemStack stack, int price) {
        // Get the amount of pumpkins in the inventory
        if (!InventoryUtil.hasEnoughItems(event.player(), XMaterial.CARVED_PUMPKIN, price)) {
            event.player().sendMessage(GreedyGhosts.PREFIX.append(Component.text("You don't have enough pumpkins to purchase this item.", NamedTextColor.RED)));
            event.player().closeInventory();
            return;
        }

        // Remove the pumpkins from the players inventory
        InventoryUtil.removeAmountFromInventory(event.player(), XMaterial.CARVED_PUMPKIN, price);

        event.player().getInventory().addItem(stack);
    }
}
