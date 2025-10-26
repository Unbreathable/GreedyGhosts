package com.liphium.greedyghosts.screens;

import com.liphium.greedyghosts.GreedyGhosts;
import com.liphium.greedyghosts.game.HotbarKit;
import com.liphium.greedyghosts.game.state.IngameState;
import de.badgames.pluginCore.inventory.CItem;
import de.badgames.pluginCore.inventory.CScreen;
import de.badgames.pluginCore.util.ItemStackBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;

public class KitSelectionScreen extends CScreen {

    public static final int SCREEN_ID = 5;

    public KitSelectionScreen() {
        super(SCREEN_ID, Component.text("Kit selection", NamedTextColor.GOLD, TextDecoration.BOLD), 1, true);

        background();

        // 0 1 2 3 4 5 6 7 8
        int count = 0;
        for (HotbarKit kit : HotbarKit.kits) {

            // Build kit item
            ItemStack itemStack = new ItemStackBuilder(kit.material())
                    .withName(Component.text(kit.name(), NamedTextColor.GOLD))
                    .withLore(kit.generateLore().toArray(new Component[0]))
                    .buildStack();

            setItem(3 + count, new CItem(itemStack).onClick(event -> {

                // Set the kit in case ingame
                if(GreedyGhosts.getInstance().getGameManager().getCurrentState() instanceof IngameState state) {
                    state.setKit(event.player(), kit);
                }

                event.player().sendMessage(GreedyGhosts.PREFIX
                        .append(Component.text("You selected the", NamedTextColor.GRAY)).appendSpace()
                        .append(Component.text(kit.name(), NamedTextColor.YELLOW)).appendSpace()
                        .append(Component.text("kit.", NamedTextColor.GRAY))
                );
                event.player().closeInventory();
            }));
            count++;
        }
    }
}
