package com.liphium.greedyghosts.game.state;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XSound;
import de.badgames.cloudhelper.CloudHelper;
import de.badgames.gameCore.GameState;
import de.badgames.gameCore.IGameManager;
import de.badgames.gameCore.events.GameStartedEvent;
import de.badgames.gameCore.team.Team;
import de.badgames.pluginCore.PluginCore;
import de.badgames.pluginCore.util.ConfigUtil;
import de.badgames.pluginCore.util.ItemStackBuilder;
import de.badgames.pluginCore.util.Messages;
import de.badgames.pluginCore.util.TaskManager;
import de.badgames.shared.SharedGame;
import de.badgames.shared.api.PartyUtil;
import de.badgames.shared.util.PlayerUtil;
import me.catcoder.sidebar.ProtocolSidebar;
import me.catcoder.sidebar.Sidebar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.CreativeCategory;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.function.Consumer;

public class LobbyState extends GameState {
    public final int NEEDED_PLAYERS;
    public final int MAX_PLAYERS;
    private final Consumer<IGameManager<?, ?>> startCallback;

    public Sidebar<Component> scoreboard;

    public final int originalCount = 30;

    private final Component title, prefix;
    private final IGameManager<?, ?> manager;
    private final TaskManager taskManager;

    public LobbyState(JavaPlugin plugin, IGameManager<?, ?> gameManager, TaskManager taskManager, Component title, Component prefix, int neededPlayers, int maxPlayers, Consumer<IGameManager<?, ?>> startCallback) {
        super("Waiting for players", 30);
        this.title = title;
        this.prefix = prefix;
        this.manager = gameManager;
        this.taskManager = taskManager;
        this.startCallback = startCallback;
        NEEDED_PLAYERS = neededPlayers;
        MAX_PLAYERS = maxPlayers;
        scoreboard = ProtocolSidebar
                .newAdventureSidebar(title, plugin);

        scoreboard.addLine(Component.text("                                ", NamedTextColor.DARK_GRAY, TextDecoration.STRIKETHROUGH));

        scoreboard.addBlankLine();

        scoreboard.addLine(Component.space().append(Component.text("⚑", PluginCore.getPrimaryColor())).appendSpace()
                .append(Component.text("❙", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .appendSpace()
                .append(Component.text("Map", NamedTextColor.GRAY)));
        scoreboard.addUpdatableLine(player ->
                Component.space().append(Component.text("◆", PluginCore.getSecondaryColor())).appendSpace().append(Component.text("⏵⏵⏵", NamedTextColor.DARK_GRAY))
                        .appendSpace().append(Component.text(gameManager.getMap().getName(), PluginCore.getPrimaryColor())));

        scoreboard.addBlankLine();

        scoreboard.addLine(Component.space().append(Component.text("✎", PluginCore.getPrimaryColor())).appendSpace()
                .append(Component.text("❙", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .appendSpace()
                .append(Component.text("Status", NamedTextColor.GRAY)));

        scoreboard.addUpdatableLine(player -> {
            if (Bukkit.getOnlinePlayers().size() >= NEEDED_PLAYERS) {
                return Component.space().append(Component.text("◆", PluginCore.getSecondaryColor())).appendSpace().append(Component.text("⏵⏵⏵", NamedTextColor.DARK_GRAY))
                        .appendSpace().append(Component.text("Starting", PluginCore.getPrimaryColor())).appendSpace().append(Component.text("in", PluginCore.getPrimaryColor())).appendSpace()
                        .append(Component.text(count, NamedTextColor.RED, TextDecoration.BOLD)).appendSpace()
                        .append(Component.text("seconds", PluginCore.getPrimaryColor()));
            } else {
                return Component.space().append(Component.text("◆", PluginCore.getSecondaryColor())).appendSpace().append(Component.text("⏵⏵⏵", NamedTextColor.DARK_GRAY))
                        .appendSpace().append(Component.text("Waiting for players", PluginCore.getPrimaryColor()))
                        .appendSpace().append(Component.text(Bukkit.getOnlinePlayers().size(), PluginCore.getSecondaryColor())
                                .append(Component.text("/", NamedTextColor.GRAY)).append(Component.text(MAX_PLAYERS, PluginCore.getPrimaryColor())));
            }
        });

        scoreboard.addBlankLine();

        scoreboard.addLine(Component.text("                                ", NamedTextColor.DARK_GRAY, TextDecoration.STRIKETHROUGH));

        scoreboard.addBlankLine();

        scoreboard.addLine(Component.space().append(Component.text("❤", PluginCore.getPrimaryColor())).appendSpace()
                .append(Component.text("❙", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .appendSpace()
                .append(Component.text("Discord", NamedTextColor.GRAY)));
        scoreboard.addLine(Component.space().append(Component.text("◆", PluginCore.getSecondaryColor())).appendSpace().append(Component.text("⏵⏵⏵", NamedTextColor.DARK_GRAY))
                .appendSpace().append(Component.text("dc", PluginCore.getSecondaryColor(), TextDecoration.BOLD))
                .append(Component.text(".", NamedTextColor.GRAY))
                .append(Component.text("badgames", PluginCore.getPrimaryColor(), TextDecoration.BOLD))
                .append(Component.text(".", NamedTextColor.GRAY))
                .append(Component.text("de", PluginCore.getSecondaryColor(), TextDecoration.BOLD)));
        scoreboard.addBlankLine();

        scoreboard.updateLinesPeriodically(5, 10);
    }

    Runnable startRunnable;

    @Override
    public void start() {
        CloudHelper.getCloudHandler().setLobby();
        CloudHelper.getCloudHandler().setMaxPlayers(MAX_PLAYERS);
        CloudHelper.getCloudHandler().setProperty("teams", String.valueOf(manager.getTeamManager().getTeams().size()));
        Location location = ConfigUtil.getLocation("lobby");
        if (location != null && location.getWorld() != null) {
            location.getWorld().setThundering(false);
            location.getWorld().setStorm(false);
            location.getWorld().setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            location.getWorld().setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            location.getWorld().setGameRule(GameRule.DO_MOB_SPAWNING, false);
            location.getWorld().setGameRule(GameRule.DO_TRADER_SPAWNING, false);
            location.getWorld().setGameRule(GameRule.DO_PATROL_SPAWNING, false);
            location.getWorld().setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
            location.getWorld().setDifficulty(Difficulty.PEACEFUL);
        } else {
            Bukkit.broadcast(Component.text("Please set up the server first.", NamedTextColor.RED));
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setHealth(20);
            player.setFoodLevel(20);
            player.getActivePotionEffects().clear();
        }

        taskManager.inject(startRunnable = new Runnable() {
            int tickCount = 0;

            @Override
            public void run() {

                if (tickCount++ >= 20) {
                    tickCount = 0;

                    if (!Bukkit.getOnlinePlayers().isEmpty() && Bukkit.getOnlinePlayers().size() >= NEEDED_PLAYERS) {
                        if (!paused) count--;

                        if (count <= 5) {

                            if (count == 0) {
                                forceStart(null);
                                return;
                            }

                            for (Player player : Bukkit.getOnlinePlayers()) {
                                player.showTitle(Title.title(
                                        Component.text(count, NamedTextColor.RED, TextDecoration.BOLD)
                                                .append(Component.text("..", PluginCore.getPrimaryColor())),
                                        title,
                                        Title.Times.times(Duration.ofSeconds(0), Duration.ofSeconds(3), Duration.ofSeconds(1))
                                ));
                                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
                            }

                        } else if (count % 10 == 0 && count <= 100) {
                            Bukkit.broadcast(prefix
                                    .append(Component.text("The ", PluginCore.getPrimaryColor()))
                                    .append(Component.text("game ", PluginCore.getSecondaryColor()))
                                    .append(Component.text("starts in ", PluginCore.getPrimaryColor()))
                                    .append(Component.text(count + " seconds", NamedTextColor.RED))
                                    .append(Component.text(".", PluginCore.getPrimaryColor()))
                            );
                        }

                        if (paused) {
                            Messages.actionBar(Component.text("Countdown ", PluginCore.getPrimaryColor())
                                    .append(Component.text("paused", NamedTextColor.RED)));
                        } else {
                            Messages.actionBar(Component.text(count, NamedTextColor.RED, TextDecoration.BOLD)
                                    .append(Component.text("..", PluginCore.getPrimaryColor())));
                        }
                    } else {
                        Messages.actionBar(Component.text("Waiting for ", PluginCore.getPrimaryColor())
                                .append(Component.text("players", PluginCore.getSecondaryColor()))
                                .append(Component.text(".. (", PluginCore.getPrimaryColor()))
                                .append(Component.text(Bukkit.getOnlinePlayers().size(), PluginCore.getSecondaryColor()))
                                .append(Component.text("/", PluginCore.getPrimaryColor()))
                                .append(Component.text(NEEDED_PLAYERS, PluginCore.getSecondaryColor()))
                                .append(Component.text(")",PluginCore.getPrimaryColor())));
                        count = originalCount;
                    }

                }

            }
        });

    }

    boolean alreadyStarted = false;

    public void forceStart(Player player) {
        if (alreadyStarted) return;
        if (player != null) {
            if (player.hasPermission("game.start")) {
                if (player.hasCooldown(XMaterial.NETHER_STAR.parseMaterial())) {
                    player.sendMessage(prefix.append(Component.text("Please wait before trying again.", PluginCore.getPrimaryColor())));
                    return;
                }

                player.setCooldown(XMaterial.NETHER_STAR.parseMaterial(), 20 * 5);

                if (Bukkit.getOnlinePlayers().size() < NEEDED_PLAYERS && !player.hasPermission("game.bypass-min")) {
                    player.sendMessage(prefix.append(Component.text("There are not enough players online!", NamedTextColor.RED)));
                    return;
                }
            } else {
                player.sendMessage(prefix.append(Component.text("You don't have the permission to do that!", NamedTextColor.RED)));
                return;
            }
        }

        alreadyStarted = true;

        for (Player all : Bukkit.getOnlinePlayers()) {
            all.showTitle(Title.title(
                    title,
                    Component.text(manager.getMap().getName(), NamedTextColor.RED),
                    Title.Times.times(Duration.ofSeconds(0), Duration.ofSeconds(3), Duration.ofSeconds(1))
            ));
            all.playSound(all.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        }

        Bukkit.broadcast(prefix
                .append(Component.text(manager.getMap().getName(), NamedTextColor.RED))
                .appendSpace()
                .append(Component.text("by", NamedTextColor.GRAY))
                .appendSpace()
                .append(Component.text(manager.getMap().getAuthor(), NamedTextColor.RED))
                .append(Component.text(".", NamedTextColor.GRAY))
        );

        scoreboard.destroy();
        taskManager.uninject(startRunnable);
        startCallback.accept(manager);
        GameStartedEvent gameStartingEvent = new GameStartedEvent(manager.getMap(), player);
        Bukkit.getPluginManager().callEvent(gameStartingEvent);
    }

    @Override
    public void onSpawn(EntitySpawnEvent event) {
        super.onSpawn(event);
    }

    @Override
    public void onPhysics(BlockPhysicsEvent event) {
        super.onPhysics(event);
        var typ = event.getSourceBlock().getType();
        var typName = typ.name().toUpperCase();
        if ((typName.endsWith("DOOR") && !typName.contains("TRAP")) || typName.endsWith("PLATE") ||
                typName.endsWith("BUTTON") || typName.endsWith("LEVER") ||
                (typ.getCreativeCategory() == CreativeCategory.REDSTONE && !typName.contains("TRAP"))) {
            return;
        }
        event.setCancelled(true);
    }

    @Override
    public void onInventoryClick(InventoryClickEvent event) {
        super.onInventoryClick(event);
        if (event.getWhoClicked() instanceof Player) {
            var item = event.getCurrentItem();

            if (item == null) {
                return;
            }

            if (XMaterial.NETHER_STAR.isSimilar(item) || XMaterial.MAP.isSimilar(item) ||
                    XMaterial.BOOK.isSimilar(item) || XMaterial.SLIME_BALL.isSimilar(item) ||
                    XMaterial.RED_BED.isSimilar(item)) {
                event.setResult(Event.Result.DENY);
            }
        }
    }

    @Override
    public void onInteract(PlayerInteractEvent event) {
        super.onInteract(event);
        if (event.getItem() == null) return;
        if (XMaterial.RED_BED.isSimilar(event.getItem())) {
            PluginCore.getInstance().getScreens().open(event.getPlayer(), 1);
        } else if (XMaterial.MAP.isSimilar(event.getItem())) {
            XSound.BLOCK_CHEST_OPEN.play(event.getPlayer(), 0.5f, 1f);
            PluginCore.getInstance().getScreens().open(event.getPlayer(), 2);
            event.setCancelled(true);
        } else if (XMaterial.BOOK.isSimilar(event.getItem())) {
            XSound.BLOCK_CHEST_OPEN.play(event.getPlayer(), 0.5f, 1f);
            PluginCore.getInstance().getScreens().open(event.getPlayer(), 3);
        } else if (XMaterial.SLIME_BALL.isSimilar(event.getItem())) {
            if (!CloudHelper.getCloudHandler().sendPlayerToLobby(event.getPlayer().getUniqueId())) {
                event.getPlayer().kick();
            }
        } else if (XMaterial.NETHER_STAR.isSimilar(event.getItem())) {
            XSound.ENTITY_PLAYER_LEVELUP.play(event.getPlayer(), 0.5f, 1f);
            forceStart(event.getPlayer());
        }
    }

    @Override
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        super.onInteractAtEntity(event);
        event.setCancelled(true);
    }

    @Override
    public void onDamage(EntityDamageEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onBreak(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode().equals(GameMode.CREATIVE)) return;
        event.setCancelled(true);
    }

    @Override
    public void onDrop(PlayerDropItemEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onPlace(BlockPlaceEvent event) {
        if (event.getPlayer().getGameMode().equals(GameMode.CREATIVE)) return;
        event.setCancelled(true);
    }

    @Override
    public void onCraft(CraftItemEvent event) {
        super.onCraft(event);
        event.setResult(Event.Result.DENY);
        event.setCancelled(true);
    }

    @Override
    public void onFood(FoodLevelChangeEvent event) {
        super.onFood(event);
        event.setCancelled(true);
    }

    @Override
    public void join(Player player) {
        super.join(player);
        if (Bukkit.getOnlinePlayers().size() > MAX_PLAYERS) {
            player.kick(Component.text("The server is full!", NamedTextColor.RED));
            return;
        }

        if (SharedGame.isShouldUseParty()) {
            PartyUtil.attemptPartyTeamJoin(player, manager.getTeamManager());
        }

        scoreboard.addViewer(player);

        player.setGameMode(GameMode.SURVIVAL);

        player.getInventory().clear();
        player.getInventory().setHelmet(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setBoots(null);

        player.clearActivePotionEffects();
        player.setHealth(20);
        player.setFoodLevel(20);

        if (player.hasPermission("game.start")) {
            int offset = 1;
            player.getInventory().setItem(0, new ItemStackBuilder(XMaterial.NETHER_STAR).withName("§5→ §dStart §7(Right-click)")
                    .withLore("§dStart the match.").buildStack());

            if (player.hasPermission("game.map")) {
                offset = 2;
                player.getInventory().setItem(1, new ItemStackBuilder(XMaterial.MAP).withName("§5→ §dChange Map §7(Right-click)")
                        .withLore("§dChange the map.").buildStack());
            }

            if (manager.getTeamManager().getTeams().size() > 1) {
                player.getInventory().setItem(offset, new ItemStackBuilder(XMaterial.RED_BED).withName("§5→ §dTeams §7(Right-click)")
                        .withLore("§dChange your Team.").buildStack());
            }
        } else {
            int offset = 0;
            if (player.hasPermission("game.map")) {
                offset = 1;
                player.getInventory().setItem(0, new ItemStackBuilder(XMaterial.MAP).withName("§5→ §dChange Map §7(Right-click)")
                        .withLore("§dChange the map.").buildStack());
            }

            if (manager.getTeamManager().getTeams().size() > 1) {
                player.getInventory().setItem(offset, new ItemStackBuilder(XMaterial.RED_BED).withName("§5→ §dTeams §7(Right-click)")
                        .withLore("§dChange your team.").buildStack());
            }
        }


        if (PlayerUtil.isConnected()) {
            player.getInventory().setItem(7, new ItemStackBuilder(XMaterial.BOOK).withName("§5→ §dAchievements §7(Right-click)")
                    .withLore("§dCheck your achievements.").buildStack());
        }

        player.getInventory().setItem(8, new ItemStackBuilder(XMaterial.SLIME_BALL).withName("§5→ §dQuit")
                .withLore("§dGo back to the Lobby.").buildStack());

        taskManager.inject(new Runnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks++ >= 10) {
                    player.teleport(ConfigUtil.getLocation("lobby"));
                    taskManager.uninject(this);
                }
            }
        });
    }

    @Override
    public void quit(Player player) {
        super.quit(player);
        Team playerTeam = manager.getTeamManager().getTeam(player);
        if (playerTeam != null) {
            playerTeam.leave(player);
        }

        scoreboard.removeViewer(player);
    }
}
