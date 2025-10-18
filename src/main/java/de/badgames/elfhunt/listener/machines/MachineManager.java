package de.badgames.elfhunt.listener.machines;

import de.badgames.elfhunt.GreedyGhosts;
import de.badgames.elfhunt.listener.machines.impl.*;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;

public class MachineManager {

    private final ArrayList<Machine> machines = new ArrayList<>();

    public MachineManager() {
    }

    public void loadMachines() {
        machines.clear();
        ArrayList<String> registered = new ArrayList<>();

        // Add all machines (all the ones that can be spawned by location)
        registered.add("CoinDropper");
        registered.add("PresentReceiver");
        registered.add("PresentGiver");
        registered.add("ItemShop");

        for (String s : registered) {
            for (int i = 1; i <= 1000; i++) {
                if (GreedyGhosts.getInstance().getGameManager().isSetMap(s + i)) {
                    machines.add(newMachineByLocation(s, GreedyGhosts.getInstance().getGameManager().getMapLocation(s + i)));
                } else break;
            }
        }
    }

    public <T> ArrayList<T> getMachines(Class<T> clazz) {
        final var toReturn = new ArrayList<T>();
        for (Machine machine : machines) {
            if (machine.getClass().getSimpleName().equals(clazz.getSimpleName())) {
                toReturn.add((T) machine);
            }
        }

        return toReturn;
    }

    public Machine getMachine(Location location) {
        for (Machine machine : machines) {
            if (machine.getLocation().distance(location) <= 0.1) {
                return machine;
            }
        }

        return null;
    }

    public void onInteract(PlayerInteractEvent event) {
        for (Machine machine : machines) {
            machine.onInteract(event);
        }
    }

    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        for (Machine machine : machines) {
            machine.onInteractAtEntity(event);
        }
    }

    public ArrayList<Machine> getMachines() {
        return machines;
    }

    public Machine newMachineByLocation(String name, Location location) {
        return switch (name) {
            case "CoinDropper" -> new CoinDropper(location, false);
            case "PresentReceiver" -> new PresentReceiver(location);
            case "PresentGiver" -> new PresentGiver(location);
            case "ItemShop" -> new ItemShop(location);
            default -> null;
        };
    }

    public Machine newMachineByMaterial(Material material, Location location) {
        return switch (material) {
            case Material.GOLD_ORE -> new CoinDropper(location, true);
            case Material.WHITE_CONCRETE ->  new ItemDropper(location, "Iron", NamedTextColor.WHITE, new ItemStack(Material.IRON_INGOT), 20);
            case Material.CYAN_CONCRETE ->  new ItemDropper(location, "Diamond", NamedTextColor.AQUA, new ItemStack(Material.DIAMOND), 30);
            case Material.RED_CONCRETE ->  new ItemDropper(location, "Redstone", NamedTextColor.RED, new ItemStack(Material.REDSTONE), 10);
            case Material.DISPENSER ->  new ItemDropper(location, "Dropper", NamedTextColor.YELLOW, new ItemStack(Material.DROPPER), 30);
            case Material.TARGET ->  new ItemDropper(location, "Arrow", NamedTextColor.RED, new ItemStack(Material.ARROW), 30);
            case Material.BEACON ->  new ItemDropper(location, "Golden apple", NamedTextColor.GOLD, new ItemStack(Material.GOLDEN_APPLE), 30);
            case Material.REDSTONE_LAMP -> new RocketDropper(location);
            case Material.BREWING_STAND -> new Brewer(location);
            default -> null;
        };
    }

    public boolean breakLocation(Location location) {
        Machine toRemove = null;
        for (Machine machine : machines) {
            if (machine.isBreakable() && machine.getLocation().getBlock().getLocation().equals(location)) {
                machine.destroy();
                toRemove = machine;
            }
        }

        if (toRemove != null) {
            machines.remove(toRemove);
            return true;
        }

        return false;
    }

    public void addMachine(Machine machine) {
        machines.add(machine);
    }

    public void tick() {
        for (Machine machine : machines) {
            machine.tick();
        }
    }

}
