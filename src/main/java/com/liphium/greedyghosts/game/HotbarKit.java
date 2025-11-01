package com.liphium.greedyghosts.game;

import com.cryptomorin.xseries.XMaterial;
import de.badgames.pluginCore.util.ItemStackBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;

public record HotbarKit(String name, XMaterial material, ItemStack[] items) {

    // All kits in the game
    public static final List<HotbarKit> kits = List.of(
            witchKit(),
            undeadKit(),
            maniacKit()
    );

    public static HotbarKit witchKit() {
        return new HotbarKit("Witch", XMaterial.POISONOUS_POTATO, new ItemStack[]{
                null,
                new ItemStackBuilder(XMaterial.WOODEN_AXE).makeUnbreakable().buildStack(),
                new ItemStackBuilder(XMaterial.WOODEN_SHOVEL).makeUnbreakable().buildStack(),
                new ItemStackBuilder(XMaterial.WOODEN_PICKAXE).makeUnbreakable().buildStack(),
                null,
                null,
                new ItemStackBuilder(XMaterial.LINGERING_POTION).withName("Lingering Potion of Regeneration I").withBasePotionType(PotionType.REGENERATION).withAmount(2).buildStack(),
                new ItemStackBuilder(XMaterial.SPLASH_POTION).withName("Splash Potion of Slowness I").withBasePotionType(PotionType.SLOWNESS).withAmount(3).buildStack(),
                new ItemStackBuilder(XMaterial.SPLASH_POTION).withName("Splash Potion of Harming I").withBasePotionType(PotionType.HARMING).withAmount(2).buildStack(),
        });
    }

    public static HotbarKit undeadKit() {
        return new HotbarKit("Undead", XMaterial.DEAD_BUSH, new ItemStack[]{
                null,
                new ItemStackBuilder(XMaterial.WOODEN_AXE).makeUnbreakable().buildStack(),
                new ItemStackBuilder(XMaterial.WOODEN_SHOVEL).makeUnbreakable().buildStack(),
                new ItemStackBuilder(XMaterial.WOODEN_PICKAXE).makeUnbreakable().buildStack(),
                null,
                null,
                null,
                null,
                new ItemStackBuilder(XMaterial.ZOMBIE_SPAWN_EGG).withAmount(2).buildStack(),
        });
    }

    public static HotbarKit maniacKit() {
        return new HotbarKit("Maniac", XMaterial.TNT, new ItemStack[]{
                null,
                new ItemStackBuilder(XMaterial.WOODEN_AXE).makeUnbreakable().buildStack(),
                new ItemStackBuilder(XMaterial.WOODEN_SHOVEL).makeUnbreakable().buildStack(),
                new ItemStackBuilder(XMaterial.WOODEN_PICKAXE).makeUnbreakable().buildStack(),
                null,
                null,
                null,
                new ItemStackBuilder(XMaterial.TNT).withAmount(2).buildStack(),
                new ItemStackBuilder(XMaterial.CREEPER_SPAWN_EGG).withAmount(1).buildStack(),
        });
    }

    /**
     * Give this kit to a player.
     * @param player The player
     */
    public void giveKit(Player player) {
        player.getInventory().clear();

        int index = 0;
        for(ItemStack item : items) {
            if(item != null) {
                player.getInventory().setItem(index, item);
            }
            index++;
        }
    }

    /**
     * Generate a lore for the kit's description.
     * @return All the text components for the lore
     */
    public List<Component> generateLore() {
        List<Component> lore = new ArrayList<>();

        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }

            // Get display name or material name
            String itemName;
            if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
                itemName = ((net.kyori.adventure.text.TextComponent) item.getItemMeta().displayName()).content();
            } else {
                // Convert material name to readable format
                itemName = formatMaterialName(item.getType().name());
            }

            Component loreLine = Component.text("-", NamedTextColor.GRAY).appendSpace()
                    .append(Component.text(item.getAmount() + "x ", NamedTextColor.GRAY))
                    .append(Component.text(itemName, NamedTextColor.GOLD));

            lore.add(loreLine);
        }

        return lore;
    }

    private String formatMaterialName(String materialName) {
        // Convert DIAMOND_SWORD to "Diamond Sword"
        String[] parts = materialName.toLowerCase().split("_");
        StringBuilder formatted = new StringBuilder();

        for (String part : parts) {
            if (!formatted.isEmpty()) {
                formatted.append(" ");
            }
            formatted.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1));
        }

        return formatted.toString();
    }

}
