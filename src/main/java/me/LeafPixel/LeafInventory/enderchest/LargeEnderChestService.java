package me.LeafPixel.LeafInventory.enderchest;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Service for LeafInventory's 54-slot large ender chest.
 *
 * Slots 0-26 can mirror the vanilla ender chest.
 * Slots 27-53 are stored by LeafInventory.
 *
 * All player-facing methods should be called from the player's entity scheduler.
 */
public final class LargeEnderChestService {

    public static final int VANILLA_SIZE = 27;
    public static final int LARGE_SIZE = 54;

    private final JavaPlugin plugin;
    private final LargeEnderChestStore store;
    private final ConcurrentMap<UUID, LargeEnderChestSession> sessions = new ConcurrentHashMap<>();

    private boolean enabled;
    private String permission;
    private boolean syncVanillaRows;
    private int saveIntervalSeconds;
    private String title;

    private ScheduledTask autoSaveTask;

    public LargeEnderChestService(JavaPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.store = new LargeEnderChestStore(plugin);
        reload(config);
    }

    public void load() {
        store.load();
    }

    public void reload(FileConfiguration config) {
        this.enabled = config.getBoolean("largeEnderChest.enabled", false);
        this.permission = config.getString("largeEnderChest.permission", "leafinventory.enderchest.large");
        this.syncVanillaRows = config.getBoolean("largeEnderChest.syncVanillaRows", true);
        this.saveIntervalSeconds = Math.max(5, config.getInt("largeEnderChest.saveIntervalSeconds", 30));
        this.title = config.getString("largeEnderChest.title", "Large Ender Chest");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean canUse(Player player) {
        return enabled && (permission == null || permission.isBlank() || player.hasPermission(permission));
    }

    /**
     * Opens either the large ender chest or the vanilla ender chest depending on config and permission.
     */
    public void openOrFallback(Player player) {
        if (canUse(player)) {
            openLarge(player);
        } else {
            openVanilla(player);
        }
    }

    /**
     * Opens the vanilla 27-slot ender chest.
     */
    public void openVanilla(Player player) {
        player.openInventory(player.getEnderChest());
        player.playSound(player, Sound.BLOCK_ENDER_CHEST_OPEN, SoundCategory.BLOCKS, 1.0f, 1.2f);
    }

    /**
     * Opens the 54-slot virtual large ender chest.
     */
    public void openLarge(Player player) {
        UUID uuid = player.getUniqueId();

        LargeEnderChestSession old = sessions.remove(uuid);
        if (old != null) {
            saveSession(player, old.inventory());
        }

        LargeEnderChestHolder holder = new LargeEnderChestHolder(uuid);
        Inventory inventory = Bukkit.createInventory(holder, LARGE_SIZE, Component.text(title));
        holder.setInventory(inventory);

        if (syncVanillaRows) {
            Inventory vanilla = player.getEnderChest();

            for (int i = 0; i < VANILLA_SIZE; i++) {
                inventory.setItem(i, cloneOrNull(vanilla.getItem(i)));
            }
        }

        ItemStack[] extra = store.getExtra(uuid);

        for (int i = 0; i < LargeEnderChestStore.EXTRA_SIZE; i++) {
            inventory.setItem(VANILLA_SIZE + i, cloneOrNull(extra[i]));
        }

        sessions.put(uuid, new LargeEnderChestSession(uuid, inventory));

        player.openInventory(inventory);
        player.playSound(player, Sound.BLOCK_ENDER_CHEST_OPEN, SoundCategory.BLOCKS, 1.0f, 1.2f);
    }

    public void handleClose(Player player, Inventory inventory) {
        if (!isLargeEnderChestInventory(inventory)) {
            return;
        }

        saveSession(player, inventory);
        sessions.remove(player.getUniqueId());

        player.playSound(player, Sound.BLOCK_ENDER_CHEST_CLOSE, SoundCategory.BLOCKS, 1.0f, 1.2f);
    }

    public void handleQuitOrKick(Player player) {
        UUID uuid = player.getUniqueId();

        LargeEnderChestSession session = sessions.remove(uuid);
        if (session == null) {
            return;
        }

        saveSession(player, session.inventory());
    }

    public boolean isSessionOpen(UUID uuid) {
        return sessions.containsKey(uuid);
    }

    public boolean isLargeEnderChestInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof LargeEnderChestHolder;
    }

    public void startAutoSave() {
        stopAutoSave();

        autoSaveTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                task -> flushAsync(),
                saveIntervalSeconds * 20L,
                saveIntervalSeconds * 20L
        );
    }

    public void stopAutoSave() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }
    }

    public void shutdown() {
        stopAutoSave();
        flushNow();
    }

    public void flushAsync() {
        Map<UUID, ItemStack[]> snapshot = store.snapshot();

        Bukkit.getAsyncScheduler().runNow(plugin, task -> store.writeSnapshot(snapshot));
    }

    public void flushLaterAsync(long delay, TimeUnit unit) {
        Map<UUID, ItemStack[]> snapshot = store.snapshot();

        Bukkit.getAsyncScheduler().runDelayed(plugin, task -> store.writeSnapshot(snapshot), delay, unit);
    }

    public void flushNow() {
        store.saveNow();
    }

    private void saveSession(Player player, Inventory inventory) {
        UUID uuid = player.getUniqueId();

        if (syncVanillaRows) {
            Inventory vanilla = player.getEnderChest();

            for (int i = 0; i < VANILLA_SIZE; i++) {
                vanilla.setItem(i, cloneOrNull(inventory.getItem(i)));
            }
        }

        ItemStack[] extra = new ItemStack[LargeEnderChestStore.EXTRA_SIZE];

        for (int i = 0; i < LargeEnderChestStore.EXTRA_SIZE; i++) {
            extra[i] = cloneOrNull(inventory.getItem(VANILLA_SIZE + i));
        }

        store.setExtra(uuid, extra);
    }

    private static ItemStack cloneOrNull(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }
    public int countExtraUsed(UUID uuid) {
        ItemStack[] extra = store.getExtra(uuid);

        int count = 0;

        for (ItemStack item : extra) {
            if (item != null && item.getType() != Material.AIR) {
                count++;
            }
        }

        return count;
    }
}
