package com.ryandw11.structure.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldInitEvent;

/**
 * Class for when a chunk loads.
 *
 * @author Ryandw11
 */
public class ChunkLoad implements Listener {

    @EventHandler
    public void on(WorldInitEvent event) {
        event.getWorld().getPopulators().add(new StructureBlockPopulator());
    }
}
