package de.badgames.elfhunt.game.team.impl;

import com.cryptomorin.xseries.XMaterial;
import de.badgames.elfhunt.GreedyGhosts;
import de.badgames.gameCore.team.Team;
import de.badgames.pluginCore.PluginCore;
import de.badgames.prefix.api.PrefixApi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Objects;


public class HunterTeam extends Team {

    public HunterTeam(int maxPlayers) {
        super("Hunters", "⑫", "RED", "§c§l", XMaterial.IRON_SWORD, maxPlayers);
    }

    @Override
    public void giveKit(Player player, boolean teleport) {
        if (teleport) {
            player.teleport(Objects.requireNonNull(GreedyGhosts.getInstance().getGameManager().getMapLocation("Hunters")));
        }
    }

    @Override
    public void sendStartMessage() {
        for (Player player : getPlayers()) {

            player.sendMessage(Component.text(" "));
            player.sendMessage(Component.text("    §7You are a §a§lhunter§7!"));
            player.sendMessage(Component.text(" "));
            player.sendMessage(Component.text("§7Prevent the §aelves §7from giving out"));
            player.sendMessage(Component.text("§apresents §7and be happy about it."));
            player.sendMessage(Component.text(" "));

        }
    }

    @Override
    public void handleWin() {

        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("   §cThe §c§lHunters §7won the §cgame§7!"));
        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("§7The §celves §7weren't able to hand out all"));
        Bukkit.broadcast(Component.text("§cpresents §7in time. What a shame!"));
        Bukkit.broadcast(Component.text(" "));

        for (Player player : getPlayers()) {
            player.showTitle(Title.title(
                    Component.text("Victory Royale", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.empty(),
                    Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(1))
            ));
            player.playSound(player.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_1, 1f, 1f);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!containsPlayer(player)) {
                player.showTitle(Title.title(
                        Component.text("Game Over", NamedTextColor.RED, TextDecoration.BOLD),
                        Component.empty(),
                        Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(1))
                ));
                player.playSound(player.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_1, 1f, 1f);
            }
        }
    }

    @Override
    public void join(Player player) {
        super.join(player);
        player.sendMessage(GreedyGhosts.PREFIX.append(Component.text("You joined the team", PluginCore.getPrimaryColor())).appendSpace().append(Component.text(getDisplayName(), NamedTextColor.WHITE)));
    }

    @Override
    public void leave(Player player) {
        super.leave(player);
        player.sendMessage(GreedyGhosts.PREFIX.append(Component.text("You left the team", PluginCore.getPrimaryColor())).appendSpace().append(Component.text(getDisplayName(), NamedTextColor.WHITE)));
    }

    @Override
    public PrefixApi getPrefixApi() {
        return GreedyGhosts.getInstance().getGameManager().getTeamManager().getPrefixApi();
    }
}
