
package me.LeafPixel.LeafInventory.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Immutable runtime config snapshot.
 *
 * Why:
 * - Read config once on enable (and optionally on reload later).
 * - Avoid reading FileConfiguration on every event (performance).
 * - Central place to apply defaults consistently.
 *
 * This class depends on:
 * - ConfigKeys (constants in a separate file)
 * - ShulkerLockMode (enum in a separate file)
 */
public final class LeafConfig {

    // -------- Feature toggles --------
    public final boolean enableShulkerbox;
    public final boolean enableEnderChest;
    public final boolean enableCraftingTable;

    public final boolean enableSmithingTable;
    public final boolean enableStoneCutter;
    public final boolean enableGrindstone;
    public final boolean enableCartographyTable;
    public final boolean enableLoom;
    public final boolean enableAnvil;
    public final boolean enableEnchantingTable;

    public final boolean enableFurnace;
    public final boolean enableBlastFurnace;
    public final boolean enableSmoker;

    public final boolean usePermissions;

    // -------- Workstation settings --------
    public final String workstationWorldName;
    public final int workstationBaseChunkX;
    public final int workstationBaseChunkZ;
    public final int workstationBaseY;
    public final int workstationStepY;
    public final String workstationBypassPermission;

    // -------- Cleanup settings --------
    public final int cleanupInactiveDays;
    public final int cleanupIntervalMinutes;

    // -------- Shulker safety settings (route2) --------
    public final int shulkerCooldownTicks;

    /**
     * If true, while editing a shulker UI, prevent opening another shulker from the bottom inventory.
     * This directly targets your reproduction case (right-click another shulker while UI is open).
     */
    public final boolean shulkerBlockOpenWhileEditing;

    /**
     * Lock strategy while editing.
     * SLOT_ONLY: only lock the backing shulker slot + block dangerous actions.
     * FULL_INVENTORY: cancel nearly all inventory interactions while editing (maximum safety).
     */
    public final ShulkerLockMode shulkerLockMode;

    private LeafConfig(
            boolean enableShulkerbox,
            boolean enableEnderChest,
            boolean enableCraftingTable,
            boolean enableSmithingTable,
            boolean enableStoneCutter,
            boolean enableGrindstone,
            boolean enableCartographyTable,
            boolean enableLoom,
            boolean enableAnvil,
            boolean enableEnchantingTable,
            boolean enableFurnace,
            boolean enableBlastFurnace,
            boolean enableSmoker,
            boolean usePermissions,
            String workstationWorldName,
            int workstationBaseChunkX,
            int workstationBaseChunkZ,
            int workstationBaseY,
            int workstationStepY,
            String workstationBypassPermission,
            int cleanupInactiveDays,
            int cleanupIntervalMinutes,
            int shulkerCooldownTicks,
            boolean shulkerBlockOpenWhileEditing,
            ShulkerLockMode shulkerLockMode
    ) {
        this.enableShulkerbox = enableShulkerbox;
        this.enableEnderChest = enableEnderChest;
        this.enableCraftingTable = enableCraftingTable;

        this.enableSmithingTable = enableSmithingTable;
        this.enableStoneCutter = enableStoneCutter;
        this.enableGrindstone = enableGrindstone;
        this.enableCartographyTable = enableCartographyTable;
        this.enableLoom = enableLoom;
        this.enableAnvil = enableAnvil;
        this.enableEnchantingTable = enableEnchantingTable;

        this.enableFurnace = enableFurnace;
        this.enableBlastFurnace = enableBlastFurnace;
        this.enableSmoker = enableSmoker;

        this.usePermissions = usePermissions;

        this.workstationWorldName = workstationWorldName;
        this.workstationBaseChunkX = workstationBaseChunkX;
        this.workstationBaseChunkZ = workstationBaseChunkZ;
        this.workstationBaseY = workstationBaseY;
        this.workstationStepY = workstationStepY;
        this.workstationBypassPermission = workstationBypassPermission;

        this.cleanupInactiveDays = cleanupInactiveDays;
        this.cleanupIntervalMinutes = cleanupIntervalMinutes;

        this.shulkerCooldownTicks = shulkerCooldownTicks;
        this.shulkerBlockOpenWhileEditing = shulkerBlockOpenWhileEditing;
        this.shulkerLockMode = shulkerLockMode;
    }

