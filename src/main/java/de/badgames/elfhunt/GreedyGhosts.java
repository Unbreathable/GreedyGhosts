package de.badgames.elfhunt;

import de.badgames.elfhunt.game.GameManager;
import de.badgames.elfhunt.listener.ChatListener;
import de.badgames.elfhunt.listener.JoinQuitListener;
import de.badgames.elfhunt.listener.machines.MachineManager;
import de.badgames.elfhunt.screens.BrewingScreen;
import de.badgames.elfhunt.screens.ItemShopScreen;
import de.badgames.cloudhelper.CloudHelper;
import de.badgames.shared.SharedGame;
import de.badgames.gameCore.map.GenericMap;
import de.badgames.shared.screens.AchievementScreen;
import de.badgames.shared.screens.MapSelectionScreen;
import de.badgames.shared.screens.TeamSelectionScreen;
import de.badgames.gameCore.util.YAMLFixerUtil;
import de.badgames.pluginCore.PluginCore;
import de.badgames.pluginCore.util.TaskManager;
import lombok.Getter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class GreedyGhosts extends JavaPlugin {
    public static final Component PREFIX = Component.text("Greedy Ghosts", NamedTextColor.GREEN).appendSpace()
            .append(Component.text("→", PluginCore.getSecondaryColor()).appendSpace());

    @Getter
    private static GreedyGhosts instance;

    @Getter
    private TaskManager taskManager;

    @Getter
    private GameManager gameManager;

    @Getter
    private MachineManager machineManager;

    @Override
    public void onEnable() {
        instance = this;
        PluginCore.init(instance);
        CloudHelper.init();
        YAMLFixerUtil.load();

        ConfigurationSerialization.registerClass(GenericMap.class, "GenericMap");

        taskManager = new TaskManager();
        taskManager.initTask(instance);

        machineManager = new MachineManager();

        gameManager = new GameManager();

        CloudHelper.getCloudHandler().setMaxPlayers(GreedyGhosts.getInstance().getGameManager().getMaxTeamSize() * 2);

        Listener[] listeners = new Listener[]{new ChatListener(), new JoinQuitListener()};
        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }

        SharedGame.init(instance, PREFIX, gameManager, List.of(), "greedyghosts");

        PluginCore.getInstance().getScreens().register(new TeamSelectionScreen<>(gameManager), new BrewingScreen(), new ItemShopScreen(), new MapSelectionScreen<>(PREFIX, gameManager), new AchievementScreen("eh"));
    }

    @Override
    public void onDisable() {
    }
}
