package me.LeafPixel.LeafInventory.menu;

import me.LeafPixel.LeafInventory.enderchest.LargeEnderChestService;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.MenuType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MenuService opens vanilla-like menus using Paper's MenuType API.
 *
 * 4.x integrates LargeEnderChestService:
 * - If large ender chest is enabled and the player has permission,
 *   ENDER_CHEST opens a 54-slot virtual GUI.
 * - Otherwise it falls back to the vanilla 27-slot ender chest.
 */
public final class MenuService {

    private final JavaPlugin plugin;
    private final LargeEnderChestService largeEnderChest;

    private FileConfiguration config;

    private boolean usePermissions;

    private boolean enableEnderChest;
    private boolean enableCraftingTable;
    private boolean enableSmithingTable;
    private boolean enableStoneCutter;
    private boolean enableGrindstone;
    private boolean enableCartographyTable;
    private boolean enableLoom;
    private boolean enableAnvil;
    private boolean enableEnchantingTable;

    public MenuService(JavaPlugin plugin, FileConfiguration config, LargeEnderChestService largeEnderChest) {
        this.plugin = plugin;
        this.largeEnderChest = largeEnderChest;
        reload(config);
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    /**
     * Reload feature flags from config.
     */
    public void reload(FileConfiguration newConfig) {
        this.config = newConfig;

        this.usePermissions = config.getBoolean("usePermissions", false);

        this.enableEnderChest = config.getBoolean("enableEnderChest", true);
        this.enableCraftingTable = config.getBoolean("enableCraftingTable", true);
        this.enableSmithingTable = config.getBoolean("enableSmithingTable", true);
        this.enableStoneCutter = config.getBoolean("enableStoneCutter", true);
        this.enableGrindstone = config.getBoolean("enableGrindstone", true);
        this.enableCartographyTable = config.getBoolean("enableCartographyTable", true);
        this.enableLoom = config.getBoolean("enableLoom", true);
        this.enableAnvil = config.getBoolean("enableAnvil", false);
        this.enableEnchantingTable = config.getBoolean("enableEnchantingTable", true);

        if (largeEnderChest != null) {
            largeEnderChest.reload(newConfig);
        }
    }

    public boolean isSupportedMenuItem(Material type) {
        return switch (type) {
            case ENDER_CHEST -> enableEnderChest;
            case CRAFTING_TABLE -> enableCraftingTable;
            case ENCHANTING_TABLE -> enableEnchantingTable;
            case STONECUTTER -> enableStoneCutter;
            case CARTOGRAPHY_TABLE -> enableCartographyTable;
            case LOOM -> enableLoom;
            case SMITHING_TABLE -> enableSmithingTable;
            case GRINDSTONE -> enableGrindstone;
            case ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL -> enableAnvil;
            default -> false;
        };
    }

    public String permissionOf(Material type) {
        return switch (type) {
            case ENDER_CHEST -> "leafinventory.enderchest";
            case CRAFTING_TABLE -> "leafinventory.craftingtable";
            case ENCHANTING_TABLE -> "leafinventory.enchantingtable";
            case STONECUTTER -> "leafinventory.stonecutter";
            case CARTOGRAPHY_TABLE -> "leafinventory.cartographytable";
            case LOOM -> "leafinventory.loom";
            case SMITHING_TABLE -> "leafinventory.smithingtable";
            case GRINDSTONE -> "leafinventory.grindstone";
            case ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL -> "leafinventory.anvil";
            default -> "leafinventory.menu";
        };
    }

    /**
     * Entry point for MenuListener.
     *
     * This method must be called from the player's entity scheduler.
     */
    public void openFromItem(Player player, Material type) {
        if (!isSupportedMenuItem(type)) {
            return;
        }

        if (usePermissions && !player.hasPermission(permissionOf(type))) {
            return;
        }

        switch (type) {
            case ENDER_CHEST -> openEnderChest(player);

            case CRAFTING_TABLE -> openMenuIfNotAlready(
                    player,
                    InventoryType.WORKBENCH,
                    MenuType.CRAFTING
            );

            case ENCHANTING_TABLE -> openMenuIfNotAlready(
                    player,
                    InventoryType.ENCHANTING,
                    MenuType.ENCHANTMENT
            );

            case STONECUTTER -> openMenuIfNotAlready(
                    player,
                    InventoryType.STONECUTTER,
                    MenuType.STONECUTTER
            );

            case CARTOGRAPHY_TABLE -> openMenuIfNotAlready(
                    player,
                    InventoryType.CARTOGRAPHY,
                    MenuType.CARTOGRAPHY_TABLE
            );

            case LOOM -> openMenuIfNotAlready(
                    player,
                    InventoryType.LOOM,
                    MenuType.LOOM
            );

            case SMITHING_TABLE -> openMenuIfNotAlready(
                    player,
                    InventoryType.SMITHING,
                    MenuType.SMITHING
            );

            case GRINDSTONE -> openMenuIfNotAlready(
                    player,
                    InventoryType.GRINDSTONE,
                    MenuType.GRINDSTONE
            );

            case ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL -> openMenuIfNotAlready(
                    player,
                    InventoryType.ANVIL,
                    MenuType.ANVIL
            );

            default -> {
                // ignored
            }
        }
    }

    private void openEnderChest(Player player) {
        if (largeEnderChest != null) {
            largeEnderChest.openOrFallback(player);
            return;
        }

        toggleVanillaEnderChest(player);
    }

    private void toggleVanillaEnderChest(Player player) {
        if (player.getOpenInventory() != null
                && player.getOpenInventory().getTopInventory().getType() == InventoryType.ENDER_CHEST) {
            player.closeInventory();
            playEnderChestClose(player);
            return;
        }

        player.openInventory(player.getEnderChest());
        playEnderChestOpen(player);
    }

    /**
     * Opens a vanilla menu if the player is not already viewing that menu type.
     *
     * If this method causes a MenuType generic/API error on 26.1.x,
     * send me the compile error and I will switch this to the builder-based API.
     */
    private void openMenuIfNotAlready(
            HumanEntity player,
            InventoryType legacyType,
            MenuType.Typed<? extends InventoryView, ?> menuType
    ) {
        if (player.getOpenInventory() != null
                && player.getOpenInventory().getTopInventory().getType() == legacyType) {
            return;
        }

        InventoryView view = menuType.create(player);

        if (view == null) {
            plugin.getLogger().warning("Failed to create menu view for type: " + legacyType);
            return;
        }

        player.openInventory(view);
    }

    private static void playEnderChestOpen(Player player) {
        player.playSound(
                player,
                Sound.BLOCK_ENDER_CHEST_OPEN,
                SoundCategory.BLOCKS,
                1.0f,
                1.2f
        );
    }

    @SuppressWarnings("null")
    private static void playEnderChestClose(Player player) {
        player.playSound(
                player,
                Sound.BLOCK_ENDER_CHEST_CLOSE,
                SoundCategory.BLOCKS,
                1.0f,
                1.2f
        );
    }
}
