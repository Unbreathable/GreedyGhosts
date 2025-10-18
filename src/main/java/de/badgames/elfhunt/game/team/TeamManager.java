package de.badgames.elfhunt.game.team;

import de.badgames.elfhunt.game.GameManager;
import de.badgames.elfhunt.game.team.impl.FarmerTeam;
import de.badgames.elfhunt.game.team.impl.GhostTeam;
import de.badgames.gameCore.team.ITeamManager;
import de.badgames.gameCore.team.Team;
import de.badgames.prefix.api.PrefixApi;
import org.bukkit.Bukkit;

import java.util.ArrayList;

public class TeamManager  implements ITeamManager<Team> {

    private final ArrayList<Team> teams = new ArrayList<>();

    PrefixApi prefixApi;

    public TeamManager(GameManager manager) {
        prefixApi = Bukkit.getServicesManager().load(PrefixApi.class);

        // Register teams
        addTeam(new GhostTeam(manager.getMaxTeamSize()));
        addTeam(new FarmerTeam(manager.getMaxTeamSize()));
        registerPrefixGroups();
    }

    @Override
    public PrefixApi getPrefixApi() {
        return prefixApi;
    }

    @Override
    public ArrayList<Team> getTeams() {
        return teams;
    }
}
