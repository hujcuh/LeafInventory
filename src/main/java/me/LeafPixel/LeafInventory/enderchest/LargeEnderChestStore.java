package me.LeafPixel.LeafInventory.enderchest;

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
 * Stores the extra 27 slots of the large ender chest.
 *
 * Vanilla ender chest slots are not stored here.
 * This store only manages slots 27-53 of the virtual 54-slot GUI.
 */
public final class LargeEnderChestStore {

    public static final int EXTRA_SIZE = 27;

    private final JavaPlugin plugin;
    private final File file;

    private final ConcurrentMap<UUID, ItemStack[]> extraStorage = new ConcurrentHashMap<>();

    public LargeEnderChestStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "large-enderchests.yml");
    }

    public void load() {
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();

                if (!file.createNewFile()) {
                    plugin.getLogger().warning("Failed to create large-enderchests.yml");
                }
            } catch (IOException e) {
                throw new RuntimeException("Cannot create large-enderchests.yml", e);
            }
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        extraStorage.clear();

        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            return;
        }

        for (String rawUuid : players.getKeys(false)) {
            UUID uuid;

            try {
                uuid = UUID.fromString(rawUuid);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid UUID in large-enderchests.yml: " + rawUuid);
                continue;
            }

            ItemStack[] extra = new ItemStack[EXTRA_SIZE];
            String base = "players." + rawUuid + ".extra";

            for (int i = 0; i < EXTRA_SIZE; i++) {
                extra[i] = cloneOrNull(yaml.getItemStack(base + "." + i));
            }

            if (!isEmpty(extra)) {
                extraStorage.put(uuid, extra);
            }
        }
    }

    public ItemStack[] getExtra(UUID uuid) {
        ItemStack[] stored = extraStorage.get(uuid);

        if (stored == null) {
            return new ItemStack[EXTRA_SIZE];
        }

        return cloneArray(stored);
    }

    public void setExtra(UUID uuid, ItemStack[] extra) {
        if (extra == null || isEmpty(extra)) {
            extraStorage.remove(uuid);
            return;
        }

        extraStorage.put(uuid, normalize(extra));
    }

    public void remove(UUID uuid) {
        extraStorage.remove(uuid);
    }

    public Map<UUID, ItemStack[]> snapshot() {
        Map<UUID, ItemStack[]> copy = new HashMap<>();

        for (var entry : extraStorage.entrySet()) {
            copy.put(entry.getKey(), cloneArray(entry.getValue()));
        }

        return copy;
    }

    public void writeSnapshot(Map<UUID, ItemStack[]> snapshot) {
        YamlConfiguration out = new YamlConfiguration();

        for (var entry : snapshot.entrySet()) {
            UUID uuid = entry.getKey();
            ItemStack[] extra = normalize(entry.getValue());

            if (isEmpty(extra)) {
                continue;
            }

            String base = "players." + uuid + ".extra";

            for (int i = 0; i < EXTRA_SIZE; i++) {
                out.set(base + "." + i, cloneOrNull(extra[i]));
            }
        }

        try {
            plugin.getDataFolder().mkdirs();
            out.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save large-enderchests.yml: " + e.getMessage());
        }
    }

    public void saveNow() {
        writeSnapshot(snapshot());
    }

    private static ItemStack[] normalize(ItemStack[] input) {
        ItemStack[] out = new ItemStack[EXTRA_SIZE];

        if (input == null) {
            return out;
        }

        int len = Math.min(input.length, EXTRA_SIZE);

        for (int i = 0; i < len; i++) {
            out[i] = cloneOrNull(input[i]);
        }

        return out;
    }

    private static ItemStack[] cloneArray(ItemStack[] input) {
        ItemStack[] out = new ItemStack[EXTRA_SIZE];

        if (input == null) {
            return out;
        }

        int len = Math.min(input.length, EXTRA_SIZE);

        for (int i = 0; i < len; i++) {
            out[i] = cloneOrNull(input[i]);
        }

        return out;
    }

    private static boolean isEmpty(ItemStack[] items) {
        if (items == null) {
            return true;
        }

        for (ItemStack item : items) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }

        return true;
    }

    private static ItemStack cloneOrNull(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }
}