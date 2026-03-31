package me.LeafPixel.LeafInventory.workstation;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists workstation index allocation (UUID -> index, nextIndex) in workstations.yml.
 */
public final class WorkstationIndexStore {

    private final JavaPlugin plugin;

    private final File file;
    private YamlConfiguration yaml;

    public WorkstationIndexStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "workstations.yml");
    }

    public void load() {
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                if (!file.createNewFile()) {
                    plugin.getLogger().warning("Failed to create workstations.yml (already exists or cannot create).");
                }
            } catch (IOException e) {
                throw new RuntimeException("Cannot create workstations.yml", e);
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public Data read() {
        //Ensure load() is called before read().
        if (yaml == null) load();

        int nextIndex = yaml.getInt("nextIndex", 0);
        Map<UUID, Integer> map = new HashMap<>();

        var sec = yaml.getConfigurationSection("indexes");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    int idx = sec.getInt(key);
                    map.put(uuid, idx);
                } catch (IllegalArgumentException ignored) {
                    //Ignore invalid UUID keys.
                }
            }
        }
        return new Data(nextIndex, map);
    }

    public void write(int nextIndex, Map<UUID, Integer> indexMap) {
        if (yaml == null) load();

        yaml.set("nextIndex", nextIndex);
        yaml.set("indexes", null);

        for (var e : indexMap.entrySet()) {
            yaml.set("indexes." + e.getKey(), e.getValue());
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save workstations.yml: " + e.getMessage());
        }
    }

    public record Data(int nextIndex, Map<UUID, Integer> indexMap) {}
}
