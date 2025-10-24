package com.liphium.greedyghosts.screens;

import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.XMaterial;
import de.badgames.pluginCore.inventory.CClickEvent;
import de.badgames.pluginCore.inventory.CItem;
import de.badgames.pluginCore.inventory.CScreen;
import de.badgames.pluginCore.util.InventoryUtil;
import de.badgames.pluginCore.util.ItemStackBuilder;
import com.liphium.greedyghosts.GreedyGhosts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;

public class ItemShopScreen extends CScreen {

    public ItemShopScreen() {
        super(5, Component.text("Item shop", NamedTextColor.DARK_GREEN, TextDecoration.BOLD), 4, false);
    }

    @Override
    public void init(Player player, Inventory inventory) {
        background(player);

        // Add all the categories
        for (ShopCategory category : ShopCategory.values()) {
            setItemNotCached(player, 10 + category.ordinal(), new CItem(category.getStack())
                    .onClick(event -> openCategory(event, category, inventory)));
        }
    }

    public void openCategory(CClickEvent event, ShopCategory category, Inventory inventory) {
        for (int i = 0; i < 9; i++) {
            if (category.getItems().size() <= i) {
                setItemNotCached(event.player(), 18 + i, ShopCategory.spacer(), inventory);
            } else {
                setItemNotCached(event.player(), 18 + i, category.getItems().get(i), inventory);
            }
        }
    }

