package com.liphium.greedyghosts.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.DoubleStream;

public class LabyrinthGenerator {

    private static final int ENTRANCE_END_DISTANCE = 3;
    private static final int ENTRANCE_DISTANCE = 8;

    private static final int WALL_ENTRANCE_DISTANCE = 3;
    private static final int WALL_WALL_DISTANCE = 2;

    /**
     * A section reaching from start to end (should be equal in one coordinate or will fail)
     */
    public record Section(Location start, Location end) {

        public double getDistanceFromEdges(Location location) {
            final var stream = DoubleStream.of(
                    start.distance(location),
                    end.distance(location)
            );

            return stream.min().getAsDouble();
        }

        public BlockFace[] getTwoDirections() {
            final var center = start.clone().add(end.clone().subtract(start).multiply(0.5));
            return new BlockFace[]{
                    directionToBlockFace(start.toVector().subtract(center.toVector())),
                    directionToBlockFace(end.toVector().subtract(center.toVector()))
            };
        }

        public Location getLocationAcross(Location from, Section in, BlockFace towards) {
            for(int i = 0; i < 100; i++) {
                final var checkLoc = from.getBlock().getRelative(towards, i).getLocation();

                // Check if checkLoc is on the line between in.start and in.end
                if (in.isOnSection(checkLoc)) {
                    return checkLoc.toCenterLocation();
                }
            }
            return null;
        }

        public void placeUntil(Location from, Section in, BlockFace towards, Material material, int height) {
            for(int i = 0; i < 100; i++) {
                final var checkLoc = from.getBlock().getRelative(towards, i).getLocation();

                for(int h = 0; h <= height; h++) {
                    checkLoc.clone().add(0, h, 0).getBlock().setType(material);
                }

                if (in.isOnSection(checkLoc)) {
                    return;
                }
            }
        }

        /**
         * Places blocks along a wall section from start to end
         */
        public void place(int height, Material material, List<Location> entrances) {

            // Calculate the direction vector between start and end
            Vector direction = end.toVector().subtract(start.toVector());
            double distance = direction.length();
            direction.normalize();

            // Place blocks along the line from start to end
            for (int i = 0; i <= (int) distance; i++) {
                Location before = start.clone().add(direction.clone().multiply(i - 1)).toCenterLocation();
                Location blockLocation = start.clone().add(direction.clone().multiply(i)).toCenterLocation();
                Location after = start.clone().add(direction.clone().multiply(i + 1)).toCenterLocation();
                if(entrances.contains(blockLocation) || entrances.contains(before) || entrances.contains(after)) {
                    continue;
                }

                // Place wall block
                for(int h = 0; h <= height; h++) {
                    blockLocation.clone().add(0, h, 0).getBlock().setType(material);
                }
            }
        }

        private boolean isOnSection(Location location) {
            int locX = location.getBlockX();
            int locZ = location.getBlockZ();
            int startX = start.getBlockX();
            int startZ = start.getBlockZ();
            int endX = end.getBlockX();
            int endZ = end.getBlockZ();

            // For horizontal lines (same Z coordinate)
            if (startZ == endZ && locZ == startZ) {
                int minX = Math.min(startX, endX);
                int maxX = Math.max(startX, endX);
                return locX >= minX && locX <= maxX;
            }

            // For vertical lines (same X coordinate)
            if (startX == endX && locX == startX) {
                int minZ = Math.min(startZ, endZ);
                int maxZ = Math.max(startZ, endZ);
                return locZ >= minZ && locZ <= maxZ;
            }

            return false;
        }

        public double getDistanceFromEdges(Section section) {
            final var stream = DoubleStream.of(
                    start.distance(section.start),
                    start.distance(section.end),
                    end.distance(section.start),
                    end.distance(section.end)
            );

            return stream.min().getAsDouble();
        }
    }

