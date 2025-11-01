package com.liphium.greedyghosts.game.state;

import com.cryptomorin.xseries.XMaterial;
import com.liphium.greedyghosts.GreedyGhosts;
import de.badgames.cloudhelper.CloudHelper;
import de.badgames.gameCore.GameState;
import de.badgames.gameCore.IGameManager;
import de.badgames.pluginCore.PluginCore;
import de.badgames.pluginCore.util.ConfigUtil;
import de.badgames.pluginCore.util.ItemStackBuilder;
import de.badgames.pluginCore.util.Messages;
import de.badgames.pluginCore.util.TaskManager;
import me.catcoder.sidebar.ProtocolSidebar;
import me.catcoder.sidebar.Sidebar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.CreativeCategory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;
import java.util.List;

public class EndState extends GameState {

    public Sidebar<Component> scoreboard;

    public JavaPlugin pluginInstance;
    public final int NEEDED_PLAYERS;
    public final int MAX_PLAYERS;

    private final Consumer<IGameManager<?, ?>> startCallback;
    private Consumer<IGameManager<?, ?>> nextState;

    private final Component title, prefix;
    private final IGameManager<?, ?> manager;
    private final TaskManager taskManager;

    public EndState(JavaPlugin plugin, IGameManager<?, ?> gameManager, TaskManager taskManager, Component title, Component prefix, int neededPlayers, int maxPlayers, Consumer<IGameManager<?, ?>> startCallback) {
        this(plugin, gameManager, taskManager, title, prefix, neededPlayers, maxPlayers, startCallback, null);
    }

    public EndState(JavaPlugin plugin, IGameManager<?, ?> gameManager, TaskManager taskManager, Component title, Component prefix, int neededPlayers, int maxPlayers, Consumer<IGameManager<?, ?>> startCallback, Consumer<IGameManager<?, ?>> nextState) {
        super("Ending", 30);
        this.pluginInstance = plugin;
        this.title = title;
        this.prefix = prefix;
        this.manager = gameManager;
        this.taskManager = taskManager;
        this.startCallback = startCallback;
        this.nextState = nextState;
        NEEDED_PLAYERS = neededPlayers;
        MAX_PLAYERS = maxPlayers;

        scoreboard = ProtocolSidebar
                .newAdventureSidebar(title, plugin);

        scoreboard.addLine(Component.text("                                ", NamedTextColor.DARK_GRAY, TextDecoration.STRIKETHROUGH));

        scoreboard.addBlankLine();

        scoreboard.addLine(Component.space().append(Component.text("✎", PluginCore.getPrimaryColor())).appendSpace()
                .append(Component.text("❙", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .appendSpace()
                .append(Component.text("Status", NamedTextColor.GRAY)));

        scoreboard.addLine(Component.space().append(Component.text("◆", PluginCore.getSecondaryColor())).appendSpace().append(Component.text("⏵⏵⏵", NamedTextColor.DARK_GRAY))
                .appendSpace().append(Component.text("Game has ended", PluginCore.getPrimaryColor())));

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

    @Override
    public void start() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            scoreboard.addViewer(player);
            player.setGameMode(GameMode.SURVIVAL);
            player.teleport(ConfigUtil.getLocation("lobby"));

            player.getInventory().clear();
            player.getInventory().setHelmet(null);
            player.getInventory().setChestplate(null);
            player.getInventory().setLeggings(null);
            player.getInventory().setBoots(null);

            player.clearActivePotionEffects();
            player.setHealth(20);
            player.setFoodLevel(20);

            for (Player allOther : Bukkit.getOnlinePlayers()) {
                player.showPlayer(pluginInstance, allOther);
            }
        }

        for (int i = 0; i <= 9; i++) {
            var fireworkLocation = ConfigUtil.getLocation("firework_" + i);
            if (fireworkLocation == null) continue;
            var world = fireworkLocation.getWorld();

            Firework firework = world.spawn(fireworkLocation, Firework.class);
            FireworkMeta meta = firework.getFireworkMeta();

            meta.setPower(2); // Flight duration
            meta.addEffect(FireworkEffect.builder()
                    .withColor(Color.RED)
                    .withFade(Color.ORANGE)
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .trail(true)
                    .flicker(true)
                    .build());

            firework.setFireworkMeta(meta);
        }

        taskManager.inject(new Runnable() {
            int tickCount = 0;

            @Override
            public void run() {

                if (tickCount++ >= 20) {
                    tickCount = 0;
                    if (!paused) count--;

                    Messages.actionBar(Component.text("Stopping ", NamedTextColor.RED)
                            .append(Component.text("in ", NamedTextColor.GRAY))
                            .append(Component.text(count, NamedTextColor.RED, TextDecoration.BOLD))
                            .append(Component.text("..", NamedTextColor.GRAY))
                    );

                    if (count == 0) {
                        List<Player> players = Bukkit.getOnlinePlayers().stream().map(x -> (Player)x).toList();
                        for (Player all : players) {
                            if (!CloudHelper.getCloudHandler().sendPlayerToLobby(all.getUniqueId())) {
                                all.kick();
                            }
                        }
                        scoreboard.destroy();
                        GreedyGhosts.getInstance().getServer().shutdown();
                        taskManager.uninject(this);
                    }
                }

            }
        });
    }

    @Override
    public void onInteract(PlayerInteractEvent event) {
        super.onInteract(event);
        if (event.getItem() == null) return;
        if (XMaterial.SLIME_BALL.isSimilar(event.getItem())) {
            if (!CloudHelper.getCloudHandler().sendPlayerToLobby(event.getPlayer().getUniqueId())) {
                event.getPlayer().kick();
            }
        }
    }

    @Override
    public void onBreak(BlockBreakEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onPlace(BlockPlaceEvent event) {
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
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onDamage(EntityDamageEvent event) {
        event.setCancelled(true);
    }

    @Override
    public void onDrop(PlayerDropItemEvent event) {
        super.onDrop(event);
        event.setCancelled(true);
    }

    @Override
    public void onInventoryClick(InventoryClickEvent event) {
        super.onInventoryClick(event);
        event.setCancelled(true);
    }
}
