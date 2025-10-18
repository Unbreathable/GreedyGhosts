package de.badgames.elfhunt.game.state;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XSound;
import de.badgames.cloudhelper.CloudHelper;
import de.badgames.gameCore.GameState;
import de.badgames.gameCore.events.PlayerKillEvent;
import de.badgames.shared.state.EndState;
import de.badgames.gameCore.map.GenericMap;
import de.badgames.gameCore.team.Team;
import de.badgames.shared.util.PlayerUtil;
import de.badgames.pluginCore.util.TimeFormatter;
import de.badgames.pluginCore.PluginCore;
import de.badgames.pluginCore.util.ConfigUtil;
import de.badgames.pluginCore.util.ItemStackBuilder;
import de.badgames.elfhunt.GreedyGhosts;
import de.badgames.elfhunt.game.team.impl.ElfTeam;
import de.badgames.elfhunt.game.team.impl.HunterTeam;
import de.badgames.elfhunt.listener.machines.impl.PresentReceiver;
import de.badgames.pluginCore.util.Messages;
import me.catcoder.sidebar.ProtocolSidebar;
import me.catcoder.sidebar.Sidebar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.NumberConversions;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class IngameState extends GameState {
    private final ArrayList<DroppableTrap> traps = new ArrayList<>();

    private Runnable runnable;

    public Sidebar<Component> scoreboard;

    final long MAX_GAME_TIME;
    long currentGameTime = 0;
    int maxPresents = 0;

    private int presentsLeft = 0;
    /**
     * Map with the format NPC Name - NPC Instance
     */
    private final HashMap<String, PresentReceiver> receivers = new HashMap<>();
    private final HashMap<Location, Boolean> placedBlocks = new HashMap<>();

    /**
     * Map with format Player - NPC Name
     */
    private final HashMap<Player, String> currentDelivery = new HashMap<>();

    public IngameState() {
        super("In game", 30);
        MAX_GAME_TIME = Duration.ofMinutes(NumberConversions.toInt(ConfigUtil.get("game.time"))).getSeconds() * 20;
        scoreboard = ProtocolSidebar
                .newAdventureSidebar(Component.text("Elfhunt", NamedTextColor.GREEN, TextDecoration.BOLD), GreedyGhosts.getInstance());

        scoreboard.addLine(Component.text("                                ", NamedTextColor.DARK_GRAY, TextDecoration.STRIKETHROUGH));

        scoreboard.addBlankLine();

        scoreboard.addLine(Component.space().append(Component.text("⌛", PluginCore.getPrimaryColor())).appendSpace()
                .append(Component.text("❙", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .appendSpace()
                .append(Component.text("Remaining Time", NamedTextColor.GRAY)));
        scoreboard.addUpdatableLine(player -> Component.space().append(Component.text("◆", PluginCore.getSecondaryColor())).appendSpace().append(Component.text("⏵⏵⏵", NamedTextColor.DARK_GRAY))
                .appendSpace().append(Component.text(TimeFormatter.formatTicks(currentGameTime), NamedTextColor.RED, TextDecoration.BOLD)).appendSpace()
                .append(Component.text("left!", PluginCore.getPrimaryColor())));


        scoreboard.addBlankLine();

        scoreboard.addLine(Component.space().append(Component.text("✉", PluginCore.getPrimaryColor())).appendSpace()
                .append(Component.text("❙", NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
                .appendSpace()
                .append(Component.text("Remaining Presents", NamedTextColor.GRAY)));

        scoreboard.addUpdatableLine(player -> Component.space().append(Component.text("◆", PluginCore.getSecondaryColor())).appendSpace().append(Component.text("⏵⏵⏵", NamedTextColor.DARK_GRAY))
                .appendSpace().append(Component.text(presentsLeft, NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text("/", NamedTextColor.GRAY))
                .append(Component.text(maxPresents, NamedTextColor.GREEN, TextDecoration.BOLD)).appendSpace()
                .append(Component.text("remaining!", PluginCore.getPrimaryColor())));

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
        CloudHelper.getCloudHandler().setInGame();
        GreedyGhosts.getInstance().getMachineManager().loadMachines();

        ArrayList<Player> playersWithOutTeam = new ArrayList<>(Bukkit.getOnlinePlayers());
        playersWithOutTeam.removeIf(x -> GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(x) != null);

        for (Player player : playersWithOutTeam) {
            Team team = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeamWithLeastPlayers();
            team.join(player);
        }

        // Change the amount of presents based on team size
        final var hunterSize = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam("Elves").getPlayers().size();
        maxPresents = hunterSize * 4; // 4 per member of the team seems fine for 15 minutes
        presentsLeft = maxPresents;

        final GenericMap map = GreedyGhosts.getInstance().getGameManager().getMap();
        World world = Bukkit.getWorld(map.getWorldName());

        world.setDifficulty(Difficulty.NORMAL);
        world.setTime(18000);
        world.setThundering(false);
        world.setStorm(true);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.NATURAL_REGENERATION, false);

        for (Player all : Bukkit.getOnlinePlayers()) {
            scoreboard.addViewer(all);
            PlayerUtil.clearPlayerMetaData(all);
        }

        for (Team team : GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeams()) {
            team.sendStartMessage();

            for (Player player : team.getPlayers()) {
                player.getInventory().clear();
                player.setHealth(20);
                team.giveKit(player, true);
            }
        }

        // Give every single present receiver a random name
        for (PresentReceiver receiver : GreedyGhosts.getInstance().getMachineManager().getMachines(PresentReceiver.class)) {
            var name = PresentReceiver.randomName();
            while (receivers.containsKey(name)) {
                name = PresentReceiver.randomName();
            }
            receiver.assignName(name);
            receivers.put(name, receiver);
        }

        currentGameTime = MAX_GAME_TIME;

        GreedyGhosts.getInstance().getTaskManager().inject(runnable = new Runnable() {
            int tickCount = 0;

            @Override
            public void run() {
                GreedyGhosts.getInstance().getGameManager().getTeamManager().tick();
                GreedyGhosts.getInstance().getMachineManager().tick();

                if (tickCount++ >= 20) {
                    tickCount = 0;

                    // Check if the win condition for the hunters is met
                    if (currentGameTime <= 0) {
                        handleWin(GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam("Hunters"));
                        return;
                    }

                    Messages.actionBar(Component.text(presentsLeft, NamedTextColor.GREEN)
                            .append(Component.text("/", NamedTextColor.GRAY))
                            .append(Component.text(maxPresents, NamedTextColor.GREEN))
                            .appendSpace()
                            .append(Component.text("remaining", NamedTextColor.GRAY))
                            .appendSpace()
                            .append(Component.text("|", NamedTextColor.DARK_GRAY))
                            .appendSpace()
                            .append(Component.text(TimeFormatter.formatTicks(currentGameTime)).appendSpace()
                                    .append(Component.text("left", NamedTextColor.GRAY)))
                    );
                }

                currentGameTime--;
            }
        });
    }

    @Override
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() != null && event.getItem().getType() == Material.WIND_CHARGE) {
            return;
        }

        GreedyGhosts.getInstance().getMachineManager().onInteract(event);

        if (event.getItem() != null) {
            Team team = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(event.getPlayer());
            ItemStack usedItem = event.getItem();
            if (usedItem.getType().equals(Material.GRAY_DYE) && event.getClickedBlock() != null) {
                traps.add(new SlowTrap(event.getClickedBlock().getLocation().clone().add(0.5, 1, 0.5), team));
                reduceMainHandItem(event.getPlayer(), Material.GRAY_DYE);
            } else if (usedItem.getType().equals(Material.GREEN_DYE) && event.getClickedBlock() != null) {
                traps.add(new PoisonTrap(event.getClickedBlock().getLocation().clone().add(0.5, 1, 0.5), team));
                reduceMainHandItem(event.getPlayer(), Material.GREEN_DYE);
            } else if (usedItem.getType().equals(Material.FEATHER) && event.getClickedBlock() != null) {
                traps.add(new FlyTrap(event.getClickedBlock().getLocation().clone().add(0.5, 1, 0.5), team));
                reduceMainHandItem(event.getPlayer(), Material.FEATHER);
            } else if (usedItem.getType().equals(Material.LIGHT_BLUE_DYE) && event.getClickedBlock() != null) {
                traps.add(new FreezeTrap(event.getClickedBlock().getLocation().clone().add(0.5, 1, 0.5), team));
                reduceMainHandItem(event.getPlayer(), Material.LIGHT_BLUE_DYE);
            } else if (usedItem.getType().equals(Material.WHITE_DYE) && event.getClickedBlock() != null) {
                traps.add(new WebTrap(event.getClickedBlock().getLocation().clone().add(0.5, 1, 0.5), team));
                reduceMainHandItem(event.getPlayer(), Material.WHITE_DYE);
            } else if (usedItem.getType().equals(Material.FEATHER) && event.getClickedBlock() != null) {
                traps.add(new FlyTrap(event.getClickedBlock().getLocation().clone().add(0.5, 1, 0.5), team));
                reduceMainHandItem(event.getPlayer(), Material.FEATHER);
            } else if (usedItem.getType().equals(Material.LEATHER_HORSE_ARMOR)) {
                event.setCancelled(true);
            }
        }
    }

    void reduceMainHandItem(Player player, Material material) {
        if (player.getInventory().getItemInMainHand().getType() == material) {
            int amount = player.getInventory().getItemInMainHand().getAmount();
            if (amount == 1) {
                player.getInventory().setItemInMainHand(null);
            } else player.getInventory().getItemInMainHand().setAmount(amount - 1);
        } else if (player.getInventory().getItemInOffHand().getType() == material) {
            int amount = player.getInventory().getItemInOffHand().getAmount();
            if (amount == 1) {
                player.getInventory().setItemInOffHand(null);
            } else player.getInventory().getItemInOffHand().setAmount(amount - 1);
        }
    }

    @Override
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        GreedyGhosts.getInstance().getMachineManager().onInteractAtEntity(event);

        if (event.getRightClicked().getType().equals(EntityType.ARMOR_STAND)) {
            event.setCancelled(true);
        }
    }

    private final ArrayList<String> messages = new ArrayList<>(List.of(
            "§7How dare you deliver this to me? This present is for §a%player%§7!",
            "§7Are you too stupid to read? The name on the present clearly says §a%player%§7!",
            "§7Why are you giving me this? It’s clearly for §a%player%§7!",
            "§7I don’t want this! This belongs to §a%player%§7!",
            "§7Is there something wrong with your eyes? This is meant for §a%player%§7!",
            "§7I’m not §a%player%§7! Take this to the right person!",
            "§7Seriously? This is for §a%player%§7, not me!",
            "§7You’ve got the wrong person! This is for §a%player%§7!",
            "§7Don’t waste my time! This clearly says it’s for §a%player%§7!",
            "§7I think you’re lost — this is meant for §a%player%§7!",
            "§7Stop being careless! This is for §a%player%§7, not me!",
            "§7Do I look like §a%player%§7 to you? Are you blind?",
            "§7This isn’t mine — it’s for §a%player%§7!",
            "§7Take a closer look. This belongs to §a%player%§7!",
            "§7How can you mix this up? It’s clearly for §a%player%§7!",
            "§7Not my name on the present—it’s §a%player%§7’s!",
            "§7This isn’t funny. Give this to §a%player%§7!",
            "§7Read the label! It’s for §a%player%§7!",
            "§7Stop bothering me and give this to §a%player%§7!",
            "§7I’m not the recipient! This is meant for §a%player%§7!",
            "§7You’ve made a mistake—this belongs to §a%player%§7!",
            "§7Clearly, you didn’t read the tag. This is for §a%player%§7!"
    ));


    public void onReceiverClicked(Player player, String name) {
        if (GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(player) instanceof ElfTeam team) {

            // Make sure the guy actually has a present
            if (!currentDelivery.containsKey(player)) {
                player.sendMessage(Component.text("§f§l" + name + "§7: You don't even have a present! Get one from §cSanta §7first."));
                return;
            }

            if (Objects.equals(currentDelivery.get(player), name)) {
                player.getInventory().remove(Material.LEATHER_HORSE_ARMOR);
                player.getInventory().remove(Material.COAL);
                presentsLeft -= 1;
                currentDelivery.remove(player);

                PlayerUtil.addStats(player, "elf.gifts.delivered", 1);
                PlayerUtil.addAchievement(player, "eh.gift_giver", true);

                if (!player.hasMetadata("elfhunt.hit")) {
                    PlayerUtil.addAchievement(player, "eh.silent_sneaker", true);
                } else {
                    player.removeMetadata("elfhunt.hit", GreedyGhosts.getInstance());
                }

                // Send an announcement that a present has been delivered
                Bukkit.broadcast(Component.text(" "));
                Bukkit.broadcast(Component.text("§a§l" + player.getName() + " §7delivered a §apresent§7!"));
                Bukkit.broadcast(Component.text(" "));

                reduceMainHandItem(player, Material.LEATHER_HORSE_ARMOR);
                reduceMainHandItem(player, Material.COAL);

                XSound.ENTITY_EXPERIENCE_ORB_PICKUP.play(player, 0.7f, 1f);
                XSound.ITEM_GOAT_HORN_SOUND_1.play(player.getLocation(), 0.5f, 1f);
                // Check if the win condition for the elves is met
                if (presentsLeft <= 0) {
                    PlayerUtil.addAchievement(player, "eh.holiday_hero", true);
                    handleWin(team);
                }
            } else {
                var message = messages.get(ThreadLocalRandom.current().nextInt(messages.size()));
                message = message.replace("%player%", currentDelivery.get(player));
                player.sendMessage(Component.text("§f§l" + name + "§7: " + message));
            }
        }
    }

    public void onGiverClicked(Player player) {
        if (GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(player) instanceof ElfTeam) {
            if (currentDelivery.containsKey(player)) {
                player.sendMessage(Component.text("§c§lSanta§7: I already gave you a present!"));
                return;
            }

            // Get a random receiver to bring the present to
            final var randomInt = ThreadLocalRandom.current().nextInt(receivers.size());
            final var receiver = receivers.keySet().stream().toList().get(randomInt);

            // Assign that receiver for the player
            currentDelivery.put(player, receiver);
            player.getInventory().addItem(new ItemStackBuilder(XMaterial.RED_WOOL)
                    .withName(Component.text("Present", NamedTextColor.RED))
                    .withLore(Component.text("For:", NamedTextColor.RED).appendSpace()
                            .append(Component.text(receiver, NamedTextColor.GRAY))).buildStack());
            player.sendMessage(Component.text("§c§lSanta§7: Bring this present to §c" + receiver + "§7!"));
            PlayerUtil.addStats(player, "elf.gifts.received", 1);
        }
    }

    @Override
    public void onMove(PlayerMoveEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // Kill the player in case they fell down
        if (event.getPlayer().getLocation().getY() <= 156) {
            event.getPlayer().setHealth(0);
            return;
        }

        Team team = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(event.getPlayer());

        // Check if they wandered into a trap
        ArrayList<DroppableTrap> toRemove = new ArrayList<>();
        for (DroppableTrap trap : traps) {
            if (trap.location.distance(event.getPlayer().getLocation()) <= 3 && !team.getName().equals(trap.team.getName())) {
                toRemove.add(trap);
                trap.onEnter(event.getPlayer());
                PlayerUtil.addStats(event.getPlayer(), "elf.traps.triggered", 1);
                break;
            }
        }
        for (DroppableTrap rem : toRemove) {
            rem.item.remove();
            traps.remove(rem);
        }

    }

    @Override
    public void onDamage(EntityDamageEvent event) {
        event.setCancelled(false);
    }

    @Override
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity().getType() == EntityType.ARMOR_STAND) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onDrop(PlayerDropItemEvent event) {
        super.onDrop(event);
        if (event.getItemDrop().getItemStack().getType().equals(Material.LEATHER_HORSE_ARMOR)) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().clear();
    }

    @Override
    public void onPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType().equals(Material.REDSTONE_TORCH)) {
            event.setCancelled(true);
            return;
        }

        if (event.getBlockPlaced().getLocation().getY() >= 250) {
            event.setCancelled(true);
            return;
        }

        for (DroppableTrap trap : traps) {
            if (trap.location.distance(event.getBlock().getLocation()) <= 2) {
                event.getPlayer().sendMessage(Component.text("You can't place a block near a trap!", NamedTextColor.RED));
                event.setCancelled(true);
                return;
            }
        }

        // Place a machine if it is one
        final var machine = GreedyGhosts.getInstance().getMachineManager().newMachineByMaterial(event.getBlockPlaced().getType(), event.getBlockPlaced().getLocation());
        if (machine != null) {
            PlayerUtil.addStats(event.getPlayer(), "elf.traps.placed", 1);
            GreedyGhosts.getInstance().getMachineManager().addMachine(machine);
        } else {

            // Add the block as placed otherwise
            placedBlocks.put(event.getBlock().getLocation(), true);
        }
    }

    final List<Material> grassTypes = Arrays.asList(
            Material.TALL_GRASS, Material.SHORT_GRASS, Material.CORNFLOWER,
            Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID, Material.ALLIUM,
            Material.AZURE_BLUET, Material.RED_TULIP, Material.ORANGE_TULIP,
            Material.WHITE_TULIP, Material.PINK_TULIP, Material.OXEYE_DAISY, Material.SUNFLOWER,
            Material.LILAC, Material.ROSE_BUSH, Material.PEONY,
            Material.LILY_OF_THE_VALLEY, Material.WITHER_ROSE, Material.COBWEB,
            Material.FERN, Material.SWEET_BERRY_BUSH
    );

    @Override
    public void onBreak(BlockBreakEvent event) {
        if (GreedyGhosts.getInstance().getMachineManager().breakLocation(event.getBlock().getLocation())) {
            event.setDropItems(false);
            return;
        }

        // Only let placed blocks be broken again
        if (placedBlocks.get(event.getBlock().getLocation()) != null) {
            placedBlocks.remove(event.getBlock().getLocation());
            return;
        }

        // Let grass blocks be removed permanently (for PvP)
        if (grassTypes.contains(event.getBlock().getType())) {
            event.setDropItems(false);
            event.setCancelled(false);
            return;
        }

        event.setCancelled(true);
    }

    @Override
    public void onDeath(PlayerDeathEvent event) {
        final var player = event.getPlayer();

        player.getInventory().clear();
        player.getInventory().setBoots(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setHelmet(null);
        event.deathMessage(null);
        event.setKeepLevel(true);

        Team team = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(player);

        PlayerUtil.addStats(player, "elf.deaths", 1);
        if (player.getKiller() != null) {
            PlayerUtil.addStats(player.getKiller(), "elf.kills", 1);
            Team killerTeam = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(player.getKiller());
            Bukkit.broadcast(GreedyGhosts.PREFIX.append(Component.text(team.getChatColor() + player.getName() + " §7was killed by " + killerTeam.getChatColor() + player.getKiller().getName() + "§7!")));

            Bukkit.getPluginManager().callEvent(new PlayerKillEvent<>(player, player.getKiller(), team, killerTeam, true));

            if (killerTeam instanceof HunterTeam && team instanceof ElfTeam) {
                PlayerUtil.addAchievement(player.getKiller(), "eh.elf_slayer", true);
                int kills = player.hasMetadata("elf_killed_elfs") ? player.getMetadata("elf_killed_elfs").getFirst().asInt() : 0;
                player.getKiller().setMetadata("elf_killed_elfs", new FixedMetadataValue(GreedyGhosts.getInstance(), ++kills));

                if (currentDelivery.containsKey(player)) {
                    String npcName = currentDelivery.get(player);

                    PresentReceiver receiver = receivers.get(npcName);
                    if (receiver != null) {
                        if (PlayerUtil.getDistance(receiver.location, player.getLocation()) <= 5) {
                            PlayerUtil.addAchievement(player.getKiller(), "eh.festive_defender", true);
                        }
                    }
                }

                if (kills >= 10) {
                    if (kills >= 20) {
                        PlayerUtil.addAchievement(player, "eh.elf_exterminator", true);
                    } else {
                        PlayerUtil.addAchievement(player, "eh.christmas_ruiner", true);
                    }
                }
            }
        } else {
            PlayerUtil.addStats(player, "elf.suicides", 1);
            Bukkit.broadcast(GreedyGhosts.PREFIX.append(Component.text(team.getChatColor() + player.getName() + " §7died!")));
        }

        event.getDrops().removeIf(x -> x.getType() == Material.LEATHER_HORSE_ARMOR);

        // Make sure the player isn't still delivering
        currentDelivery.remove(player);

        GreedyGhosts.getInstance().getTaskManager().inject(new Runnable() {
            int tickCount = 0;

            @Override
            public void run() {
                if (tickCount++ >= 1) {
                    if (player.isDead()) {
                        player.spigot().respawn();
                        player.getInventory().clear();
                        player.setHealth(20);
                    }
                    GreedyGhosts.getInstance().getTaskManager().uninject(this);
                }
            }
        });
    }

    @Override
    public void onRespawn(PlayerRespawnEvent event) {
        final var team = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(event.getPlayer());
        if (team instanceof HunterTeam) {
            event.setRespawnLocation(Objects.requireNonNull(GreedyGhosts.getInstance().getGameManager().getMapLocation("Hunters")));
        } else {
            event.setRespawnLocation(Objects.requireNonNull(GreedyGhosts.getInstance().getGameManager().getMapLocation("Elves")));
        }
    }

    public void handleWin(Team team) {
        team.handleWin();
        team.getPlayers().forEach(player -> {
            PlayerUtil.addStats(player, "elf.win", 1);
        });

        for (Player all : Bukkit.getOnlinePlayers()) {
            if (!team.getPlayers().contains(all)) {
                PlayerUtil.addStats(all, "elf.loss", 1);
            }
        }
        scoreboard.destroy();
        GreedyGhosts.getInstance().getTaskManager().uninject(runnable);
        GreedyGhosts.getInstance().getGameManager().setCurrentState(new EndState(GreedyGhosts.getInstance(), GreedyGhosts.getInstance().getGameManager(), GreedyGhosts.getInstance().getTaskManager(),
                Component.text("Elfhunt", NamedTextColor.GREEN, TextDecoration.BOLD), GreedyGhosts.PREFIX,
                2, GreedyGhosts.getInstance().getGameManager().getMaxTeamSize() * 2,
                x -> GreedyGhosts.getInstance().getGameManager().setCurrentState(new IngameState())));
    }

    @Override
    public void onSpawn(EntitySpawnEvent event) {
        if (event.getEntityType().equals(EntityType.ITEM)
                || event.getEntityType().equals(EntityType.FIREWORK_ROCKET)
                || event.getEntityType().equals(EntityType.ARMOR_STAND)
                || event.getEntityType().equals(EntityType.POTION)
                || event.getEntityType().equals(EntityType.AREA_EFFECT_CLOUD)
                || event.getEntityType().equals(EntityType.WIND_CHARGE)
                || event.getEntityType().equals(EntityType.BREEZE_WIND_CHARGE)
                || event.getEntityType().equals(EntityType.TNT)
                || event.getEntityType().equals(EntityType.ARROW)) {
            return;
        }
        event.setCancelled(true);
    }

    @Override
    public void join(Player player) {
        super.join(player);
        scoreboard.addViewer(player);
        player.setGameMode(GameMode.SPECTATOR);

        for (Player all : Bukkit.getOnlinePlayers()) {
            all.hidePlayer(GreedyGhosts.getInstance(), player);
        }

        (new BukkitRunnable() {
            @Override
            public void run() {
                player.teleportAsync(GreedyGhosts.getInstance().getGameManager().getMapLocation("Elves"));
            }
        }).runTaskLater(GreedyGhosts.getInstance(), 10);
    }

    @Override
    public void quit(Player player) {
        scoreboard.removeViewer(player);
        Team team = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(player);
        if (team == null) return;
        team.removePlayer(player);
        PlayerUtil.addStats(player, "elf.disconnects", 1);

        // Make sure the team loses if there are no players left
        if (team.getPlayers().isEmpty()) {
            if (team instanceof HunterTeam) {
                handleWin(GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam("Elves"));
            } else {
                handleWin(GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam("Hunters"));
            }
        }
    }

    public static abstract class DroppableTrap {
        public final Location location;
        public final Team team;
        public final Item item;
        public final long start;

        public DroppableTrap(Location location, Team team, Material material) {
            this.location = location;
            this.team = team;

            item = (Item) location.getWorld().spawnEntity(location.clone().add(0, 0.5, 0), EntityType.ITEM);
            item.setItemStack(new ItemStackBuilder(material).buildStack());
            item.setVelocity(new Vector(0, 0, 0));
            item.setPickupDelay(1000000000);
            item.setCanPlayerPickup(false);
            item.setCanMobPickup(false);
            item.setUnlimitedLifetime(true);
            start = System.currentTimeMillis();
        }

        public abstract void onEnter(Player player);
    }

    public static class SlowTrap extends DroppableTrap {

        SlowTrap(Location location, Team team) {
            super(location, team, Material.GRAY_DYE);
        }

        @Override
        public void onEnter(Player player) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 300, 4));
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 300, 0));
        }
    }

    public static class PoisonTrap extends DroppableTrap {

        PoisonTrap(Location location, Team team) {
            super(location, team, Material.GREEN_DYE);
        }

        @Override
        public void onEnter(Player player) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 2));
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 300, 0));
        }
    }

    public static class FreezeTrap extends DroppableTrap {

        FreezeTrap(Location location, Team team) {
            super(location, team, Material.LIGHT_BLUE_DYE);
        }

        @Override
        public void onEnter(Player player) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 255, true, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 300, 0));
        }
    }

    public static class FlyTrap extends DroppableTrap {

        FlyTrap(Location location, Team team) {
            super(location, team, Material.FEATHER);
        }

        @Override
        public void onEnter(Player player) {
            if (player.hasCooldown(Material.FEATHER)) {
                return;
            }

            player.setVelocity(new Vector(0, 3, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 300, 0));
        }
    }

    public static class WebTrap extends DroppableTrap {

        WebTrap(Location location, Team team) {
            super(location, team, Material.WHITE_DYE);
        }

        @Override
        public void onEnter(Player player) {
            // Place 5 blocks of webs around the location
            final var main = location.clone().getBlock();
            main.setType(Material.COBWEB);
            main.getRelative(BlockFace.EAST).setType(Material.COBWEB);
            main.getRelative(BlockFace.WEST).setType(Material.COBWEB);
            main.getRelative(BlockFace.NORTH).setType(Material.COBWEB);
            main.getRelative(BlockFace.SOUTH).setType(Material.COBWEB);
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 300, 0));
        }
    }
}