    /**
     * Generate one side of the labyrinth.
     * @param walls should be sorted from inner to outer, will break otherwise
     */
    public static void generateLabyrinth(List<Section> walls, BlockFace towards) {
        final Random rand = new Random();
        final var height = 5;

        // Generate the entrances
        final List<List<Location>> entrances = new ArrayList<>(); // Section id -> Entrances
        final List<Location> generalEntrances = new ArrayList<>();
        int entrancesToGenerate = 1;
        for(final var wall : walls) {
            final var generatedEntrances = new ArrayList<Location>();

            // Make sure to generate entrances that don't collide with each other and aren't too close to the end of the wall
            int tries = 0;
            while(generatedEntrances.size() < entrancesToGenerate && tries <= 50) {
                final var entranceLocation = generateRandomPosition(wall, rand).toCenterLocation();
                if(wall.getDistanceFromEdges(entranceLocation) <= ENTRANCE_END_DISTANCE) {
                    continue;
                }

                var found = false;
                for(final var entrance : generalEntrances) {
                    if(entrance.distance(entranceLocation) <= ENTRANCE_DISTANCE) {
                        found = true;
                    }
                }
                if(!found) {
                    Bukkit.broadcast(Component.text("Entrance placed at" + entranceLocation));
                    generatedEntrances.add(entranceLocation);
                    generalEntrances.add(entranceLocation);
                }
                tries++;
            }

            if(tries >= 50) {
                Bukkit.broadcast(Component.text("Not all entrances could be placed... Continuing..."));
            }

            entrances.add(generatedEntrances);
            entrancesToGenerate++;
        }

        Bukkit.broadcast(Component.text("Finished entrances."));

        // Generate all the walls
        final List<List<Location>> innerWalls = new ArrayList<>(walls.size()); // Section id -> Wall location
        walls.forEach(w -> innerWalls.add(new ArrayList<>()));

        // Different wall generation
        final List<Integer> wallsToPlace = new ArrayList<>();
        int wallsToGenerate = 1;
        for(int i = 0; i < walls.size() - 1; i++) {
            for(int g = 0; g < wallsToGenerate; g++) {
                wallsToPlace.add(i);
            }
            wallsToGenerate += 1;
        }

        final List<Location> allInnerWalls = new ArrayList<>();
        int tries = 0;
        while(!wallsToPlace.isEmpty() && tries++ <= 1000) {
            final var randIndex =  rand.nextInt(wallsToPlace.size());
            final var wallToPlaceIn = wallsToPlace.get(randIndex);
            final var wall = walls.get(wallToPlaceIn);

            final var innerWallLocation = generateRandomPosition(wall, rand).toCenterLocation();
            final var locAcross = wall.getLocationAcross(innerWallLocation, walls.get(wallToPlaceIn + 1), towards);

            var found = false;
            for(final var entrance : generalEntrances) {
                if(entrance.distance(innerWallLocation) <= WALL_ENTRANCE_DISTANCE || entrance.distance(locAcross) <= WALL_ENTRANCE_DISTANCE) {
                    found = true;
                }
            }
            for(final var innerWall : allInnerWalls) {
                if(innerWall.distance(innerWallLocation) <= WALL_WALL_DISTANCE) {
                    found = true;
                }
            }

            if(!found) {

                // Make sure the labyrinth is still possible with the wall
                innerWalls.get(wallToPlaceIn).add(innerWallLocation);
                if(!isLabyrinthPossible(walls, entrances, innerWalls, towards)) {
                    innerWalls.get(wallToPlaceIn).remove(innerWallLocation);
                    continue;
                }

                allInnerWalls.add(innerWallLocation);
                innerWalls.get(wallToPlaceIn).add(innerWallLocation);
                wallsToPlace.remove(randIndex);

                // Place the wall
                wall.placeUntil(innerWallLocation, walls.get(wallToPlaceIn + 1), towards, Material.STONE_BRICKS, height);
            }
        }

        if(tries >= 50) {
            Bukkit.broadcast(Component.text("Not all walls could be placed... Continuing..."));
        }

        /*
        wallsToGenerate = 2;
        int index = 0;
        for(final var wall : walls) {
            if(index == walls.size() - 1) {
                break;
            }
            Bukkit.broadcast(Component.text("Placing walls for " + index + "..."));

            List<Location> generatedInnerWalls = new ArrayList<>();
            if(innerWalls.size() > index) {
                generatedInnerWalls = innerWalls.get(index);
            }

            tries = 0;
            while(generatedInnerWalls.size() < wallsToGenerate && tries++ <= 50) {
                final var innerWallLocation = generateRandomPosition(wall, rand).toCenterLocation();
                final var locAcross = wall.getLocationAcross(innerWallLocation, walls.get(index + 1), towards);

                var found = false;
                for(final var entrance : generalEntrances) {
                    if(entrance.distance(innerWallLocation) <= WALL_ENTRANCE_DISTANCE || entrance.distance(locAcross) <= WALL_ENTRANCE_DISTANCE) {
                        found = true;
                    }
                }
                for(final var innerWall : generatedInnerWalls) {
                    if(innerWall.distance(innerWallLocation) <= WALL_WALL_DISTANCE) {
                        found = true;
                    }
                }

                if(!found) {

                    // Make sure the labyrinth is still possible with the wall
                    generatedInnerWalls.add(innerWallLocation);
                    innerWalls.set(index, generatedInnerWalls);
                    if(!isLabyrinthPossible(walls, entrances, innerWalls, towards)) {
                        generatedInnerWalls.remove(innerWallLocation);
                        continue;
                    }

                    // Place the wall
                    wall.placeUntil(innerWallLocation, walls.get(index + 1), towards, Material.STONE_BRICKS, height);
                }
            }

            if(tries >= 50) {
                Bukkit.broadcast(Component.text("Not all walls could be placed... Continuing..."));
            }

            innerWalls.set(index, generatedInnerWalls);
            wallsToGenerate += 2;
            index++;
        }
         */

        // Place all the walls with the entrances carved out
        int index = 0;
        for(var wall : walls) {
            wall.place(height, Material.STONE_BRICKS, entrances.get(index));
            index++;
        }
    }

