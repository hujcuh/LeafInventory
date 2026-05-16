package me.LeafPixel.LeafInventory.largeshulker;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Persistent placed index for large shulker blocks.
 *
 * This index maps:
 * locationKey -> shulkerId
 *
 * The TileState PDC is still used as block-side metadata,
 * but this store is the plugin-side authority for placed blocks.
 */
public final class LargeShulkerPlacedStore {

    private final JavaPlugin plugin;
    private final File file;
    private final ConcurrentMap<String, UUID> placed = new ConcurrentHashMap<>();

    private YamlConfiguration yaml;

    public LargeShulkerPlacedStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "large-shulker-placed.yml");
    }

    public void load() {
        placed.clear();

        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();

                if (!file.createNewFile()) {
                    plugin.getLogger().warning("Failed to create large-shulker-placed.yml");
                }
            } catch (IOException e) {
                throw new RuntimeException("Cannot create large-shulker-placed.yml", e);
            }
        }

        yaml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection section = yaml.getConfigurationSection("placed");
        if (section == null) {
            return;
        }

        for (String locationKey : section.getKeys(false)) {
            String rawId = section.getString(locationKey);

            if (rawId == null || rawId.isBlank()) {
                continue;
            }

            try {
                placed.put(locationKey, UUID.fromString(rawId));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid shulkerId in large-shulker-placed.yml: " + rawId);
            }
        }
    }

    public UUID get(String locationKey) {
        return placed.get(locationKey);
    }

    public void put(String locationKey, UUID shulkerId) {
        if (locationKey == null || locationKey.isBlank() || shulkerId == null) {
            return;
        }

        placed.put(locationKey, shulkerId);
    }

    public void remove(String locationKey) {
        if (locationKey == null || locationKey.isBlank()) {
            return;
        }

        placed.remove(locationKey);
    }

    public boolean containsLocation(String locationKey) {
        return placed.containsKey(locationKey);
    }

    public boolean containsShulkerId(UUID shulkerId) {
        if (shulkerId == null) {
            return false;
        }

        return placed.containsValue(shulkerId);
    }

    public String findLocationByShulkerId(UUID shulkerId) {
        if (shulkerId == null) {
            return null;
        }

        for (var entry : placed.entrySet()) {
            if (shulkerId.equals(entry.getValue())) {
                return entry.getKey();
            }
        }

        return null;
    }

    public Map<String, UUID> snapshot() {
        return new HashMap<>(placed);
    }

    public void replaceAll(Map<String, UUID> source) {
        placed.clear();

        if (source != null) {
            placed.putAll(source);
        }
    }

    public void saveNow() {
        if (yaml == null) {
            yaml = new YamlConfiguration();
        }

        yaml.set("placed", null);

        for (var entry : placed.entrySet()) {
            yaml.set("placed." + entry.getKey(), entry.getValue().toString());
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save large-shulker-placed.yml: " + e.getMessage());
        }
    }
}