    /**
     * Apply defaults to config.yml. Call before saveConfig().
     *
     * This covers your original defaults plus route2 shulker safety options.
     */
    public static void applyDefaults(JavaPlugin plugin, boolean isPaper) {
        FileConfiguration c = plugin.getConfig();

        // Core toggles
        c.addDefault(ConfigKeys.ENABLE_SHULKERBOX, true);
        c.addDefault(ConfigKeys.ENABLE_ENDER_CHEST, true);
        c.addDefault(ConfigKeys.ENABLE_CRAFTING_TABLE, true);

        // Permissions
        c.addDefault(ConfigKeys.USE_PERMISSIONS, false);

        // Paper extras + workstation + cleanup
        if (isPaper) {
            c.addDefault(ConfigKeys.ENABLE_SMITHING_TABLE, true);
            c.addDefault(ConfigKeys.ENABLE_STONECUTTER, true);
            c.addDefault(ConfigKeys.ENABLE_GRINDSTONE, true);
            c.addDefault(ConfigKeys.ENABLE_CARTOGRAPHY_TABLE, true);
            c.addDefault(ConfigKeys.ENABLE_LOOM, true);
            c.addDefault(ConfigKeys.ENABLE_ANVIL, false);
            c.addDefault(ConfigKeys.ENABLE_ENCHANTING_TABLE, true);

            c.addDefault(ConfigKeys.ENABLE_FURNACE, true);
            c.addDefault(ConfigKeys.ENABLE_BLAST_FURNACE, true);
            c.addDefault(ConfigKeys.ENABLE_SMOKER, true);

            c.addDefault(ConfigKeys.WS_WORLD_NAME, "leafinventory_workstations");
            c.addDefault(ConfigKeys.WS_BASE_CHUNK_X, 0);
            c.addDefault(ConfigKeys.WS_BASE_CHUNK_Z, 0);
            c.addDefault(ConfigKeys.WS_BASE_Y, 64);
            c.addDefault(ConfigKeys.WS_STEP_Y, 2);
            c.addDefault(ConfigKeys.WS_BYPASS_PERMISSION, "leafinventory.workstation.bypass");

            c.addDefault(ConfigKeys.CLEANUP_INACTIVE_DAYS, 30);
            c.addDefault(ConfigKeys.CLEANUP_INTERVAL_MINUTES, 60);
        }

        // Route2 shulker safety defaults
        c.addDefault(ConfigKeys.SHULKER_COOLDOWN_TICKS, 2);

        // True by default because your repro involves opening another shulker while editing.
        c.addDefault(ConfigKeys.SHULKER_BLOCK_OPEN_WHILE_EDITING, true);

        // SLOT_ONLY keeps good UX; FULL_INVENTORY is maximum safety.
        c.addDefault(ConfigKeys.SHULKER_LOCK_MODE, "SLOT_ONLY");

        c.options().copyDefaults(true);
        plugin.saveConfig();
    }

    /**
     * Load an immutable snapshot from config.yml.
     * Call once onEnable() and reuse the returned instance.
     */
    public static LeafConfig load(JavaPlugin plugin, boolean isPaper) {
        FileConfiguration c = plugin.getConfig();

        boolean enableShulkerbox = c.getBoolean(ConfigKeys.ENABLE_SHULKERBOX, true);
        boolean enableEnderChest = c.getBoolean(ConfigKeys.ENABLE_ENDER_CHEST, true);
        boolean enableCraftingTable = c.getBoolean(ConfigKeys.ENABLE_CRAFTING_TABLE, true);

        boolean enableSmithingTable = isPaper && c.getBoolean(ConfigKeys.ENABLE_SMITHING_TABLE, true);
        boolean enableStoneCutter = isPaper && c.getBoolean(ConfigKeys.ENABLE_STONECUTTER, true);
        boolean enableGrindstone = isPaper && c.getBoolean(ConfigKeys.ENABLE_GRINDSTONE, true);
        boolean enableCartographyTable = isPaper && c.getBoolean(ConfigKeys.ENABLE_CARTOGRAPHY_TABLE, true);
        boolean enableLoom = isPaper && c.getBoolean(ConfigKeys.ENABLE_LOOM, true);
        boolean enableAnvil = isPaper && c.getBoolean(ConfigKeys.ENABLE_ANVIL, false);
        boolean enableEnchantingTable = isPaper && c.getBoolean(ConfigKeys.ENABLE_ENCHANTING_TABLE, true);

        boolean enableFurnace = isPaper && c.getBoolean(ConfigKeys.ENABLE_FURNACE, true);
        boolean enableBlastFurnace = isPaper && c.getBoolean(ConfigKeys.ENABLE_BLAST_FURNACE, true);
        boolean enableSmoker = isPaper && c.getBoolean(ConfigKeys.ENABLE_SMOKER, true);

        boolean usePermissions = c.getBoolean(ConfigKeys.USE_PERMISSIONS, false);

        String wsWorldName = c.getString(ConfigKeys.WS_WORLD_NAME, "leafinventory_workstations");
        int wsBaseChunkX = c.getInt(ConfigKeys.WS_BASE_CHUNK_X, 0);
        int wsBaseChunkZ = c.getInt(ConfigKeys.WS_BASE_CHUNK_Z, 0);
        int wsBaseY = c.getInt(ConfigKeys.WS_BASE_Y, 64);
        int wsStepY = c.getInt(ConfigKeys.WS_STEP_Y, 2);
        String wsBypassPerm = c.getString(ConfigKeys.WS_BYPASS_PERMISSION, "leafinventory.workstation.bypass");

        int cleanupDays = c.getInt(ConfigKeys.CLEANUP_INACTIVE_DAYS, 30);
        int cleanupIntervalMin = c.getInt(ConfigKeys.CLEANUP_INTERVAL_MINUTES, 60);

        int cooldownTicks = Math.max(0, c.getInt(ConfigKeys.SHULKER_COOLDOWN_TICKS, 2));
        boolean blockOpenWhileEditing = c.getBoolean(ConfigKeys.SHULKER_BLOCK_OPEN_WHILE_EDITING, true);

        String lockModeRaw = c.getString(ConfigKeys.SHULKER_LOCK_MODE, "SLOT_ONLY");
        ShulkerLockMode lockMode = ShulkerLockMode.fromString(lockModeRaw);

        return new LeafConfig(
                enableShulkerbox,
                enableEnderChest,
                enableCraftingTable,
                enableSmithingTable,
                enableStoneCutter,
                enableGrindstone,
                enableCartographyTable,
                enableLoom,
                enableAnvil,
                enableEnchantingTable,
                enableFurnace,
                enableBlastFurnace,
                enableSmoker,
                usePermissions,
                wsWorldName,
                wsBaseChunkX,
                wsBaseChunkZ,
                wsBaseY,
                wsStepY,
                wsBypassPerm,
                cleanupDays,
                cleanupIntervalMin,
                cooldownTicks,
                blockOpenWhileEditing,
                lockMode
        );
    }
}

