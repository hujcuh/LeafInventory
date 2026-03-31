package me.LeafPixel.LeafInventory.workstation;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VirtualWorkstationStore {
    private final JavaPlugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    public VirtualWorkstationStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "virtual-workstations.yml");
    }

    public void load() {
        if (!file.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                if (!file.createNewFile()) {
                    plugin.getLogger().warning("Failed to create virtual-workstations.yml");
                }
            } catch (IOException e) {
                throw new RuntimeException("Cannot create virtual-workstations.yml", e);
            }
        }
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    public Map<UUID, EnumMap<VirtualWorkstationType, VirtualWorkstationData>> readAll() {
        if (yaml == null) load();

        Map<UUID, EnumMap<VirtualWorkstationType, VirtualWorkstationData>> result = new HashMap<>();
        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) return result;

        for (String rawUuid : players.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(rawUuid);
            } catch (IllegalArgumentException ex) {
                continue;
            }

            EnumMap<VirtualWorkstationType, VirtualWorkstationData> typed = new EnumMap<>(VirtualWorkstationType.class);
            for (VirtualWorkstationType type : VirtualWorkstationType.values()) {
                String base = "players." + rawUuid + "." + type.name();
                if (!yaml.contains(base)) continue;

                VirtualWorkstationData data = new VirtualWorkstationData();
                data.setInput(yaml.getItemStack(base + ".input"));
                data.setFuel(yaml.getItemStack(base + ".fuel"));
                data.setOutput(yaml.getItemStack(base + ".output"));
                data.setBurnTimeRemaining(yaml.getInt(base + ".burnTimeRemaining", 0));
                data.setBurnTimeTotal(yaml.getInt(base + ".burnTimeTotal", 0));
                data.setCookTime(yaml.getInt(base + ".cookTime", 0));
                data.setCookTimeTotal(yaml.getInt(base + ".cookTimeTotal", 200));
                typed.put(type, data);
            }

            if (!typed.isEmpty()) {
                result.put(uuid, typed);
            }
        }

        return result;
    }

    public void writeAll(Map<UUID, EnumMap<VirtualWorkstationType, VirtualWorkstationData>> source) {
        if (yaml == null) load();

        yaml.set("players", null);

        for (var playerEntry : source.entrySet()) {
            String uuid = playerEntry.getKey().toString();
            for (var typeEntry : playerEntry.getValue().entrySet()) {
                String base = "players." + uuid + "." + typeEntry.getKey().name();
                VirtualWorkstationData data = typeEntry.getValue();

                yaml.set(base + ".input", cloneOrNull(data.getInput()));
                yaml.set(base + ".fuel", cloneOrNull(data.getFuel()));
                yaml.set(base + ".output", cloneOrNull(data.getOutput()));
                yaml.set(base + ".burnTimeRemaining", data.getBurnTimeRemaining());
                yaml.set(base + ".burnTimeTotal", data.getBurnTimeTotal());
                yaml.set(base + ".cookTime", data.getCookTime());
                yaml.set(base + ".cookTimeTotal", data.getCookTimeTotal());
            }
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save virtual-workstations.yml: " + e.getMessage());
        }
    }

    private static ItemStack cloneOrNull(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }
}
