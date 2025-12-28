
package me.LeafPixel.LeafInventory;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class LastSeenManager {

    private final JavaPlugin plugin;
    private final Map<UUID, Long> lastSeen = new HashMap<>();

    private File file;
    private YamlConfiguration yaml;

    public LastSeenManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        file = new File(plugin.getDataFolder(), "lastseen.yml");
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException("Cannot create lastseen.yml", e);
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);

        var sec = yaml.getConfigurationSection("lastSeen");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    long t = sec.getLong(key);
                    lastSeen.put(uuid, t);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public void save() {
        if (yaml == null || file == null) return;
        yaml.set("lastSeen", null);
        for (var e : lastSeen.entrySet()) {
            yaml.set("lastSeen." + e.getKey(), e.getValue());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save lastseen.yml: " + e.getMessage());
        }
    }

    public void touch(UUID uuid) {
        lastSeen.put(uuid, System.currentTimeMillis());
    }

    public long getLastSeen(UUID uuid) {
        return lastSeen.getOrDefault(uuid, 0L);
    }

    public Set<UUID> all() {
        return new HashSet<>(lastSeen.keySet());
    }

    public void remove(UUID uuid) {
        lastSeen.remove(uuid);
    }
}
