package de.badgames.elfhunt.listener.machines.impl;

import de.badgames.elfhunt.GreedyGhosts;
import de.badgames.elfhunt.game.state.IngameState;
import de.badgames.elfhunt.listener.machines.Machine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;


public class PresentReceiver extends Machine {

    // TODO:: use model engine for custom models.

    private final UUID uniqueId;
    private static final ArrayList<String> names = new ArrayList<>(List.of(
            "Julian", "Thorben", "Crash", "Jonas", "Shadow", "Tim",
            "Atlas", "Animo", "Louis", "Liph", "Ritso", "Glaze", "Jumbo",
            "JuliPvP99", "Night", "Maya", "Theo", "Prime", "Lofi Girl", "Alex",
            "Steve", "Arch", "Colin", "Dani", "Dave", "David", "Eric", "Lofi Boy",
            "Matt", "Elon Musk", "Linus LTT", "Neko", "Ben Dover", "CD", "CI/CD",
            "Maja", "Shadow the Hedgehog", "Sonic the Hedgehog", "Goku", "Vegeta",
            "Opel Corsa", "Astolfo", "Julien Bam", "Julienco", "Bibis Beauty Palace",
            "Zombey", "GLP", "Maudado", "Reeperk", "Lucy", "Steve Jobs", "Luffy",
            "Munjo", "Scrambled Eggs"
    ));

    /**
     * Generate a random name for a present receiver.
     * @return The random name
     */
    public static String randomName() {
        return names.get(ThreadLocalRandom.current().nextInt(names.size() - 1));
    }

    // The name chosen for the stand (only once assigned)
    String chosenName = "";

    public PresentReceiver(Location location) {
        super(location, false);

        ArmorStand stand = location.getWorld().spawn(location.clone(), ArmorStand.class);
        stand.setCustomNameVisible(true);
        stand.customName(Component.text("Unnamed", NamedTextColor.WHITE, TextDecoration.BOLD));
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setRemoveWhenFarAway(false);
        stand.setBasePlate(false);

        // Get her some drip
        stand.getEquipment().setHelmet(new ItemStack(Material.PLAYER_HEAD));
        ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
        ItemStack leggings = new ItemStack(Material.LEATHER_LEGGINGS);
        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        LeatherArmorMeta chestplateMeta = (LeatherArmorMeta) chestplate.getItemMeta();
        chestplateMeta.setColor(Color.fromRGB(255, 255, 255));
        chestplate.setItemMeta(chestplateMeta);
        LeatherArmorMeta leggingsMeta = (LeatherArmorMeta) leggings.getItemMeta();
        leggingsMeta.setColor(Color.fromRGB(255, 255, 255));
        leggings.setItemMeta(leggingsMeta);
        LeatherArmorMeta bootsMeta = (LeatherArmorMeta) boots.getItemMeta();
        bootsMeta.setColor(Color.fromRGB(255, 255, 255));
        boots.setItemMeta(bootsMeta);
        stand.getEquipment().setChestplate(chestplate);
        stand.getEquipment().setLeggings(leggings);
        stand.getEquipment().setBoots(boots);

        uniqueId = stand.getUniqueId();
    }

    boolean failedNameSet = false;

    /**
     * Change the name of the armor stand to the specified name.
     * @param name Name to change to
     */
    public void assignName(String name) {
        chosenName = name;

        final var stand = (ArmorStand) location.getWorld().getEntity(uniqueId);
        if(stand == null) {
            GreedyGhosts.getInstance().getLogger().warning("Tried to assign name to non-existent stand");
            GreedyGhosts.getInstance().getLogger().warning("Stands Id: " + uniqueId);
            failedNameSet = true;
            return;
        }

        failedNameSet = true;
        stand.customName(Component.text(chosenName, NamedTextColor.WHITE, TextDecoration.BOLD));
    }

    @Override
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        final var stand = (ArmorStand) location.getWorld().getEntity(uniqueId);
        if(stand == null) {
            GreedyGhosts.getInstance().getLogger().warning("Tried to interact with non-existent stand");
            GreedyGhosts.getInstance().getLogger().warning("Stands Id: " + uniqueId);
            return;
        }

        if (failedNameSet) {
            assignName(chosenName);
        }

        if (event.getRightClicked().equals(stand)) {
            if(GreedyGhosts.getInstance().getGameManager().getCurrentState() instanceof IngameState state) {
                state.onReceiverClicked(event.getPlayer(), chosenName);
            }
            event.setCancelled(true);
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
