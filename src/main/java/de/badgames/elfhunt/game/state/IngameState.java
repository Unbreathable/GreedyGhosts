package de.badgames.elfhunt.game.state;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XSound;
import de.badgames.cloudhelper.CloudHelper;
import de.badgames.elfhunt.util.LabyrinthGenerator;
import de.badgames.gameCore.GameState;
import de.badgames.gameCore.events.PlayerKillEvent;
import de.badgames.shared.state.EndState;
import de.badgames.gameCore.map.GenericMap;
import de.badgames.gameCore.team.Team;
import de.badgames.shared.util.PlayerUtil;
import de.badgames.pluginCore.util.TimeFormatter;
import de.badgames.pluginCore.util.ConfigUtil;
import de.badgames.pluginCore.util.ItemStackBuilder;
import de.badgames.elfhunt.GreedyGhosts;
import de.badgames.elfhunt.game.team.impl.GhostTeam;
import de.badgames.elfhunt.game.team.impl.FarmerTeam;
import de.badgames.elfhunt.listener.machines.impl.PresentReceiver;
import de.badgames.pluginCore.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
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

    // The minecraft ingame end and start times for the cycle to end in a sunrise
    final int START_TIME = 13000;
    final int END_TIME = 23000;

    final int RESPAWN_DELAY = 10; // In seconds

    // All locations for the map
    final String SPECTATOR_LOCATION = "Spectators";
    final String CENTER_LOCATION = "Center";
    final String FARMER_SPAWN = "Farmers";

    final long MAX_GAME_TIME;
    long currentGameTime = 0;

    int maxSnacks = 0;
    private int snacksLeft = 0;

    /**
     * Map with the format NPC Name - NPC Instance
     */
    private final HashMap<String, PresentReceiver> receivers = new HashMap<>();
    private final HashMap<Location, Boolean> placedBlocks = new HashMap<>();

    // Map with Player -> Respawn timer
    private final HashMap<Player, Integer> currentRespawnTimer = new HashMap<>();

    public IngameState() {
        super("In game", 30);
        MAX_GAME_TIME = Duration.ofMinutes(NumberConversions.toInt(ConfigUtil.get("game.time"))).getSeconds() * 20;
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
        final var hunterSize = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam("Ghosts").getPlayers().size();
        maxSnacks = hunterSize * 4; // 4 per member of the team seems fine for 15 minutes
        snacksLeft = maxSnacks;

        final GenericMap map = GreedyGhosts.getInstance().getGameManager().getMap();
        World world = Bukkit.getWorld(map.getWorldName());

        assert world != null;
        world.setDifficulty(Difficulty.NORMAL);
        world.setThundering(false);
        world.setStorm(false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.NATURAL_REGENERATION, false);

        // Generate the labyrinth
        generateLabyrinth(GreedyGhosts.getInstance().getGameManager().getMapLocation(CENTER_LOCATION), GreedyGhosts.getInstance().getGameManager().getMap().getCornerA());

        for (Player all : Bukkit.getOnlinePlayers()) {
            PlayerUtil.clearPlayerMetaData(all);
        }

        for (Team team : GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeams()) {
            team.sendStartMessage();

            for (Player player : team.getPlayers()) {
                player.getInventory().clear();
                player.setHealth(20);
                teleportToProperLocation(player);
            }

            // Apply invisibility to the ghosts
            if(team instanceof GhostTeam) {
                for(Player player : team.getPlayers()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 99999, 1, false, false));
                }
            }
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
                        handleWin(GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam("Farmers"));
                        return;
                    }

                    // Decrement all respawn timers and teleport players
                    handleRespawnTimers();

                    // Adjust the ingame time for the night cycle
                    final double progress = (double) currentGameTime / MAX_GAME_TIME;
                    final int ingameTime = START_TIME + (int) ((END_TIME - START_TIME) * progress);
                    world.setTime(ingameTime);

                    // Send action bar to remind of missing snacks
                    Messages.actionBar(Component.text(snacksLeft, NamedTextColor.GOLD)
                            .append(Component.text("/", NamedTextColor.GRAY))
                            .append(Component.text(maxSnacks, NamedTextColor.GOLD))
                            .appendSpace()
                            .append(Component.text("remaining", NamedTextColor.YELLOW))
                            .appendSpace()
                            .append(Component.text("|", NamedTextColor.DARK_GRAY))
                            .appendSpace()
                            .append(Component.text(TimeFormatter.formatTicks(currentGameTime), NamedTextColor.GOLD).appendSpace()
                                    .append(Component.text("left", NamedTextColor.YELLOW)))
                    );
                }

                currentGameTime--;
            }
        });
    }

    /**
     * This method decrements all respawn timers and makes sure players are teleported back to their respective locations
     * after the timer is up.
     */
    private void handleRespawnTimers() {
        for(var entry : currentRespawnTimer.entrySet()) {
            final var player = entry.getKey();

            // Respawn in case it would hit zero
            if(entry.getValue() - 1 <= 0) {
                player.clearTitle();
                player.setGameMode(GameMode.SURVIVAL);
                currentRespawnTimer.remove(player);

                teleportToProperLocation(player);
                return;
            }

            // Show them the timer
            player.showTitle(Title.title(
                    // Title
                    Component.text("Respawning in", NamedTextColor.YELLOW),

                    // Subtitle
                    Component.text(entry.getValue() - 1, NamedTextColor.GOLD)
                            .append(Component.text("...", NamedTextColor.GRAY)),

                    // Make sure the title appears instantly
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(3), Duration.ZERO)
            ));

            // Decrement the timer
            currentRespawnTimer.put(player, entry.getValue() - 1);
        }
    }

    /**
     * Teleport a player to their proper location after respawn or at game start.
     * @param player The player
     */
    private void teleportToProperLocation(Player player) {
        final var team = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(player);
        if(team instanceof GhostTeam) {
            // TODO: Clockwise teleports
            player.teleport(Objects.requireNonNull(GreedyGhosts.getInstance().getGameManager().getMapLocation(CENTER_LOCATION)));
        } else {
            player.teleport(Objects.requireNonNull(GreedyGhosts.getInstance().getGameManager().getMapLocation(FARMER_SPAWN)));
        }
    }

    private void generateLabyrinth(Location center, Location cornerA) {
        final var diffX = Math.abs(cornerA.getX() - center.getX());

        for (var face : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST}) {
            final var walls = generateSectionsForSide(center, face, (int) diffX);
            LabyrinthGenerator.generateLabyrinth(walls, face);
        }
    }

    /**
     * Generate the sides for a wall pointing in a specific direction from a base location.
     * @param towards
     * @return
     */
    private List<LabyrinthGenerator.Section> generateSectionsForSide(Location base, BlockFace towards, int radius) {
        final var wallAmount = 6;
        var wallBase = base.getBlock().getRelative(towards, radius).getLocation().toCenterLocation();
        final var directions = getTwoDirections(towards);
        assert directions != null && directions.length == 2;

        List<LabyrinthGenerator.Section> walls = new ArrayList<>();
        for(int i = 0; i < wallAmount; i++) {
            final var start = wallBase.getBlock().getRelative(directions[0], radius + i * 4).getLocation().toCenterLocation();
            final var end = wallBase.getBlock().getRelative(directions[1], radius + i * 4).getLocation().toCenterLocation();

            walls.add(new LabyrinthGenerator.Section(start, end));
            wallBase = wallBase.getBlock().getRelative(towards, 4).getLocation().toCenterLocation();
        }

        return walls;
    }

    /**
     * Get the directions you need to go towards from a face to construct a wall.
     * Say you're currently generating a wall for a labyrinth pointing north, then you'd want the blocks to be placed east and
     * west of the current block.
     *
     * @param towards The direction of the labyrinth
     * @return The two directions the walls point to
     */
    private BlockFace[] getTwoDirections(BlockFace towards) {
        return switch (towards) {
            case SOUTH, NORTH -> new BlockFace[]{BlockFace.WEST, BlockFace.EAST};
            case EAST, WEST -> new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH};
            default -> null;
        };
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

    @Override
    public void onMove(PlayerMoveEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        Team team = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(event.getPlayer());

        // Check if they wandered into a trap
        ArrayList<DroppableTrap> toRemove = new ArrayList<>();
        for (DroppableTrap trap : traps) {
            if (trap.location.distance(event.getPlayer().getLocation()) <= 3 && !team.getName().equals(trap.team.getName())) {
                toRemove.add(trap);
                trap.onEnter(event.getPlayer());
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

        if (player.getKiller() != null) {
            Team killerTeam = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(player.getKiller());
            Bukkit.broadcast(GreedyGhosts.PREFIX.append(Component.text(team.getChatColor() + player.getName() + " §7was killed by " + killerTeam.getChatColor() + player.getKiller().getName() + "§7!")));

            Bukkit.getPluginManager().callEvent(new PlayerKillEvent<>(player, player.getKiller(), team, killerTeam, true));
        } else {
            Bukkit.broadcast(GreedyGhosts.PREFIX.append(Component.text(team.getChatColor() + player.getName() + " §7died!")));
        }

        event.getDrops().removeIf(x -> x.getType() == Material.LEATHER_HORSE_ARMOR);

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
        event.setRespawnLocation(Objects.requireNonNull(GreedyGhosts.getInstance().getGameManager().getMapLocation(SPECTATOR_LOCATION)));
        event.getPlayer().setGameMode(GameMode.SPECTATOR);

        currentRespawnTimer.put(event.getPlayer(), RESPAWN_DELAY);
    }

    public void handleWin(Team team) {
        team.handleWin();

        GreedyGhosts.getInstance().getTaskManager().uninject(runnable);
        GreedyGhosts.getInstance().getGameManager().setCurrentState(new EndState(GreedyGhosts.getInstance(), GreedyGhosts.getInstance().getGameManager(), GreedyGhosts.getInstance().getTaskManager(),
                Component.text("Greedy Ghosts", NamedTextColor.GOLD, TextDecoration.BOLD), GreedyGhosts.PREFIX,
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
        player.setGameMode(GameMode.SPECTATOR);

        for (Player all : Bukkit.getOnlinePlayers()) {
            all.hidePlayer(GreedyGhosts.getInstance(), player);
        }

        (new BukkitRunnable() {
            @Override
            public void run() {
                player.teleportAsync(GreedyGhosts.getInstance().getGameManager().getMapLocation("Center"));
            }
        }).runTaskLater(GreedyGhosts.getInstance(), 10);
    }

    @Override
    public void quit(Player player) {
        Team team = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(player);
        if (team == null) return;
        team.removePlayer(player);

        // Make sure the team loses if there are no players left
        if (team.getPlayers().isEmpty()) {
            if (team instanceof FarmerTeam) {
                handleWin(GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam("Farmers"));
            } else {
                handleWin(GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam("Ghosts"));
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
