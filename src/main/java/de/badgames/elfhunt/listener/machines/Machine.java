package de.badgames.elfhunt.listener.machines;

import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class Machine {

    @Getter
    public final Location location;
    @Getter
    private final boolean breakable;
    public boolean broken = false;

    public Machine(Location location, boolean breakable) {
        this.location = location;
        this.breakable = breakable;
    }

    public void tick() {
    }

    public void destroy() {
    }

    public void onInteract(PlayerInteractEvent event) {
    }

    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
    }

}
