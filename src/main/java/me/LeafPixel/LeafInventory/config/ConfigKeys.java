
package me.LeafPixel.LeafInventory.config;

/**
 * Centralized config keys for LeafInventory.
 *
 * Why:
 * - Avoid hard-coded strings scattered across the project.
 * - Reduce typos and make refactors safer.
 */
public final class ConfigKeys {

    private ConfigKeys() {
        // Utility class; no instances.
    }

    // --- Core feature toggles ---
    public static final String ENABLE_SHULKERBOX = "enableShulkerbox";
    public static final String ENABLE_ENDER_CHEST = "enableEnderChest";
    public static final String ENABLE_CRAFTING_TABLE = "enableCraftingTable";

    // --- Paper-only extra toggles (still configurable even if you later change behaviour) ---
    public static final String ENABLE_SMITHING_TABLE = "enableSmithingTable";
    public static final String ENABLE_STONECUTTER = "enableStoneCutter";
    public static final String ENABLE_GRINDSTONE = "enableGrindstone";
    public static final String ENABLE_CARTOGRAPHY_TABLE = "enableCartographyTable";
    public static final String ENABLE_LOOM = "enableLoom";
    public static final String ENABLE_ANVIL = "enableAnvil";
    public static final String ENABLE_ENCHANTING_TABLE = "enableEnchantingTable";

    // --- Workstation (virtual furnace area) toggles ---
    public static final String ENABLE_FURNACE = "enableFurnace";
    public static final String ENABLE_BLAST_FURNACE = "enableBlastFurnace";
    public static final String ENABLE_SMOKER = "enableSmoker";

    // --- Permissions ---
    public static final String USE_PERMISSIONS = "usePermissions";

    // --- Workstation world settings ---
    public static final String WS_WORLD_NAME = "workstation.worldName";
    public static final String WS_BASE_CHUNK_X = "workstation.baseChunkX";
    public static final String WS_BASE_CHUNK_Z = "workstation.baseChunkZ";
    public static final String WS_BASE_Y = "workstation.baseY";
    public static final String WS_STEP_Y = "workstation.stepY";
    public static final String WS_BYPASS_PERMISSION = "workstation.bypassPermission";

    // --- Cleanup settings ---
    public static final String CLEANUP_INACTIVE_DAYS = "cleanup.inactiveDays";
    public static final String CLEANUP_INTERVAL_MINUTES = "cleanup.intervalMinutes";

    // --- Shulker safety settings (recommended for route2) ---
    public static final String SHULKER_COOLDOWN_TICKS = "shulker.cooldownTicks";
    public static final String SHULKER_BLOCK_OPEN_WHILE_EDITING = "shulker.blockOpenWhileEditing";
    public static final String SHULKER_LOCK_MODE = "shulker.lockMode"; // "SLOT_ONLY" or "FULL_INVENTORY"
}
