package com.ryandw11.structure.utils;

import com.ryandw11.structure.CustomStructures;
import com.ryandw11.structure.api.structaddon.StructureSection;
import com.ryandw11.structure.exceptions.StructureConfigurationException;
import com.ryandw11.structure.ignoreblocks.IgnoreBlocks;
import com.ryandw11.structure.schematic.SchematicHandler;
import com.ryandw11.structure.structure.PriorityStructureQueue;
import com.ryandw11.structure.structure.Structure;
import com.ryandw11.structure.structure.StructureHandler;
import com.ryandw11.structure.structure.properties.BlockLevelLimit;
import com.ryandw11.structure.structure.properties.StructureYSpawning;
import com.sk89q.worldedit.WorldEditException;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.io.IOException;
import java.util.Objects;
import java.util.Random;

/**
 * This class prevents the server from crashing when it attempts to pick a
 * structure.
 * <p>
 * The server will still lag a bit thanks to the nature of 1.14.
 * </p>
 *
 * @author Ryandw11
 */
public class StructurePicker {

    public static final Random RANDOM = new Random(System.currentTimeMillis());

    private final CustomStructures plugin;

    private PriorityStructureQueue priorityStructureQueue;
    private final IgnoreBlocks ignoreBlocks;

    private final int radius;
    private final Location center;
    private Block bl;
    private Chunk ch;
    // Variable that contains the structureBlock of the current structure being processed.
    private Block structureBlock, cachedStructureBlock;
    private int attempts;

    public StructurePicker(int radius, Location center, CustomStructures plugin) {
        this.plugin = plugin;

        this.radius = radius / 2;
        this.center = center;
        this.ignoreBlocks = plugin.getBlockIgnoreManager();

        this.attempts = 0;

        this.updateLocation();
    }

    private void updateLocation() {
        Location location = this.getRandomLocation(center);

        Chunk chunk = location.getChunk();
        this.bl = chunk.getBlock(8, 5, 8);
        this.ch = chunk;

        StructureHandler structureHandler = plugin.getStructureHandler();
        if (structureHandler == null) {
            plugin.getLogger().warning("A structure is trying to spawn without the plugin initialization step being completed.");
            plugin.getLogger().warning("If you are using a fork of Spigot, this likely means that the fork does not adhere to the API standard properly.");
            throw new RuntimeException("Plugin Not Initialized.");
        }

        priorityStructureQueue = new PriorityStructureQueue(structureHandler.getStructures(), Objects.requireNonNull(bl), ch);

        this.attempts++;

        this.run();
    }

    public void run() {
        Structure gStructure = null;
        try {
            if (!priorityStructureQueue.hasNextStructure()) {
                if (this.attempts > 10) {
                    plugin.getLogger().info("More than 10 attempts. Stopping spawning for this structure.");
                    return;
                }

//                plugin.getLogger().info("Location failed, checking next.");

                cachedStructureBlock = null;
                this.updateLocation();
                return;
            }

            gStructure = priorityStructureQueue.getNextStructure();

            Structure structure = gStructure;
            assert structure != null;
            StructureYSpawning structureSpawnSettings = structure.getStructureLocation().getSpawnSettings();

//            plugin.getLogger().info("Starting checks for " + structure.getName());
            structureBlock = structureSpawnSettings.getHighestBlock(bl.getLocation());

            this.pasteLocation(1, structure, structureBlock.getLocation());

            // If the block is the void, then set it to null to maintain compatibility.
            if (structureBlock.getType() == Material.VOID_AIR) {
                structureBlock = null;
            }

            // If the block is null, Skip the other steps and spawn.
            if (structureBlock == null) {
                structureBlock = ch.getBlock(8, structureSpawnSettings.getHeight(null), 8);
                // Now to finally paste the schematic
                plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                    // It is assumed at this point that the structure has been spawned.
                    // Add it to the list of spawned structures.
                    plugin.getStructureHandler().putSpawnedStructure(structureBlock.getLocation(),
                            structure);
                    try {
                        SchematicHandler.placeSchematic(structureBlock.getLocation(),
                                structure.getSchematic(),
                                structure.getStructureProperties().canPlaceAir(),
                                structure);
                    } catch (IOException | WorldEditException e) {
                        e.printStackTrace();
                    }
                });

                // Cancel the process and return.
                return;
            }

