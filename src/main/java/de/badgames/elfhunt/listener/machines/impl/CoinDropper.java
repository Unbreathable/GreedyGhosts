package de.badgames.elfhunt.listener.machines.impl;

import com.cryptomorin.xseries.XMaterial;
import de.badgames.pluginCore.util.ItemStackBuilder;
import de.badgames.elfhunt.listener.machines.Machine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.UUID;

public class CoinDropper extends Machine {

    private final UUID uniqueId;

    public CoinDropper(Location location, boolean breakable) {
        super(location, breakable);

        ArmorStand stand;
        if (breakable) {
            stand = location.getWorld().spawn(location.clone().add(0.5, -0.5, 0.5), ArmorStand.class);
        } else {
            stand = location.getWorld().spawn(location.clone().add(0, -1.5, 0), ArmorStand.class);
        }

        uniqueId = stand.getUniqueId();

        stand.setCustomNameVisible(true);
        stand.customName(Component.text("Coins", NamedTextColor.GOLD).appendSpace()
                .append(Component.text("are produced here.", NamedTextColor.GRAY)));
        stand.setGravity(false);
        stand.setInvisible(true);
        stand.setInvulnerable(true);
        stand.setRemoveWhenFarAway(false);
    }

    int count = 2, tickCount = 0;

    @Override
    public void destroy() {
        final var stand = (ArmorStand) location.getWorld().getEntity(uniqueId);
        if(stand == null) {
            return;
        }
        stand.remove();
    }

    @Override
    public void tick() {
        if (broken) {
            return;
        }

        if (tickCount++ >= 20) {
            tickCount = 0;
            final var stand = (ArmorStand) location.getWorld().getEntity(uniqueId);
            if(stand == null) {
                return;
            }

            count--;
            stand.customName(Component.text("Coin", NamedTextColor.GOLD).appendSpace()
                    .append(Component.text("in", NamedTextColor.GRAY)).appendSpace()
                    .append(Component.text(count, NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text("..", NamedTextColor.GRAY))
            );

            if (count == 0) {
                count = computeCount();

                // Drop the pumpkin where the machine is located
                ItemStack coin = new ItemStackBuilder(XMaterial.GOLD_NUGGET).buildStack();
                Item item;
                if (isBreakable()) {
                    item = location.getWorld().dropItem(location.clone().add(0.5, 1.5, 0.5), coin);
                } else {
                    item = location.getWorld().dropItem(location, coin);
                }
                item.setVelocity(new Vector());
            }
        }
    }

    int computeCount() {
        return Math.max(10 - Bukkit.getOnlinePlayers().size(), 3);
    }
}
