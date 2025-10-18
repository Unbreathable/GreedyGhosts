package de.badgames.elfhunt.listener.machines.impl;

import de.badgames.elfhunt.listener.machines.Machine;
import de.badgames.pluginCore.PluginCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

public class Brewer extends Machine {

    private final UUID uniqueId;

    public Brewer(Location location) {
        super(location, true);

        ArmorStand stand = location.getWorld().spawn(location.clone().add(0.5, -0.5, 0.5), ArmorStand.class);

        stand.setCustomNameVisible(true);
        stand.customName(Component.text("Click the", NamedTextColor.GRAY).appendSpace()
                .append(Component.text("brewing stand", NamedTextColor.GOLD))
                .append(Component.text(".", NamedTextColor.GRAY)));
        stand.setGravity(false);
        stand.setInvisible(true);
        stand.setVisible(false);
        stand.setInvulnerable(true);
        stand.setRemoveWhenFarAway(false);

        uniqueId = stand.getUniqueId();
    }

    @Override
    public void destroy() {
        final var stand = (ArmorStand) location.getWorld().getEntity(uniqueId);
        if(stand == null) {
            return;
        }
        stand.remove();
    }

    int tickCount = 0, count = 60;
    public ItemStack currentPotion;

    @Override
    public void tick() {
        if (broken) {
            return;
        }

        if (currentPotion != null) {
            if (tickCount++ >= 20) {
                count--;
                tickCount = 0;

                final var stand = (ArmorStand) location.getWorld().getEntity(uniqueId);
                if(stand == null) {
                    return;
                }

                // Set the custom name of the brew stand
                stand.customName(Component.text()
                        .append(Objects.requireNonNull(currentPotion.getItemMeta().displayName())).appendSpace()
                        .append(Component.text("in", NamedTextColor.GRAY)).appendSpace()
                        .append(Component.text(count, NamedTextColor.GOLD, TextDecoration.BOLD))
                        .append(Component.text("..", NamedTextColor.GRAY))
                        .build()
                );

                if (count == 0) {
                    stand.customName(Component.text("Click the", NamedTextColor.GRAY).appendSpace()
                            .append(Component.text("brewing stand", NamedTextColor.GOLD))
                            .append(Component.text(".", NamedTextColor.GRAY)));
                    count = 60;
                    location.getWorld().dropItem(location.clone().add(0.5, 1, 0.5), currentPotion);
                    currentPotion = null;
                }
            }
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


    @Override
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) {
            if (!event.getClickedBlock().equals(location.getBlock())) return;

            PluginCore.getInstance().getScreens().open(event.getPlayer(), 4);
            clickedBrewer.put(event.getPlayer(), this);
            event.setCancelled(true);
        }
    }

    final public static HashMap<Player, Brewer> clickedBrewer = new HashMap<>();

    @Override
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        final var stand = (ArmorStand) location.getWorld().getEntity(uniqueId);
        if(stand == null) {
            return;
        }

        if (event.getRightClicked().equals(stand)) {
            PluginCore.getInstance().getScreens().open(event.getPlayer(), 4);
            clickedBrewer.put(event.getPlayer(), this);
            event.setCancelled(true);
        }
    }
}
