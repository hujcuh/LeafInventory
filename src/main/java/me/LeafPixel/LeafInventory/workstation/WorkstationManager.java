package me.LeafPixel.LeafInventory.workstation;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.block.Smoker;
import org.bukkit.block.BlastFurnace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import java.util.UUID;

/**
 * Manages per-player workstation blocks in a dedicated world.
 * Each player gets an "index" which maps to a unique Y-layer.
 */
public final class WorkstationManager implements PortableWorkstationBackend  {

    private final JavaPlugin plugin;
    private final WorkstationIndexStore store;

    private World world;
    private String worldName;
    private int baseChunkX, baseChunkZ;
    private int baseY;
    private int stepY;

    // UUID -> index
    private final ConcurrentMap<UUID, Integer> indexMap = new ConcurrentHashMap<>();
    private final AtomicInteger nextIndex = new AtomicInteger(0);

    public WorkstationManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.store = new WorkstationIndexStore(plugin);
    }

    /**
     * Initialize from config, create/load world, force-load chunk, load index map.
     */
    @Override
    public void initFromConfig() {
        var cfg = plugin.getConfig();

        worldName = cfg.getString("workstation.worldName", "leafinventory_workstations");
        baseChunkX = cfg.getInt("workstation.baseChunkX", 0);
        baseChunkZ = cfg.getInt("workstation.baseChunkZ", 0);
        baseY = cfg.getInt("workstation.baseY", 64);
        stepY = cfg.getInt("workstation.stepY", 2);

        // Create or load the dedicated world.
        world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.createWorld(new WorldCreator(worldName));
        }

        // Force-load the base chunk to keep workstation blocks available.
        world.getChunkAt(baseChunkX, baseChunkZ).setForceLoaded(true);

        // Load index allocation from disk.
        store.load();
        var data = store.read();
        indexMap.clear();
        indexMap.putAll(data.indexMap());
        nextIndex.set(data.nextIndex());

    }

    public World getWorld() {
        return world;
    }
    
    @Override
    public World getGuardWorld() {
        return world;
    }

    public synchronized int getOrAssignIndex(UUID uuid) {
        Integer idx = indexMap.get(uuid);
        if (idx != null) return idx;

        idx = nextIndex.getAndIncrement();
        indexMap.put(uuid, idx);

        // Persist mapping when a new index is allocated.
        store.write(nextIndex.get(), indexMap);
        return idx;
    }

    public void removePlayer(UUID uuid) {
        indexMap.remove(uuid);
        store.write(nextIndex.get(), indexMap);
    }

    @Override
    public void clearAll(UUID uuid) {

        clearOne(uuid, WorkstationType.FURNACE);
        clearOne(uuid, WorkstationType.BLAST_FURNACE);
        clearOne(uuid, WorkstationType.SMOKER);
    }

    @Override
    public void openFurnace(Player player) {
        BlockState state = getOrCreateBlock(player.getUniqueId(), WorkstationType.FURNACE).getState();
        player.openInventory(((Furnace) state).getInventory());
    }

    @Override
    public void openBlastFurnace(Player player) {
        BlockState state = getOrCreateBlock(player.getUniqueId(), WorkstationType.BLAST_FURNACE).getState();
        player.openInventory(((BlastFurnace) state).getInventory());
    }

    @Override
    public void openSmoker(Player player) {
        BlockState state = getOrCreateBlock(player.getUniqueId(), WorkstationType.SMOKER).getState();
        player.openInventory(((Smoker) state).getInventory());
    }

    // --------------------------
    // Internal helpers
    // --------------------------

    private Block getOrCreateBlock(UUID uuid, WorkstationType type) {
        Location loc = getBlockLocation(uuid, type);
        Block b = world.getBlockAt(loc);

        Material want = type.material;
        if (b.getType() != want) {
            // Do not apply physics; we want a stable hidden setup.
            b.setType(want, false);
        }
        return b;
    }

    private void clearOne(UUID uuid, WorkstationType type) {
        Location loc = getBlockLocation(uuid, type);
        Block b = world.getBlockAt(loc);
        if (b.getType() != type.material) return;

        BlockState st = b.getState();
        if (st instanceof Furnace f) f.getInventory().clear();
        else if (st instanceof BlastFurnace bf) bf.getInventory().clear();
        else if (st instanceof Smoker s) s.getInventory().clear();
    }

    private Location getBlockLocation(UUID uuid, WorkstationType type) {
        int idx = getOrAssignIndex(uuid);

        // Place each workstation type at a different x offset inside the chunk.
        int xInChunk = 1 + type.ordinal();
        int zInChunk = 1;

        int x = (baseChunkX << 4) + xInChunk;
        int z = (baseChunkZ << 4) + zInChunk;

        int y = baseY + idx * stepY;
        if (y > world.getMaxHeight() - 5) {
            throw new IllegalStateException("Workstation capacity exceeded, increase stepY or use more chunks");
        }

        return new Location(world, x, y, z);
    }

    public enum WorkstationType {
        FURNACE(Material.FURNACE),
        BLAST_FURNACE(Material.BLAST_FURNACE),
        SMOKER(Material.SMOKER);

        final Material material;

        WorkstationType(Material material) {
            this.material = material;
        }
    }
}