    public enum ShopCategory {
        WEAPONS(
                new ItemStackBuilder(XMaterial.IRON_SWORD)
                        .withName(Component.text("Weapons", NamedTextColor.RED, TextDecoration.BOLD))
                        .withLore("§7Swords, bows, and more.")
                        .buildStack(),
                List.of(
                        itemWithPrice(XMaterial.MACE, "Mace", NamedTextColor.RED, 25, 1),
                        itemWithPrice(XMaterial.WIND_CHARGE, "Wind charge", NamedTextColor.RED, 20, 5),
                        itemWithPrice(XMaterial.BOW, "Bow", NamedTextColor.RED, 10, 1),
                        itemWithPriceCustom(new ItemStackBuilder(XMaterial.BOW)
                                .withName(Component.text("Punch Bow", NamedTextColor.RED))
                                .withEnchantments(Map.of(XEnchantment.PUNCH, 1))
                                .buildStack(), 20
                        ),
                        itemWithPriceCustom(new ItemStackBuilder(XMaterial.BOW)
                                .withName(Component.text("More Punch Bow", NamedTextColor.RED))
                                .withEnchantments(Map.of(XEnchantment.PUNCH, 2, XEnchantment.INFINITY, 1))
                                .buildStack(), 100
                        ),
                        itemWithPrice(XMaterial.ARROW, "Arrow", NamedTextColor.RED, 15, 3),
                        itemWithPrice(XMaterial.CROSSBOW, "Crossbow", NamedTextColor.RED, 50, 1),
                        itemWithPriceCustom(new ItemStackBuilder(XMaterial.FIREWORK_ROCKET)
                                .withName(Component.text("Rocket", NamedTextColor.RED))
                                .withAmount(3)
                                .buildStack(), 30
                        )
                )
        ),
        DEFENSE(
                new ItemStackBuilder(XMaterial.SHIELD)
                        .withName(Component.text("Defense", NamedTextColor.GREEN, TextDecoration.BOLD))
                        .withLore("§7Turrets and traps.")
                        .buildStack(),
                List.of(
                        itemWithPrice(XMaterial.GRAY_DYE, "Slow trap", NamedTextColor.GREEN, 25, 1),
                        itemWithPrice(XMaterial.GREEN_DYE, "Poison trap", NamedTextColor.GREEN, 25, 1),
                        itemWithPrice(XMaterial.WHITE_DYE, "Web trap", NamedTextColor.GREEN, 35, 1),
                        itemWithPrice(XMaterial.LIGHT_BLUE_DYE, "Freeze trap", NamedTextColor.GREEN, 35, 1),
                        itemWithPrice(XMaterial.FEATHER, "Fly trap", NamedTextColor.GREEN, 60, 1)
                )
        ),
        TOOLS(
                new ItemStackBuilder(XMaterial.DIAMOND_PICKAXE)
                        .withName(Component.text("Tools", NamedTextColor.AQUA, TextDecoration.BOLD))
                        .withLore("§7Shovels and pickaxes.")
                        .buildStack(),
                List.of(
                        itemWithPriceCustom(new ItemStackBuilder(XMaterial.GOLDEN_SHOVEL)
                                .withName(Component.text("Golden shovel", NamedTextColor.AQUA))
                                .withEnchantments(Map.of(XEnchantment.EFFICIENCY, 5))
                                .buildStack(), 15
                        ),
                        itemWithPriceCustom(new ItemStackBuilder(XMaterial.DIAMOND_SHOVEL)
                                .withName(Component.text("Diamond shovel", NamedTextColor.AQUA))
                                .withEnchantments(Map.of(XEnchantment.EFFICIENCY, 5))
                                .buildStack(), 35
                        ),
                        spacer(),
                        itemWithPriceCustom(new ItemStackBuilder(XMaterial.GOLDEN_PICKAXE)
                                .withName(Component.text("Golden pickaxe", NamedTextColor.AQUA))
                                .withEnchantments(Map.of(XEnchantment.EFFICIENCY, 5))
                                .buildStack(), 15
                        ),
                        itemWithPriceCustom(new ItemStackBuilder(XMaterial.DIAMOND_PICKAXE)
                                .withName(Component.text("Diamond pickaxe", NamedTextColor.AQUA))
                                .withEnchantments(Map.of(XEnchantment.EFFICIENCY, 5))
                                .buildStack(), 35
                        )
                )
        ),
        ITEMS(
                new ItemStackBuilder(XMaterial.IRON_PICKAXE)
                        .withName(Component.text("Items", NamedTextColor.WHITE, TextDecoration.BOLD))
                        .withLore("§7Blocks & materials.")
                        .buildStack(),
                List.of(
                        itemWithPrice(XMaterial.PACKED_ICE, "Ice", NamedTextColor.WHITE, 2, 16),
                        itemWithPrice(XMaterial.SNOW_BLOCK, "Snow", NamedTextColor.WHITE, 4, 16),
                        itemWithPrice(XMaterial.SPRUCE_LOG, "Spruce wood", NamedTextColor.WHITE, 8, 4),
                        itemWithPrice(XMaterial.COBBLESTONE, "Cobblestone", NamedTextColor.WHITE, 8, 16),
                        itemWithPrice(XMaterial.COBWEB, "Cobweb", NamedTextColor.WHITE, 10, 1),
                        spacer(),
                        itemWithPrice(XMaterial.IRON_INGOT, "Iron", NamedTextColor.WHITE, 4, 1),
                        itemWithPrice(XMaterial.DIAMOND, "Diamond", NamedTextColor.WHITE, 7, 1),
                        itemWithPrice(XMaterial.GOLDEN_APPLE, "Golden apple", NamedTextColor.WHITE, 10, 1)
                )
        ),
        DROPPER(
                new ItemStackBuilder(XMaterial.DROPPER)
                        .withName(Component.text("Droppers", NamedTextColor.GOLD, TextDecoration.BOLD))
                        .withLore("§7Coin and material droppers.")
                        .buildStack(),
                List.of(
                        itemWithPrice(XMaterial.GOLD_ORE, "Coin dropper", NamedTextColor.GOLD, 35, 1),
                        itemWithPrice(XMaterial.WHITE_CONCRETE, "Iron dropper", NamedTextColor.GOLD, 20, 1),
                        itemWithPrice(XMaterial.RED_CONCRETE, "Redstone dropper", NamedTextColor.GOLD, 20, 1),
                        itemWithPrice(XMaterial.CYAN_CONCRETE, "Diamond dropper", NamedTextColor.GOLD, 30, 1),
                        itemWithPrice(XMaterial.DISPENSER, "Dropper dropper", NamedTextColor.GOLD, 60, 1),
                        itemWithPrice(XMaterial.TARGET, "Arrow dropper", NamedTextColor.GOLD, 20, 1),
                        itemWithPrice(XMaterial.REDSTONE_LAMP, "Rocket dropper", NamedTextColor.GOLD, 20, 1),
                        itemWithPrice(XMaterial.BEACON, "Golden apple dropper", NamedTextColor.GOLD, 40, 1),
                        itemWithPrice(XMaterial.BREWING_STAND, "Brewer", NamedTextColor.GOLD, 40, 1)
                )
        );

        final ItemStack stack;
        final List<CItem> items;

        ShopCategory(ItemStack stack, List<CItem> items) {
            this.stack = stack;
            this.items = items;
        }

        public ItemStack getStack() {
            return stack;
        }

        public List<CItem> getItems() {
            return items;
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
            if (!InventoryUtil.hasEnoughItems(event.player(), XMaterial.GOLD_NUGGET, price)) {
                event.player().sendMessage(GreedyGhosts.PREFIX.append(Component.text("You don't have enough gold nuggets to purchase this item.", NamedTextColor.RED)));
                event.player().closeInventory();
                return;
            }

            // Remove the pumpkins from the players inventory
            InventoryUtil.removeAmountFromInventory(event.player(), XMaterial.GOLD_NUGGET, price);

            event.player().getInventory().addItem(stack);
        }
    }
}
