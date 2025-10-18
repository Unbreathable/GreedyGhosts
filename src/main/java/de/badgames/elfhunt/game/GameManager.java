package de.badgames.elfhunt.game;

import de.badgames.cloudhelper.CloudHelper;
import de.badgames.elfhunt.GreedyGhosts;
import de.badgames.elfhunt.game.state.IngameState;
import de.badgames.elfhunt.game.team.TeamManager;
import de.badgames.gameCore.GameState;
import de.badgames.gameCore.IGameManager;
import de.badgames.gameCore.events.GameMapChangeEvent;
import de.badgames.gameCore.events.GameMapChangedEvent;
import de.badgames.shared.state.LobbyState;
import de.badgames.gameCore.map.GenericMap;
import de.badgames.gameCore.map.IMap;
import de.badgames.shared.screens.TeamSelectionScreen;
import de.badgames.gameCore.team.Team;
import de.badgames.gameCore.util.MapLoader;
import de.badgames.pluginCore.PluginCore;
import de.badgames.shared.state.RestartState;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Arrays;

@Getter
public class GameManager implements IGameManager<GenericMap, Team> {

    private GameState currentState;

    private final TeamManager teamManager;

    private final MapLoader<GenericMap> mapLoader;

    private GenericMap map;

    public GameManager() {
        teamManager = new TeamManager();

        mapLoader = new MapLoader<>(GreedyGhosts.getInstance(), GenericMap.class);

        if (mapLoader.getMapCount() <= 0)
            throw new NullPointerException("No maps found");

        GreedyGhosts.getInstance().getLogger().info(mapLoader.getMapCount() + " maps loaded");
        GreedyGhosts.getInstance().getLogger().info(Arrays.toString(mapLoader.getAllMaps().stream().map(IMap::getName).toArray()));

        setCurrentState(new LobbyState(GreedyGhosts.getInstance(), this, GreedyGhosts.getInstance().getTaskManager(),
                Component.text("Elfhunt", NamedTextColor.GREEN, TextDecoration.BOLD), GreedyGhosts.PREFIX,
                2, getMaxTeamSize() * 2,
                x -> setCurrentState(new IngameState())));

        changeMap(mapLoader.getRandomMap(), null);
        GreedyGhosts.getInstance().getLogger().info("Current map: " + map.getName());
    }

    @Override
    public void setCurrentState(GameState currentState) {
        this.currentState = currentState;
        this.currentState.start();
    }
    @Override
    public void join(Player player) {
        this.currentState.join(player);

        TeamSelectionScreen<Team, GameManager> screen = (TeamSelectionScreen<Team, GameManager>) PluginCore.getInstance().getScreens().screen(1);
        screen.rebuild();
    }

    @Override
    public void quit(Player player) {
        this.currentState.quit(player);
    }

    @Override
    public void changeMap(GenericMap map, Player player) {
        if (!(currentState instanceof LobbyState))
            return;

        GameMapChangeEvent event = new GameMapChangeEvent(map, map, player);
        Bukkit.getPluginManager().callEvent(event);

        if (event.isCancelled())
            return;

        this.map = (GenericMap) event.getNewMap();
        CloudHelper.getCloudHandler().changeMOTD(map.getName());

        GameMapChangedEvent changedEvent = new GameMapChangedEvent(map, map, player);
        Bukkit.getPluginManager().callEvent(changedEvent);
    }

    @Override
    public int getMaxTeamSize() {
        return 20;
    }

    @Override
    public boolean canRunLobbyCommands() {
        return currentState instanceof LobbyState || currentState instanceof RestartState;
    }
}
