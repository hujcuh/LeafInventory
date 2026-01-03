package me.LeafPixel.LeafInventory.workstation;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Furnace;
import org.bukkit.block.Smoker;
import org.bukkit.block.BlastFurnace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class WorkstationManager {

    private final JavaPlugin plugin;

    private World world;
    private String worldName;
    private int baseChunkX, baseChunkZ;
    private int baseY;
    private int stepY;

    // UUID -> index
    private final Map<UUID, Integer> indexMap = new HashMap<>();
    private int nextIndex = 0;

    private File indexFile;
    private YamlConfiguration indexYaml;

    public WorkstationManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initFromConfig() {
        var cfg = plugin.getConfig();

        worldName = cfg.getString("workstation.worldName", "leafinventory_workstations");
        baseChunkX = cfg.getInt("workstation.baseChunkX", 0);
        baseChunkZ = cfg.getInt("workstation.baseChunkZ", 0);
        baseY = cfg.getInt("workstation.baseY", 64);
        stepY = cfg.getInt("workstation.stepY", 2);

        // 1) world
        world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.createWorld(new WorldCreator(worldName));
        }

        // 2) force-load 
        world.getChunkAt(baseChunkX, baseChunkZ).setForceLoaded(true);

        // 3) load index map
        indexFile = new File(plugin.getDataFolder(), "workstations.yml");
        if (!indexFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                indexFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException("Cannot create workstations.yml", e);
            }
        }
        indexYaml = YamlConfiguration.loadConfiguration(indexFile);
        loadIndexMap();
    }

    private void loadIndexMap() {
        indexMap.clear();
        nextIndex = indexYaml.getInt("nextIndex", 0);

        var sec = indexYaml.getConfigurationSection("indexes");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int idx = sec.getInt(key);
                    indexMap.put(uuid, idx);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    private void saveIndexMap() {
        indexYaml.set("nextIndex", nextIndex);


        indexYaml.set("indexes", null);
        for (var e : indexMap.entrySet()) {
            indexYaml.set("indexes." + e.getKey(), e.getValue());
        }
        try {
            indexYaml.save(indexFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save workstations.yml: " + e.getMessage());
        }
    }


    public int getOrAssignIndex(UUID uuid) {
        Integer idx = indexMap.get(uuid);
        if (idx != null) return idx;

        idx = nextIndex++;
        indexMap.put(uuid, idx);
        saveIndexMap();
        return idx;
    }


    private Location getBlockLocation(UUID uuid, WorkstationType type) {
        int idx = getOrAssignIndex(uuid);


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

    private Block getOrCreateBlock(UUID uuid, WorkstationType type) {
        Location loc = getBlockLocation(uuid, type);
        Block b = world.getBlockAt(loc);

        Material want = type.material;
        if (b.getType() != want) {
            b.setType(want, false);
        }
        return b;
    }

    public void openFurnace(Player player) {
        BlockState state = getOrCreateBlock(player.getUniqueId(), WorkstationType.FURNACE).getState();
        player.openInventory(((Furnace) state).getInventory());
    }

    public void openBlastFurnace(Player player) {
        BlockState state = getOrCreateBlock(player.getUniqueId(), WorkstationType.BLAST_FURNACE).getState();
        player.openInventory(((BlastFurnace) state).getInventory());
    }

    public void openSmoker(Player player) {
        BlockState state = getOrCreateBlock(player.getUniqueId(), WorkstationType.SMOKER).getState();
        player.openInventory(((Smoker) state).getInventory());
    }


    public void clearAll(UUID uuid) {
        clearOne(uuid, WorkstationType.FURNACE);
        clearOne(uuid, WorkstationType.BLAST_FURNACE);
        clearOne(uuid, WorkstationType.SMOKER);
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


    public void removePlayer(UUID uuid) {
        indexMap.remove(uuid);
        saveIndexMap();
    }

    public World getWorld() {
        return world;
    }

    public enum WorkstationType {
        FURNACE(Material.FURNACE),
        BLAST_FURNACE(Material.BLAST_FURNACE),
        SMOKER(Material.SMOKER);

        final Material material;
        WorkstationType(Material material) { this.material = material; }
    }
}
