package com.ryandw11.structure.listener;

import com.ryandw11.structure.CustomStructures;
import com.ryandw11.structure.api.CustomStructuresAPI;
import com.ryandw11.structure.api.StructureSpawnValidator;
import com.ryandw11.structure.utils.StructurePicker;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Class for when a chunk loads.
 *
 * @author Ryandw11
 */
public class ChunkLoad implements Listener {

    private final List<Chunk> chunksList;
    private Instant cooldown;

    public ChunkLoad() {
        chunksList = new ArrayList<>();

        cooldown = Instant.now();
    }

    @EventHandler
    public void on(ChunkLoadEvent event) {
        World world = event.getWorld();
        Chunk source = event.getChunk();

        if (ChronoUnit.SECONDS.between(cooldown, Instant.now()) < 1) {
            return;
        }

        StructureSpawnValidator validator = CustomStructuresAPI.validator;
        if (validator != null && !validator.shouldSpawn()) return;

        if (this.chunksList.contains(source)) return;
        if (!world.getName().equalsIgnoreCase(CustomStructures.getInstance().getConfig().getString("world"))) return;

        this.chunksList.add(source);

        Block b = source.getBlock(8, 5, 8); //Grabs the block 8, 5, 8 in that chunk.

        try {
            StructurePicker s = new StructurePicker(b, source, CustomStructures.getInstance());
            s.run();
        } catch (RuntimeException ex) {
            // ignore, error already logged.
        }

        cooldown = Instant.now();
    }
}