package de.badgames.elfhunt.listener;

import de.badgames.elfhunt.GreedyGhosts;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinQuitListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.joinMessage(null);
        GreedyGhosts.getInstance().getGameManager().join(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        event.quitMessage(null);
        GreedyGhosts.getInstance().getGameManager().quit(event.getPlayer());
    }

}