    private static Location generateRandomPosition(Section section, Random random) {
        Location start = section.start();
        Location end = section.end();

        if (start.x() == end.x()) {
            int minZ = Math.min(start.getBlockZ(), end.getBlockZ());
            int maxZ = Math.max(start.getBlockZ(), end.getBlockZ());
            return new Location(start.getWorld(), start.x(), start.y(), minZ + random.nextInt(maxZ - minZ + 1) + 0.5);
        }

        if (start.z() == end.z()) {
            int minX = Math.min(start.getBlockX(), end.getBlockX());
            int maxX = Math.max(start.getBlockX(), end.getBlockX());
            return new Location(start.getWorld(), minX + random.nextInt(maxX - minX + 1) + 0.5, start.y(), start.z());
        }

        throw new IllegalArgumentException("Section must have equal x or z coordinates");
    }

    public static BlockFace directionToBlockFace(Vector direction) {
        // Normalize the direction for better calculation
        direction = direction.normalize();

        double x = direction.getX();
        double z = direction.getZ();

        // Determine the dominant axis
        if (Math.abs(x) > Math.abs(z)) {
            // X-axis is dominant
            return x > 0 ? BlockFace.EAST : BlockFace.WEST;
        } else {
            // Z-axis is dominant
            return z > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
        }
    }