            // Allows the structures to no longer spawn on plant life.
            if (structure.getStructureProperties().isIgnoringPlants() && ignoreBlocks.getBlocks().contains(structureBlock.getType())) {
                for (int i = structureBlock.getY(); i >= 4; i--) {
                    if (!ignoreBlocks.getBlocks().contains(ch.getBlock(8, i, 8).getType()) && !ch.getBlock(8, i, 8).getType().isAir()) {
                        structureBlock = ch.getBlock(8, i, 8);
                        this.pasteLocation(2, structure, structureBlock.getLocation());
                        break;
                    }
                }
            }

            this.pasteLocation(3, structure, structureBlock.getLocation());

            // calculate SpawnY if first is true
            if (structureSpawnSettings.isCalculateSpawnYFirst()) {
                structureBlock = new Location(structureBlock.getWorld(), structureBlock.getX(), structureSpawnSettings.getHeight(structureBlock.getLocation()), structureBlock.getZ()).getBlock();
            }

            if (!structure.getStructureLimitations().hasWhitelistBlock(structureBlock)) {
                Location location = structureBlock.getLocation();
//                plugin.getLogger().info(structure.getName() + " Location failed bc of whitelist. Does not allow spawning on " + structureBlock.getType() + " (" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")");
                this.run();
                return;
            }

            if (structure.getStructureLimitations().hasBlacklistBlock(structureBlock)) {
                this.run();
//                plugin.getLogger().info(structure.getName() + " Location failed bc of blacklist");
                return;
            }

            // If it can spawn in water
            if (!structure.getStructureProperties().canSpawnInWater()) {
                if (structureBlock.getType() == Material.WATER) {
                    this.run();
//                    plugin.getLogger().info(structure.getName() + " Location failed bc of water");
                    return;
                }
            }

            // If the structure can spawn in lava
            if (!structure.getStructureProperties().canSpawnInLavaLakes()) {
                if (structureBlock.getType() == Material.LAVA) {
                    this.run();
//                    plugin.getLogger().info(structure.getName() + " Location failed bc of lava");
                    return;
                }
            }

            // calculate SpawnY if first is false
            if (!structureSpawnSettings.isCalculateSpawnYFirst()) {
                structureBlock = ch.getBlock(8, structureSpawnSettings.getHeight(structureBlock.getLocation()), 8);
            }

            this.pasteLocation(4, structure, structureBlock.getLocation());

            // If the structure is going to be cut off by the world height limit, pick a new structure.
            if (structure.getStructureLimitations().getWorldHeightRestriction() != -1 &&
                    structureBlock.getLocation().getY() > ch.getWorld().getMaxHeight() - structure.getStructureLimitations().getWorldHeightRestriction()) {
                this.run();
//                plugin.getLogger().info(structure.getName() + " Location failed bc of height");
                return;
            }

