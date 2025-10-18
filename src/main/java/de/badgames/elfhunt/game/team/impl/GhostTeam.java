package de.badgames.elfhunt.game.team.impl;

import com.cryptomorin.xseries.XMaterial;
import de.badgames.elfhunt.GreedyGhosts;
import de.badgames.gameCore.team.Team;
import de.badgames.pluginCore.PluginCore;
import de.badgames.prefix.api.PrefixApi;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Objects;

public class GhostTeam extends Team {

    public GhostTeam(int maxPlayers) {
        super("Ghosts", "§f§lGhosts", "WHITE", "§f§l", XMaterial.SKELETON_SKULL, maxPlayers);
    }

    @Override
    public void giveKit(Player player, boolean teleport) {
        if (teleport) {
            player.teleport(Objects.requireNonNull(GreedyGhosts.getInstance().getGameManager().getMapLocation("Center")));
        }
    }

    @Override
    public void sendStartMessage() {
        for (Player player : getPlayers()) {

            player.sendMessage(Component.text(" "));
            player.sendMessage(Component.text("    You are a", NamedTextColor.GRAY).appendSpace()
                    .append(Component.text("Ghost", NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text("!", NamedTextColor.GRAY)));
            player.sendMessage(Component.text(" "));
            player.sendMessage(Component.text("Steal all", NamedTextColor.GRAY).appendSpace()
                    .append(Component.text("snacks", NamedTextColor.WHITE)).appendSpace()
                    .append(Component.text("before the", NamedTextColor.GRAY)));
            player.sendMessage(Component.text("timer", NamedTextColor.WHITE).appendSpace()
                    .append(Component.text("hits zero!", NamedTextColor.GRAY)));
            player.sendMessage(Component.text(" "));
        }
    }

    @Override
    public void handleWin() {

        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("   The").appendSpace()
                .append(Component.text("Ghosts", NamedTextColor.WHITE, TextDecoration.BOLD)).appendSpace()
                .append(Component.text("won the", NamedTextColor.GRAY)).appendSpace()
                .append(Component.text("game", NamedTextColor.WHITE))
                .append(Component.text("!", NamedTextColor.GRAY)));
        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("All", NamedTextColor.GRAY).appendSpace()
                .append(Component.text("snacks", NamedTextColor.WHITE)).appendSpace()
                .append(Component.text("have been stolen by", NamedTextColor.GRAY)));
        Bukkit.broadcast(Component.text("the", NamedTextColor.GRAY).appendSpace()
                .append(Component.text("ghosts", NamedTextColor.WHITE, TextDecoration.BOLD))
                .append(Component.text("! What a sad Halloween...", NamedTextColor.GRAY)));
        Bukkit.broadcast(Component.text(" "));

        for (Player player : getPlayers()) {
            player.showTitle(Title.title(
                    Component.text("VICTORY", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.empty(),
                    Title.Times.times(Duration.ofSeconds(1), Duration.ofSeconds(3), Duration.ofSeconds(1))
            ));
            player.playSound(player.getLocation(), Sound.ITEM_GOAT_HORN_SOUND_1, 1f, 1f);
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!containsPlayer(player)) {
                player.showTitle(Title.title(
                        Component.text("DEFEAT", NamedTextColor.RED, TextDecoration.BOLD),
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
    }

    @Override
    public void leave(Player player) {
        super.leave(player);
    }

    @Override
    public PrefixApi getPrefixApi() {
        return GreedyGhosts.getInstance().getGameManager().getTeamManager().getPrefixApi();
    }
}
