package me.LeafPixel.LeafInventory.menu;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.builder.InventoryViewBuilder;
import org.bukkit.inventory.view.builder.LocationInventoryViewBuilder;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MenuService: opens vanilla-like menus using Paper's MenuType API.
 * It supports dynamic config reload by re-reading feature toggles from FileConfiguration.
 *
 * Note: MenuType API is experimental and may change in future Paper versions. [1](https://helpch.at/docs/1.21.1/org/bukkit/inventory/MenuType.Typed.html)[2](https://www.spigotmc.org/threads/menutype-api-and-how-to-use-it-1-21-1.662556/)
 */
public final class MenuService {

    private final JavaPlugin plugin;

    // Keep a reference for reload support (scheme B).
    private FileConfiguration config;

    // Permission gating is controlled by plugin config.
    private boolean usePermissions;

    // Feature toggles (cached; updated by reload()).
    private boolean enableEnderChest;
    private boolean enableCraftingTable;
    private boolean enableSmithingTable;
    private boolean enableStoneCutter;
    private boolean enableGrindstone;
    private boolean enableCartographyTable;
    private boolean enableLoom;
    private boolean enableAnvil;
    private boolean enableEnchantingTable;

    public MenuService(JavaPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        reload(config);
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    /**
     * Reload feature flags from config (scheme B).
     * Call this after plugin.reloadConfig() if you implement a reload command.
     */
    public void reload(FileConfiguration newConfig) {
        // Store the latest configuration reference.
        this.config = newConfig;

        // Read all toggles from config each reload.
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
     */
    public void openFromItem(Player player, Material type) {
        if (!isSupportedMenuItem(type)) return;

        if (usePermissions && !player.hasPermission(permissionOf(type))) return;

        switch (type) {
            case ENDER_CHEST -> toggleEnderChest(player);
            case CRAFTING_TABLE -> openMenuIfNotAlready(player, InventoryType.WORKBENCH, MenuType.CRAFTING);
            case ENCHANTING_TABLE -> openMenuIfNotAlready(player, InventoryType.ENCHANTING, MenuType.ENCHANTMENT);
            case STONECUTTER -> openMenuIfNotAlready(player, InventoryType.STONECUTTER, MenuType.STONECUTTER);
            case CARTOGRAPHY_TABLE -> openMenuIfNotAlready(player, InventoryType.CARTOGRAPHY, MenuType.CARTOGRAPHY_TABLE);
            case LOOM -> openMenuIfNotAlready(player, InventoryType.LOOM, MenuType.LOOM);
            case SMITHING_TABLE -> openMenuIfNotAlready(player, InventoryType.SMITHING, MenuType.SMITHING);
            case GRINDSTONE -> openMenuIfNotAlready(player, InventoryType.GRINDSTONE, MenuType.GRINDSTONE);
            case ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL -> openMenuIfNotAlready(player, InventoryType.ANVIL, MenuType.ANVIL);
            default -> { /* ignore */ }
        }
    }

    private void toggleEnderChest(Player player) {
        if (player.getOpenInventory() != null
                && player.getOpenInventory().getTopInventory().getType() == InventoryType.ENDER_CHEST) {
            player.closeInventory();
            player.playSound(player, Sound.BLOCK_ENDER_CHEST_CLOSE, SoundCategory.BLOCKS, 1.0f, 1.2f);
        } else {
            player.openInventory(player.getEnderChest());
            player.playSound(player, Sound.BLOCK_ENDER_CHEST_OPEN, SoundCategory.BLOCKS, 1.0f, 1.2f);
        }
    }

    /**
     * Opens a MenuType view using builders ONLY.
     * We avoid MenuType.Typed#create(...) because it is deprecated since 1.21. [1](https://helpch.at/docs/1.21.1/org/bukkit/inventory/MenuType.Typed.html)[2](https://www.spigotmc.org/threads/menutype-api-and-how-to-use-it-1-21-1.662556/)
     */
    private void openMenuIfNotAlready(
            HumanEntity player,
            InventoryType legacyType,
            MenuType.Typed<? extends InventoryView, ? extends InventoryViewBuilder<? extends InventoryView>> menuType
    ) {
        if (player.getOpenInventory() != null
                && player.getOpenInventory().getTopInventory().getType() == legacyType) {
            return;
        }

        Location loc = player.getLocation();

        // Prefer builder-based creation (recommended by MenuType API docs). [1](https://helpch.at/docs/1.21.1/org/bukkit/inventory/MenuType.Typed.html)[2](https://www.spigotmc.org/threads/menutype-api-and-how-to-use-it-1-21-1.662556/)
        InventoryViewBuilder<? extends InventoryView> builder = menuType.builder();

        if (builder instanceof LocationInventoryViewBuilder<?> locBuilder) {
            // Use player's location; do not require reachability for "portable" menus.
            locBuilder.location(loc).checkReachable(false);
            InventoryView view = ((LocationInventoryViewBuilder<?>) locBuilder).build(player);
            player.openInventory(view);
            return;
        }

        // Non-location builders can still build a view for the player.
        InventoryView view = builder.build(player);
        player.openInventory(view);
    }
}