            // If the structure can follows block level limit.
            // This only triggers if it spawns on the top.
            if (structure.getStructureLimitations().getBlockLevelLimit().isEnabled()) {
                BlockLevelLimit limit = structure.getStructureLimitations().getBlockLevelLimit();
                if (limit.getMode().equalsIgnoreCase("flat")) {
                    for (int x = limit.getX1() + structureBlock.getX(); x <= limit.getX2() + structureBlock.getX(); x++) {
                        for (int z = limit.getZ1() + structureBlock.getZ(); z <= limit.getZ2() + structureBlock.getZ(); z++) {
                            Block top = ch.getWorld().getBlockAt(x, structureBlock.getY() + 1, z);
                            Block bottom = ch.getWorld().getBlockAt(x, structureBlock.getY() - 1, z);
                            if (!(top.getType().isAir() || ignoreBlocks.getBlocks().contains(top.getType())))
                                return;
                            if (bottom.getType().isAir())
                                return;
                        }
                    }
                } else if (limit.getMode().equalsIgnoreCase("flat_error")) {
                    int total = 0;
                    int error = 0;
                    for (int x = limit.getX1() + structureBlock.getX(); x <= limit.getX2() + structureBlock.getX(); x++) {
                        for (int z = limit.getZ1() + structureBlock.getZ(); z <= limit.getZ2() + structureBlock.getZ(); z++) {
                            Block top = ch.getWorld().getBlockAt(x, structureBlock.getY() + 1, z);
                            Block bottom = ch.getWorld().getBlockAt(x, structureBlock.getY() - 1, z);
                            if (!(top.getType().isAir() || ignoreBlocks.getBlocks().contains(top.getType())))
                                error++;
                            if (bottom.getType().isAir())
                                error++;

                            total += 2;
                        }
                    }

                    if (((double) error / total) > limit.getError()) {
                        this.run();
//                        plugin.getLogger().info(structure.getName() + " Location failed bc of flat_error");
                        return;
                    }
                }
            }

            for (StructureSection section : structure.getStructureSections()) {
                // Check if the structure can spawn according to the section.
                // If an error occurs, report it to the user.
                try {
//                    plugin.getLogger().info("Custom Structures Check For: " + structure.getName() + " - " + section.getName());
                    if (!section.checkStructureConditions(structure, structureBlock, ch)) {
                        return;
                    }
                } catch (Exception ex) {
                    plugin.getLogger().severe(String.format("[CS Addon] An error has occurred when attempting to spawn " +
                            "the structure %s with the custom property %s!", structure.getName(), section.getName()));
                    plugin.getLogger().severe("This is not a CustomStructures error! Please report " +
                            "this to the developer of the addon.");
                    if (plugin.isDebug()) {
                        ex.printStackTrace();
                    } else {
                        plugin.getLogger().severe("Enable debug mode to see the stack trace.");
                    }
                    return;
                }
            }

            // It is assumed at this point that the structure has been spawned.
            // Add it to the list of spawned structures.

            if (!CustomStructures.getInstance().getStructureHandler().validDistance(structure, structureBlock.getLocation())) {
                this.run();
                return;
            }

            plugin.getStructureHandler().putSpawnedStructure(structureBlock.getLocation(),
                    structure);

            this.pasteLocation(5, structure, structureBlock.getLocation());

            try {
                SchematicHandler.placeSchematic(structureBlock.getLocation(),
                        structure.getSchematic(),
                        structure.getStructureProperties().canPlaceAir(),
                        structure);
            } catch (IOException | WorldEditException e) {
                e.printStackTrace();
            }

        } catch (StructureConfigurationException ex) {

            if (gStructure != null) {
                plugin.getLogger().severe("A configuration error was encountered when attempting to spawn the structure: "
                        + gStructure.getName());
            } else {
                plugin.getLogger().severe("A configuration error was encountered when attempting to spawn a structure.");
            }
            plugin.getLogger().severe(ex.getMessage());

        } catch (Exception ex) {

            plugin.getLogger().severe("An error was encountered during the schematic pasting section.");
            plugin.getLogger().severe("The task was stopped for the safety of your server!");
            plugin.getLogger().severe("For more information enable debug mode.");
            if (plugin.isDebug())
                ex.printStackTrace();

        }
    }

    /**
     * Used to a get a random location for the whole map
     *
     * @param center the center of the map
     * @return a random location
     */
    protected Location getRandomLocation(Location center) {
        int x = (RANDOM.nextBoolean() ? RANDOM.nextInt(radius) : -RANDOM.nextInt(radius));
        int z = (RANDOM.nextBoolean() ? RANDOM.nextInt(radius) : -RANDOM.nextInt(radius));

        return new Location(center.getWorld(), x, 0, z);
    }

    private void pasteLocation(int id, Structure structure, Location loc) {
//        int y = loc.getWorld().getHighestBlockYAt(loc.getBlockX(), loc.getBlockZ());
//        plugin.getLogger().info(id + ": " + structure.getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ") Highest Y: " + y);
    }

}
