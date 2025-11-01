package com.liphium.greedyghosts.game.state;

import com.cryptomorin.xseries.XMaterial;
import com.liphium.greedyghosts.game.HotbarKit;
import com.liphium.greedyghosts.screens.ItemShopScreen;
import com.liphium.greedyghosts.screens.KitSelectionScreen;
import de.badgames.cloudhelper.CloudHelper;
import com.liphium.greedyghosts.util.LabyrinthGenerator;
import de.badgames.gameCore.GameState;
import de.badgames.gameCore.events.PlayerKillEvent;
import de.badgames.pluginCore.PluginCore;
import de.badgames.gameCore.map.GenericMap;
import de.badgames.gameCore.team.Team;
import de.badgames.shared.util.PlayerUtil;
import de.badgames.pluginCore.util.ConfigUtil;
import de.badgames.pluginCore.util.ItemStackBuilder;
import com.liphium.greedyghosts.GreedyGhosts;
import com.liphium.greedyghosts.game.team.impl.GhostTeam;
import com.liphium.greedyghosts.game.team.impl.FarmerTeam;
import de.badgames.pluginCore.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.NumberConversions;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.*;

public class IngameState extends GameState {
    private final ArrayList<DroppableTrap> traps = new ArrayList<>();
    private final BlockFace[] wallDirections = new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private Runnable runnable;

    // The minecraft ingame end and start times for the cycle to end in a sunrise
    final int START_TIME = 13000;
    final int END_TIME = 23000;

    final int RESPAWN_DELAY = 10; // In seconds
    final int SNACK_RESPAWN_DELAY = 120; // In seconds
    final int PUMPKIN_RESPAWN_DELAY = 15; // In seconds

    final double TRAP_RANGE = 4; // In blocks (roughly)

    // All locations for the map
    final String SPECTATOR_LOCATION = "Spectators";
    final String CENTER_LOCATION = "Center";
    final String FARMER_SPAWN = "Farmers";

    final long MAX_GAME_TIME;
    long currentGameTime = 0;

    int maxSnacks = 0;
    private int snacksLeft = 0;

    public record HighwayWall(LabyrinthGenerator.Section wallBefore, LabyrinthGenerator.Section wall,
                              BlockFace direction) {
    }

    // Outside walls of the highway wall
    private final ArrayList<HighwayWall> highwayWalls = new ArrayList<>();

    // Spawn locations for all walls (in the directions)
    private final HashMap<BlockFace, Location> spawnLocations = new HashMap<>();

    // Current spawn direction of the ghosts (this is an index for wallDirections)
    private int currentSpawnDirection = -1;

    // Ticks since a player received velocity from the kit protection
    private final HashMap<Player, Integer> boostProtection = new HashMap<>();

    // If a kit has already been given to a ghost
    private final HashMap<Player, Boolean> kitGiven = new HashMap<>();

    /**
     * Map with the format NPC Name - NPC Instance
     */
    private final HashMap<Location, Boolean> placedBlocks = new HashMap<>();

    // Map with Player -> Respawn timer
    private final HashMap<Player, Integer> currentRespawnTimer = new HashMap<>();

    // Selected kit by player (only ghost team)
    private final HashMap<Player, HotbarKit> selectedKits = new HashMap<>();

