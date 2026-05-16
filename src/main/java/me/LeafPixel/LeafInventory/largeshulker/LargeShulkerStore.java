package me.LeafPixel.LeafInventory.largeshulker;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Stores all 54-slot large shulker contents.
 */
public final class LargeShulkerStore {

    private final JavaPlugin plugin;
    private final File file;

    private final ConcurrentMap<UUID, LargeShulkerData> data = new ConcurrentHashMap<>();

    public LargeShulkerStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "large-shulkers.yml");
    }

    public void load() {
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();

                if (!file.createNewFile()) {
                    plugin.getLogger().warning("Failed to create large-shulkers.yml");
                }
            } catch (IOException e) {
                throw new RuntimeException("Cannot create large-shulkers.yml", e);
            }
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        data.clear();

        ConfigurationSection root = yaml.getConfigurationSection("shulkers");
        if (root == null) {
            return;
        }

        for (String rawId : root.getKeys(false)) {
            UUID shulkerId;

            try {
                shulkerId = UUID.fromString(rawId);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid large shulker id in large-shulkers.yml: " + rawId);
                continue;
            }

            String base = "shulkers." + rawId;

            UUID owner = null;
            String rawOwner = yaml.getString(base + ".owner", null);

            if (rawOwner != null && !rawOwner.isBlank()) {
                try {
                    owner = UUID.fromString(rawOwner);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Invalid owner UUID for large shulker " + rawId + ": " + rawOwner);
                }
            }

            int rows = yaml.getInt(base + ".rows", LargeShulkerData.DEFAULT_ROWS);
            LargeShulkerData shulkerData = new LargeShulkerData(shulkerId, owner, rows);

            shulkerData.setCreatedAt(yaml.getLong(base + ".createdAt", System.currentTimeMillis()));
            shulkerData.setLastAccessTime(yaml.getLong(base + ".lastAccessTime", System.currentTimeMillis()));

            ItemStack[] items = new ItemStack[shulkerData.size()];

            for (int i = 0; i < items.length; i++) {
                items[i] = cloneOrNull(yaml.getItemStack(base + ".items." + i));
            }

            shulkerData.setItems(items);
            data.put(shulkerId, shulkerData);
        }
    }

    public boolean contains(UUID shulkerId) {
        return data.containsKey(shulkerId);
    }

    public LargeShulkerData get(UUID shulkerId) {
        LargeShulkerData found = data.get(shulkerId);
        return found == null ? null : found.copy();
    }

    public LargeShulkerData getOrCreate(UUID shulkerId, UUID owner, int rows) {
        LargeShulkerData found = data.computeIfAbsent(
                shulkerId,
                id -> new LargeShulkerData(id, owner, rows)
        );

        return found.copy();
    }

    public void put(LargeShulkerData source) {
        if (source == null) {
            return;
        }

        data.put(source.shulkerId(), source.copy());
    }

    public void remove(UUID shulkerId) {
        data.remove(shulkerId);
    }

    public Map<UUID, LargeShulkerData> snapshot() {
        Map<UUID, LargeShulkerData> copy = new HashMap<>();

        for (var entry : data.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().copy());
        }

        return copy;
    }

    public void writeSnapshot(Map<UUID, LargeShulkerData> snapshot) {
        YamlConfiguration out = new YamlConfiguration();

        for (LargeShulkerData shulkerData : snapshot.values()) {
            String base = "shulkers." + shulkerData.shulkerId();

            out.set(base + ".owner", shulkerData.owner() == null ? null : shulkerData.owner().toString());
            out.set(base + ".rows", shulkerData.rows());
            out.set(base + ".createdAt", shulkerData.createdAt());
            out.set(base + ".lastAccessTime", shulkerData.lastAccessTime());

            ItemStack[] items = shulkerData.items();

            for (int i = 0; i < items.length; i++) {
                out.set(base + ".items." + i, cloneOrNull(items[i]));
            }
        }

        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save large-shulkers.yml: " + e.getMessage());
        }
    }

    public void saveNow() {
        writeSnapshot(snapshot());
    }

    private static ItemStack cloneOrNull(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }
}