package de.badgames.elfhunt.game.team;

import de.badgames.elfhunt.GreedyGhosts;
import de.badgames.elfhunt.game.team.impl.HunterTeam;
import de.badgames.elfhunt.game.team.impl.ElfTeam;
import de.badgames.gameCore.team.ITeamManager;
import de.badgames.gameCore.team.Team;
import de.badgames.prefix.api.PrefixApi;
import org.bukkit.Bukkit;

import java.util.ArrayList;

public class TeamManager  implements ITeamManager<Team> {

    private final ArrayList<Team> teams = new ArrayList<>();

    PrefixApi prefixApi;

    public TeamManager() {
        prefixApi = Bukkit.getServicesManager().load(PrefixApi.class);

        // Register teams
        addTeam(new ElfTeam(GreedyGhosts.getInstance().getGameManager().getMaxTeamSize()));
        addTeam(new HunterTeam(GreedyGhosts.getInstance().getGameManager().getMaxTeamSize()));
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
