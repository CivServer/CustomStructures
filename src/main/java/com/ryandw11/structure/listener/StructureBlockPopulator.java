package com.ryandw11.structure.listener;

import com.ryandw11.structure.CustomStructures;
import com.ryandw11.structure.utils.StructurePicker;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.generator.BlockPopulator;
import org.jetbrains.annotations.NotNull;

import java.util.Random;

/**
 * Class for when a chunk loads.
 *
 * @author Ryandw11
 */
public class StructureBlockPopulator extends BlockPopulator {

    @Override
    public void populate(@NotNull World world, @NotNull Random random, @NotNull Chunk source) {
        if (!world.getName().equalsIgnoreCase(CustomStructures.getInstance().getConfig().getString("world"))) return;

        Block b = source.getBlock(8, 5, 8); //Grabs the block 8, 5, 8 in that chunk.

        /*
         * Schematic handler
         * This activity is done async to prevent the server from lagging.
         */
        try {
/*            StructurePicker s = new StructurePicker(b, source, CustomStructures.getInstance());
            s.run();*/
        } catch (RuntimeException ex) {
            // ignore, error already logged.
        }
    }
}
