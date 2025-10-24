package com.liphium.greedyghosts.listener.machines.impl;

import com.cryptomorin.xseries.XMaterial;
import de.badgames.pluginCore.util.ItemStackBuilder;
import com.liphium.greedyghosts.listener.machines.Machine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.util.Vector;

import java.util.UUID;

public class RocketDropper extends Machine {

    private final UUID uniqueId;

    public RocketDropper(Location location) {
        super(location, true);

        ArmorStand stand = location.getWorld().spawn(location.clone().add(0.5, -0.5, 0.5), ArmorStand.class);

        stand.setCustomNameVisible(true);
        stand.customName(Component.text("Rockets", NamedTextColor.RED).appendSpace()
                .append(Component.text("are produced here.", NamedTextColor.GRAY)));
        stand.setGravity(false);
        stand.setInvisible(true);
        stand.setInvulnerable(true);
        stand.setRemoveWhenFarAway(false);

        uniqueId = stand.getUniqueId();
    }

    int count = 20, tickCount = 0;

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
            stand.customName(Component.text("Rocket", NamedTextColor.RED).appendSpace()
                    .append(Component.text("in", NamedTextColor.GRAY)).appendSpace()
                    .append(Component.text(count, NamedTextColor.RED, TextDecoration.BOLD)).appendSpace()
                    .append(Component.text("..", NamedTextColor.GRAY))
            );

            if (count == 0) {
                count = 20;

                ItemStack rocket = new ItemStackBuilder(XMaterial.FIREWORK_ROCKET).buildStack();
                FireworkMeta meta = (FireworkMeta) rocket.getItemMeta();
                meta.setPower(3);
                meta.addEffect(FireworkEffect.builder().withColor(Color.RED).build());
                rocket.setItemMeta(meta);

                Item item = location.getWorld().dropItem(location.clone().add(0.5, 1.5, 0.5), rocket);
                item.setVelocity(new Vector());
            }
        }
    }

    @Override
    public void destroy() {
        final var stand = (ArmorStand) location.getWorld().getEntity(uniqueId);
        if(stand == null) {
            return;
        }
        stand.remove();
    }
}