    // All currently mined snack blocks
    private final ArrayList<SnackBlock> snackBlocks = new ArrayList<>();

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
        final var hunterSize = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(GhostTeam.TEAM_NAME).getPlayers().size();
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
            all.clearActivePotionEffects();
        }

        for (Team team : GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeams()) {
            team.sendStartMessage();

            for (Player player : team.getPlayers()) {
                player.getInventory().clear();
                player.setHealth(20);
                teleportToProperLocation(player);
                team.giveKit(player, false);
                giveProperInventory(player, false);
            }

            // Apply invisibility to the ghosts
            if (team instanceof GhostTeam) {
                for (Player player : team.getPlayers()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 99999, 1, false, false));
                }
            } else {
                for (Player player : team.getPlayers()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, 1, false, false));
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
                        handleWin(GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(FarmerTeam.TEAM_NAME));
                        return;
                    }

                    // Decrement all respawn timers and teleport players
                    handleRespawnTimers();

                    // Decrement all snack block counters
                    handleSnackBlocks();

                    // Adjust the ingame time for the night cycle
                    final double progress = (double) currentGameTime / MAX_GAME_TIME;
                    final int ingameTime = START_TIME + (int) ((END_TIME - START_TIME) * progress);
                    world.setFullTime(ingameTime);

                    // Send action bar to remind of missing snacks
                    Messages.actionBar(Component.text(snacksLeft, NamedTextColor.GOLD)
                            .append(Component.text("/", NamedTextColor.GRAY))
                            .append(Component.text(maxSnacks, NamedTextColor.GOLD))
                            .appendSpace()
                            .append(Component.text("remaining", NamedTextColor.YELLOW))
                            .appendSpace()
                            .append(Component.text("|", NamedTextColor.DARK_GRAY))
                            .appendSpace()
                            .append(Component.text(formatTicks(currentGameTime), NamedTextColor.GOLD).appendSpace()
                                    .append(Component.text("left", NamedTextColor.YELLOW)))
                    );
                }

                currentGameTime--;
            }
        });
    }

    private String formatTicks(long ticks) {
        long seconds = ticks / 20L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        return String.format("§6%02d§7:§6%02d", minutes, seconds);
    }

    // Called by the kit selection screen
    public void setKit(Player player, HotbarKit kit) {
        selectedKits.put(player, kit);
    }

    /**
     * This method decrements all respawn timers and makes sure players are teleported back to their respective locations
     * after the timer is up.
     */
    private void handleRespawnTimers() {
        for (var entry : currentRespawnTimer.entrySet()) {
            final var player = entry.getKey();

            // Respawn in case it would hit zero
            if (entry.getValue() - 1 <= 0) {
                player.clearTitle();
                player.setGameMode(GameMode.SURVIVAL);
                currentRespawnTimer.remove(player);

                // Make sure to give invisibility again when ghost
                final var team = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(player);
                if (team instanceof GhostTeam) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 99999, 1, false, false));
                } else {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, 1, false, false));
                }

                teleportToProperLocation(player);
                giveProperInventory(player, true);
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
     * This method decrements all snack counters and restores the original block once the counter is over.
     */
    private void handleSnackBlocks() {
        snackBlocks.removeIf(block -> {
            // Restore block and remove from list in case the counter would hit zero
            if (block.secondsRemaining - 1 <= 0) {
                block.location.getBlock().setType(block.original);
                return true;
            }

            // Decrement the timer
            block.secondsRemaining--;
            return false;
        });
    }


    /**
     * Teleport a player to their proper location after respawn or at game start.
     *
     * @param player The player
     */
    private void teleportToProperLocation(Player player) {
        final var team = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(player);
        if (team instanceof GhostTeam) {
            currentSpawnDirection++;
            if (currentSpawnDirection >= wallDirections.length) {
                currentSpawnDirection = 0;
            }

            player.teleport(spawnLocations.get(wallDirections[currentSpawnDirection]));
        } else {
            player.teleport(Objects.requireNonNull(GreedyGhosts.getInstance().getGameManager().getMapLocation(FARMER_SPAWN)));
        }
    }

    /**
     * Give a player the proper inventory for the team they're in.
     *
     * @param player the player
     * @param death  whether the player died or not
     */
    private void giveProperInventory(Player player, boolean death) {
        final var team = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(player);

        if (team instanceof GhostTeam) {
            giveGhostInventory(player);
        } else {
            giveFarmerInventory(player, death);
        }
    }

    private void generateLabyrinth(Location center, Location cornerA) {
        final var diffX = Math.abs(cornerA.getX() - center.getX());

        for (var face : wallDirections) {
            final var walls = generateSectionsForSide(center, face, (int) diffX);

            // Add the last section to the highway walls
            final var highwaySection = walls.removeLast();
            highwayWalls.add(new HighwayWall(walls.getLast(), highwaySection, face));

            LabyrinthGenerator.generateLabyrinth(walls, face);
        }

        // Place the highway walls and set the spawns
        for (final var wall : highwayWalls) {
            final var center1 = getCenter(wall.wall.start(), wall.wallBefore.start());
            final var center2 = getCenter(wall.wall.end(), wall.wallBefore.end());
            final var wallSpawn = getCenter(center1, center2);

            wall.wall.place(5, Material.BEDROCK, new ArrayList<>());
            spawnLocations.put(wall.direction, wallSpawn);
        }
    }

    /**
     * Generate the sides for a wall pointing in a specific direction from a base location.
     *
     * @param towards
     * @return
     */
    private List<LabyrinthGenerator.Section> generateSectionsForSide(Location base, BlockFace towards, int radius) {
        final var wallAmount = 7;
        var wallBase = base.getBlock().getRelative(towards, radius).getLocation().toCenterLocation();
        final var directions = getTwoDirections(towards);
        assert directions != null && directions.length == 2;

        List<LabyrinthGenerator.Section> walls = new ArrayList<>();
        for (int i = 0; i < wallAmount; i++) {
            final var start = wallBase.getBlock().getRelative(directions[0], radius + i * 4).getLocation().toCenterLocation();
            final var end = wallBase.getBlock().getRelative(directions[1], radius + i * 4).getLocation().toCenterLocation();

            walls.add(new LabyrinthGenerator.Section(start, end));
            wallBase = wallBase.getBlock().getRelative(towards, 4).getLocation().toCenterLocation();
        }

        return walls;
    }

    /**
     * Get the center location between two locations.
     *
     * @param loc1 The first location
     * @param loc2 The second location
     * @return The center location between the two
     */
    private Location getCenter(Location loc1, Location loc2) {
        return new Location(
                loc1.getWorld(),
                (loc1.getX() + loc2.getX()) / 2,
                (loc1.getY() + loc2.getY()) / 2,
                (loc1.getZ() + loc2.getZ()) / 2
        );
    }

    /**
     * Check if a player is inside the center walls (in the labyrinth, not on the highway ring).
     *
     * @param player The player to check
     * @return true if the player is inside the center walls, false otherwise
     */
    private boolean isInsideCenterWalls(Player player) {
        return isInRegion(player.getLocation(), highwayWalls.get(0).wallBefore().start(), highwayWalls.get(2).wallBefore().end());
    }

    private boolean isInRegion(Location source, Location bound1, Location bound2) {
        return source.getX() >= Math.min(bound1.getX(), bound2.getX()) &&
                source.getZ() >= Math.min(bound1.getZ(), bound2.getZ()) &&
                source.getX() <= Math.max(bound1.getX(), bound2.getX()) &&
                source.getZ() <= Math.max(bound1.getZ(), bound2.getZ());
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

    private void giveGhostInventory(Player player) {
        player.getInventory().setBoots(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setHelmet(null);
        player.getInventory().clear();

        player.getInventory().setItem(0, new ItemStackBuilder(XMaterial.CHEST)
                .withName(Component.text("Kit selection", NamedTextColor.GOLD))
                .buildStack());
    }

    private void giveFarmerInventory(Player player, boolean death) {
        player.getInventory().setBoots(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setHelmet(null);
        player.getInventory().clear();

        player.getInventory().setItem(0, new ItemStackBuilder(XMaterial.STONE_SWORD).makeUnbreakable().buildStack());
        player.getInventory().setItem(1, new ItemStackBuilder(XMaterial.WOODEN_AXE).makeUnbreakable().buildStack());

        if (!death) {
            player.getInventory().setItem(2, new ItemStackBuilder(XMaterial.DIRT).withAmount(4).buildStack());
            player.getInventory().setItem(3, new ItemStackBuilder(XMaterial.COBBLESTONE).withAmount(2).buildStack());
            player.getInventory().setItem(4, new ItemStackBuilder(XMaterial.OAK_PLANKS).withAmount(2).buildStack());
        }

        player.getInventory().setItem(8, new ItemStackBuilder(XMaterial.CHEST)
                .withName(Component.text("Shop", NamedTextColor.GOLD))
                .buildStack());
    }

    @Override
    public void onInteract(PlayerInteractEvent event) {
        if (event.getItem() != null && event.getItem().getType() == Material.WIND_CHARGE) {
            return;
        }

        GreedyGhosts.getInstance().getMachineManager().onInteract(event);

        if (event.getItem() != null) {
            final var usedItem = event.getItem();
            final var team = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(event.getPlayer());

            if (team instanceof GhostTeam) {

                // For the ghosts the chest is the kit selection
                if (usedItem.getType().equals(Material.CHEST)) {
                    PluginCore.getInstance().getScreens().open(event.getPlayer(), KitSelectionScreen.SCREEN_ID);
                    event.setCancelled(true);
                    return;
                }

            } else {

                // Check if it's the item shop
                if (usedItem.getType().equals(Material.CHEST)) {
                    PluginCore.getInstance().getScreens().open(event.getPlayer(), ItemShopScreen.SCREEN_ID);
                    event.setCancelled(true);
                    return;
                }

                // Spawn traps in case it's that kind of item
                if (usedItem.getType().equals(Material.GREEN_DYE) && event.getClickedBlock() != null) {
                    traps.add(new PoisonTrap(event.getClickedBlock().getLocation().clone().add(0.5, 1, 0.5), team));
                    reduceMainHandItem(event.getPlayer(), Material.GREEN_DYE);
                } else if (usedItem.getType().equals(Material.WHITE_DYE) && event.getClickedBlock() != null) {
                    traps.add(new WebTrap(event.getClickedBlock().getLocation().clone().add(0.5, 1, 0.5), team));
                    reduceMainHandItem(event.getPlayer(), Material.WHITE_DYE);
                } else if (usedItem.getType().equals(Material.LEATHER) && event.getClickedBlock() != null) {
                    traps.add(new ArmorTrap(event.getClickedBlock().getLocation().clone().add(0.5, 1, 0.5), team));
                    reduceMainHandItem(event.getPlayer(), Material.LEATHER);
                } else if (usedItem.getType().equals(Material.GLOWSTONE_DUST) && event.getClickedBlock() != null) {
                    traps.add(new GlowTrap(event.getClickedBlock().getLocation().clone().add(0.5, 1, 0.5), team));
                    reduceMainHandItem(event.getPlayer(), Material.GLOWSTONE_DUST);
                }
            }
        }

        // Make sure fields can't be destroyed
        if (event.getAction().equals(Action.PHYSICAL)) {
            event.setCancelled(true);
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
        //GreedyGhosts.getInstance().getMachineManager().onInteractAtEntity(event);

        if (event.getRightClicked().getType().equals(EntityType.ARMOR_STAND)) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onMove(PlayerMoveEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        if (boostProtection.containsKey(event.getPlayer())) {
            boostProtection.put(event.getPlayer(), boostProtection.get(event.getPlayer()) - 1);
            if (boostProtection.get(event.getPlayer()) <= 0) {
                boostProtection.remove(event.getPlayer());
            }
        }

        Team team = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(event.getPlayer());
        var inCenter = isInsideCenterWalls(event.getPlayer());

        // Movement handling for the ghosts
        if (team instanceof GhostTeam) {

            // Give them speed if they're inside the highway walls (but not in the center labyrinth)
            if (!inCenter) {
                kitGiven.remove(event.getPlayer());

                if (!event.getPlayer().hasPotionEffect(PotionEffectType.SPEED)) {
                    event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 10, false, false));

                    // If the ghost's inventory contains a snack decrement the counter
                    if (hasSnack(event.getPlayer())) {
                        Bukkit.broadcast(GreedyGhosts.PREFIX.append(Component.text(event.getPlayer().getName(), NamedTextColor.GOLD, TextDecoration.BOLD)).appendSpace()
                                .append(Component.text("stole a snack and returned successfully!", NamedTextColor.GRAY)));
                        snacksLeft--;
                        if (snacksLeft <= 0) {
                            handleWin(team);
                        }
                    }

                    // Give them the chest and regenerate them
                    giveGhostInventory(event.getPlayer());
                    event.getPlayer().setHealth(20);
                }
            } else {
                event.getPlayer().removePotionEffect(PotionEffectType.SPEED);

                // Boost away in case they don't have a kit selected
                if (!selectedKits.containsKey(event.getPlayer())) {
                    if (!boostProtection.containsKey(event.getPlayer())) {
                        event.getPlayer().sendMessage(GreedyGhosts.PREFIX.append(Component.text("Please select a kit!", NamedTextColor.RED)));
                        boostProtection.put(event.getPlayer(), 20);
                    }
                } else {
                    if (!kitGiven.containsKey(event.getPlayer())) {
                        selectedKits.get(event.getPlayer()).giveKit(event.getPlayer());
                        kitGiven.put(event.getPlayer(), true);
                    }
                }
            }
        }

        // Check if they wandered into a trap
        ArrayList<DroppableTrap> toRemove = new ArrayList<>();
        for (DroppableTrap trap : traps) {
            if (trap.location.distance(event.getPlayer().getLocation()) <= TRAP_RANGE && !team.getName().equals(trap.team.getName())) {

                // Make sure the trap is actually visible
                final var toRaytrace = Arrays.asList(
                        event.getPlayer().getLocation(), // Feet
                        event.getPlayer().getLocation().clone().add(0, 1, 0), // Middle
                        event.getPlayer().getLocation().clone().add(0, 2, 0) // Eyes
                );

                var found = false;
                for(final var toTrace : toRaytrace) {
                    final var direction = toTrace.clone().subtract(trap.location).toVector().normalize();
                    final var distance = trap.location.distance(toTrace);
                    final var result = trap.location.getWorld().rayTraceBlocks(trap.location, direction, distance, FluidCollisionMode.NEVER, true);

                    if (result == null) {
                        found = true;
                        break;
                    }
                }

                if(found) {
                    toRemove.add(trap);
                    trap.onEnter(event.getPlayer());
                }
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

        // Make sure ghosts don't do any attack damage against farmers or each other
        if ((event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) &&
                event.getDamager() instanceof Player damager && event.getEntity() instanceof Player player) {
            final var team = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(damager);
            if (team instanceof GhostTeam) {
                event.setDamage(0);
            } else {
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 1, false, false));
            }
        }

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
        if (event.getBlockPlaced().getLocation().getY() >= 250) {
            event.setCancelled(true);
            return;
        }

        // Instantly light placed tnt
        if (event.getBlockPlaced().getType().equals(Material.TNT)) {
            event.getBlockPlaced().setType(Material.AIR);
            final var world = event.getBlockPlaced().getWorld();
            world.spawnEntity(event.getBlockPlaced().getLocation().toCenterLocation(), EntityType.TNT);
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

        // Make sure ghosts can't place blocks
        final var team = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(event.getPlayer());
        if (team instanceof GhostTeam) {
            event.setCancelled(true);
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

    final List<Material> snackTypes = Arrays.asList(
            Material.RED_STAINED_GLASS
    );

    /**
     * Check if a player's inventory contains a snack.
     *
     * @param player The player
     * @return If the inventory contains a snack or not
     */
    private boolean hasSnack(Player player) {
        for (Material wool : snackTypes) {
            if (player.getInventory().contains(wool)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onBreak(BlockBreakEvent event) {
        if (GreedyGhosts.getInstance().getMachineManager().breakLocation(event.getBlock().getLocation())) {
            event.setDropItems(false);
            return;
        }

        final var team = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(event.getPlayer());

        // Let farms break pumpkins for getting carved pumpkins
        if (team instanceof FarmerTeam) {
            if (event.getBlock().getType() == Material.PUMPKIN || event.getBlock().getType() == Material.CARVED_PUMPKIN) {
                event.getPlayer().getInventory().addItem(new ItemStackBuilder(Material.CARVED_PUMPKIN).buildStack());
                snackBlocks.add(new SnackBlock(PUMPKIN_RESPAWN_DELAY, event.getBlock().getLocation(), event.getBlock().getType()));
                event.getBlock().setType(Material.BEDROCK);
            }
        }

        // Handle ghosts breaking wool blocks (snacks)
        if (team instanceof GhostTeam && snackTypes.contains(event.getBlock().getType())) {
            event.setCancelled(true);
            event.setDropItems(false);

            // Make sure the ghost doesn't already have a snack
            if (hasSnack(event.getPlayer())) {
                event.getPlayer().sendMessage(Component.text("You already have a snack!", NamedTextColor.RED));
                return;
            }

            // Give the ghost a snack
            event.getPlayer().getInventory().addItem(new ItemStackBuilder(event.getBlock().getType())
                    .withName(Component.text("Snack", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .buildStack());
            event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 1, false, false));

            // Make sure the block is reverted from bedrock after being replaced
            snackBlocks.add(new SnackBlock(SNACK_RESPAWN_DELAY, event.getBlock().getLocation(), event.getBlock().getType()));
            event.getBlock().setType(Material.BEDROCK);
            return;
        }

        // Only let placed blocks be broken again
        if (placedBlocks.get(event.getBlock().getLocation()) != null) {
            placedBlocks.remove(event.getBlock().getLocation());
            event.setDropItems(false);
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

        event.getDrops().clear();
        player.getInventory().clear();
        player.getInventory().setBoots(null);
        player.getInventory().setLeggings(null);
        player.getInventory().setChestplate(null);
        player.getInventory().setHelmet(null);
        event.deathMessage(null);
        event.setKeepLevel(true);
        event.setShouldDropExperience(false);
        event.setKeepInventory(true);

        Team team = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(player);

        if (player.getKiller() != null) {
            Team killerTeam = GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(player.getKiller());
            Bukkit.broadcast(GreedyGhosts.PREFIX.append(Component.text(team.getChatColor() + player.getName() + " §7was killed by " + killerTeam.getChatColor() + player.getKiller().getName() + "§7!")));

            Bukkit.getPluginManager().callEvent(new PlayerKillEvent<>(player, player.getKiller(), team, killerTeam, true));
        } else {
            Bukkit.broadcast(GreedyGhosts.PREFIX.append(Component.text(team.getChatColor() + player.getName() + " §7died!")));
        }

        // Make sure to reward the farmers when a ghost gets killed
        if (team instanceof GhostTeam) {
            for (var farmer : GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(FarmerTeam.TEAM_NAME).getPlayers()) {
                if (farmer.getGameMode().equals(GameMode.SURVIVAL)) {
                    farmer.getInventory().addItem(new ItemStackBuilder(XMaterial.CARVED_PUMPKIN).withAmount(5).buildStack());
                }
            }
        }

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
                || event.getEntityType().equals(EntityType.POTION)
                || event.getEntityType().equals(EntityType.ZOMBIE)
                || event.getEntityType().equals(EntityType.SKELETON)
                || event.getEntityType().equals(EntityType.CREEPER)
                || event.getEntityType().equals(EntityType.ARROW)) {
            return;
        }
        event.setCancelled(true);
    }

    @Override
    public void onFood(FoodLevelChangeEvent event) {
        event.setFoodLevel(20);
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
                handleWin(GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(FarmerTeam.TEAM_NAME));
            } else {
                handleWin(GreedyGhosts.getInstance().getGameManager().getTeamManager().getTeam(GhostTeam.TEAM_NAME));
            }
        }
    }

    public static class SnackBlock {
        private final Location location;
        private final Material original;
        public int secondsRemaining;

        public SnackBlock(int secondsRemaining, Location location, Material original) {
            this.secondsRemaining = secondsRemaining;
            this.location = location;
            this.original = original;
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

    public static class GlowTrap extends DroppableTrap {

        GlowTrap(Location location, Team team) {
            super(location, team, Material.GLOWSTONE_DUST);
        }

        @Override
        public void onEnter(Player player) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 4));
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0));
        }
    }

    public static class ArmorTrap extends DroppableTrap {

        ArmorTrap(Location location, Team team) {
            super(location, team, Material.LEATHER);
        }

        @Override
        public void onEnter(Player player) {
            player.getInventory().setHelmet(new ItemStackBuilder(XMaterial.LEATHER_HELMET).makeUnbreakable().buildStack());
            player.getInventory().setChestplate(new ItemStackBuilder(XMaterial.LEATHER_CHESTPLATE).makeUnbreakable().buildStack());
            player.getInventory().setLeggings(new ItemStackBuilder(XMaterial.LEATHER_LEGGINGS).makeUnbreakable().buildStack());
            player.getInventory().setBoots(new ItemStackBuilder(XMaterial.LEATHER_BOOTS).makeUnbreakable().buildStack());
        }
    }

    public static class PoisonTrap extends DroppableTrap {

        PoisonTrap(Location location, Team team) {
            super(location, team, Material.GREEN_DYE);
        }

        @Override
        public void onEnter(Player player) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 2));
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
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
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
        }
    }
}
