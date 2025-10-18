package de.badgames.elfhunt.screens;

import com.cryptomorin.xseries.XMaterial;
import de.badgames.pluginCore.inventory.CItem;
import de.badgames.pluginCore.inventory.CScreen;
import de.badgames.pluginCore.util.ItemStackBuilder;
import de.badgames.elfhunt.listener.machines.impl.Brewer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

public class BrewingScreen extends CScreen {

    public BrewingScreen() {
        super(4, Component.text("Brauen", NamedTextColor.RED, TextDecoration.BOLD), 3, true);

        background();

        PotionType[] potions = new PotionType[]{
                PotionType.SWIFTNESS,
                PotionType.STRONG_LEAPING,
                PotionType.LONG_REGENERATION,
        };

        // 9 10 11 12 13 14 15 16 17
        int count = 0;
        for (PotionType data : potions) {

            // Build potion
            ItemStack itemStack = new ItemStackBuilder(XMaterial.LINGERING_POTION)
                    .withName(((TextComponent)Component.text(convertToReadable(data.name()), NamedTextColor.GOLD).appendSpace()
                            .append(Component.text("potion", NamedTextColor.GRAY))).content())
                    .buildStack();
            PotionMeta meta = (PotionMeta) itemStack.getItemMeta();
            meta.setBasePotionType(data);
            itemStack.setItemMeta(meta);

            setItem(12 + count, new CItem(itemStack).onClick(event -> {
                Brewer brewer = Brewer.clickedBrewer.get(event.player());
                // Build potion
                ItemStack potion = new ItemStack(Material.LINGERING_POTION);
                PotionMeta potionMeta = (PotionMeta) itemStack.getItemMeta();
                potionMeta.setBasePotionType(data);
                potionMeta.displayName(Component.text(convertToReadable(data.name()), NamedTextColor.GOLD).appendSpace()
                        .append(Component.text("potion", NamedTextColor.GRAY)));
                potion.setItemMeta(potionMeta);

                if (brewer.currentPotion != null) {
                    event.player().closeInventory();
                    event.player().sendMessage(Component.text("A potion is already being produced.", NamedTextColor.RED));
                    return;
                }

                brewer.currentPotion = potion;
                event.player().closeInventory();
            }));
            count++;
        }
    }

    public static String convertToReadable(String input) {
        input = input.toLowerCase().replace("_", " "); // Convert to lowercase and replace underscores with spaces
        String[] words = input.split(" ");

        // Capitalize only the first word
        words[0] = words[0].substring(0, 1).toUpperCase() + words[0].substring(1);

        // Join the words back together into a sentence
        return String.join(" ", words);
    }

}