    private static boolean isLabyrinthPossible(List<Section> walls, List<List<Location>> entrances, List<List<Location>> innerWalls, BlockFace towards) {
        // Idea for marching
        // 1. Go across current wall (recursively)
        // 2. Check for walls, in case bumped, return false
        // 3. Check for entrances across, if there, create three new runners, one that continues, one that goes right
        //    on the wall and one that goes left
        // 4. If entrance on last wall is hit, return true

        final var possibleDirections = walls.get(0).getTwoDirections();
        final var firstEntrance = entrances.get(0).get(0);
        assert possibleDirections.length == 2;
        assert firstEntrance != null;
        return marchLabyrinth(walls, entrances, innerWalls, 0, firstEntrance, possibleDirections[0], towards, new ArrayList<>())
                || marchLabyrinth(walls, entrances, innerWalls, 0, firstEntrance, possibleDirections[1], towards, new ArrayList<>());
    }

    private record VisitedEntrance(int id, int index) {}

    private static boolean marchLabyrinth(
            List<Section> walls,
            List<List<Location>> entrances,
            List<List<Location>> innerWalls,
            int currentWall,
            Location currentLocation,
            BlockFace currentDirection,
            BlockFace wallDirection,
            ArrayList<VisitedEntrance> visitedEntrances
    ) {
        final var now = currentLocation.getBlock().getRelative(currentDirection).getLocation().toCenterLocation();
        if(!walls.get(currentWall).isOnSection(now)) {
            return false;
        }

        // Check if we hit a wall
        if(innerWalls.get(currentWall).contains(now)) {
            return false;
        }

        // Check if there is an entrance across we can go to towards the exit
        if(walls.size() > currentWall + 1) {
            final var locAcross = walls.get(currentWall).getLocationAcross(now, walls.get(currentWall + 1), wallDirection);
            assert locAcross != null;

            final var entranceIndex = entrances.get(currentWall + 1).indexOf(locAcross);
            if(entranceIndex != -1 && !visitedEntrances.contains(new VisitedEntrance(currentWall + 1, entranceIndex))) {

                // If it's an entrance in the last wall, return true (we're out of the labyrinth)
                if(walls.size() == currentWall + 2) {
                    return true;
                }

                // Continue marching on all fronts
                visitedEntrances.add(new VisitedEntrance(currentWall + 1, entranceIndex));
                return marchLabyrinth(walls,  entrances, innerWalls, currentWall + 1, locAcross, wallDirection, wallDirection, new ArrayList<>(visitedEntrances))
                        || marchLabyrinth(walls,  entrances, innerWalls, currentWall + 1, locAcross, currentDirection.getOppositeFace(), wallDirection, new ArrayList<>(visitedEntrances))
                        || marchLabyrinth(walls, entrances, innerWalls, currentWall, now, currentDirection, wallDirection, new ArrayList<>(visitedEntrances));
            }
        }

        // Check if there is an entrance across we can go to towards the center
        if(currentWall != 0) {
            final var locAcross = walls.get(currentWall).getLocationAcross(now, walls.get(currentWall - 1), wallDirection);
            assert locAcross != null;

            final var entranceIndex = entrances.get(currentWall - 1).indexOf(locAcross);
            if(entranceIndex != -1 && !visitedEntrances.contains(new VisitedEntrance(currentWall - 1, entranceIndex))) {

                // If it's an entrance in the first wall, return false (we're back in the center)
                if(currentWall - 1 == 0) {
                    return false;
                }

                // Continue marching on all fronts
                visitedEntrances.add(new VisitedEntrance(currentWall - 1, entranceIndex));
                return marchLabyrinth(walls,  entrances, innerWalls, currentWall - 1, locAcross, wallDirection, wallDirection, new ArrayList<>(visitedEntrances))
                        || marchLabyrinth(walls,  entrances, innerWalls, currentWall - 1, locAcross, currentDirection.getOppositeFace(), wallDirection, new ArrayList<>(visitedEntrances))
                        || marchLabyrinth(walls, entrances, innerWalls, currentWall, now, currentDirection, wallDirection, new ArrayList<>(visitedEntrances));
            }
        }

        // Continue marching
        return marchLabyrinth(walls, entrances, innerWalls, currentWall, now, currentDirection, wallDirection, visitedEntrances);
    }
}
