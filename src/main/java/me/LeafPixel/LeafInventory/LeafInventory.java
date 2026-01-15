package me.LeafPixel.LeafInventory;

import me.LeafPixel.LeafInventory.listener.MenuListener;
import me.LeafPixel.LeafInventory.listener.ShulkerListener;
import me.LeafPixel.LeafInventory.listener.WorkstationListener;
import me.LeafPixel.LeafInventory.lastseen.LastSeenListener;
import me.LeafPixel.LeafInventory.lastseen.LastSeenManager;
import me.LeafPixel.LeafInventory.menu.MenuService;
import me.LeafPixel.LeafInventory.shulker.ShulkerKeys;
import me.LeafPixel.LeafInventory.shulker.ShulkerService;
import me.LeafPixel.LeafInventory.workstation.WorkstationCleanupTask;
import me.LeafPixel.LeafInventory.workstation.WorkstationGuardListener;
import me.LeafPixel.LeafInventory.workstation.WorkstationManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class (Paper-only).
 */
public final class LeafInventory extends JavaPlugin {

    private WorkstationManager workstationManager;
    private LastSeenManager lastSeenManager;
    private ShulkerService shulkerService;
    private MenuService menuService;

    @Override
    public void onEnable() {
        if (!isPaperServer()) {
            getLogger().severe("LeafInventory is Paper-only. Please run this plugin on Paper.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // 1) Config defaults
        FileConfiguration config = getConfig();
        applyDefaults(config);
        saveConfig();

        boolean usePermissions = config.getBoolean("usePermissions", false);

        // 2) Initialize services
        ShulkerKeys shulkerKeys = new ShulkerKeys(this);
        this.shulkerService = new ShulkerService(this, shulkerKeys);

        this.menuService = new MenuService(this, config);

        // 3) Workstation system
        this.workstationManager = new WorkstationManager(this);
        this.workstationManager.initFromConfig();

        // 4) LastSeen manager for cleanup
        this.lastSeenManager = new LastSeenManager(this);
        this.lastSeenManager.load();

        // 5) Register listeners
        PluginManager pm = getServer().getPluginManager();

        boolean enableShulker = config.getBoolean("enableShulkerbox", true);
        String shulkerPerm = config.getString("permissions.shulkerbox", "leafinventory.shulkerbox");
        pm.registerEvents(
                new ShulkerListener(this, shulkerService, enableShulker, usePermissions, shulkerPerm),
                this
        );

        pm.registerEvents(new MenuListener(this, menuService, shulkerService), this);

        boolean enableFurnace = config.getBoolean("enableFurnace", true);
        boolean enableBlastFurnace = config.getBoolean("enableBlastFurnace", true);
        boolean enableSmoker = config.getBoolean("enableSmoker", true);
        pm.registerEvents(
                new WorkstationListener(
                        this,
                        workstationManager,
                        usePermissions,
                        enableFurnace,
                        enableBlastFurnace,
                        enableSmoker
                ),
                this
        );

        String bypassPerm = config.getString("workstation.bypassPermission", "leafinventory.workstation.bypass");
        pm.registerEvents(new WorkstationGuardListener(workstationManager.getWorld(), bypassPerm), this);

        pm.registerEvents(new LastSeenListener(lastSeenManager), this);

        // 6) Cleanup task scheduling
        int inactiveDays = config.getInt("cleanup.inactiveDays", 30);
        int intervalMin = config.getInt("cleanup.intervalMinutes", 60);
        long periodTicks = Math.max(1, intervalMin) * 60L * 20L;

        Bukkit.getScheduler().runTaskTimer(
                this,
                new WorkstationCleanupTask(this, workstationManager, lastSeenManager, inactiveDays),
                20L,
                periodTicks
        );

        getLogger().info("LeafInventory enabled (Paper-only).");
    }

    @Override
    public void onDisable() {
        if (lastSeenManager != null) {
            lastSeenManager.save();
        }
        getLogger().info("LeafInventory disabled.");
    }

    private void applyDefaults(FileConfiguration config) {
        // Feature toggles
        config.addDefault("enableShulkerbox", true);
        config.addDefault("enableEnderChest", true);
        config.addDefault("enableCraftingTable", true);
        config.addDefault("enableSmithingTable", true);
        config.addDefault("enableStoneCutter", true);
        config.addDefault("enableGrindstone", true);
        config.addDefault("enableCartographyTable", true);
        config.addDefault("enableLoom", true);
        config.addDefault("enableAnvil", false);
        config.addDefault("enableEnchantingTable", true);

        // Workstation-backed containers
        config.addDefault("enableFurnace", true);
        config.addDefault("enableBlastFurnace", true);
        config.addDefault("enableSmoker", true);

        // Workstation world settings
        config.addDefault("workstation.worldName", "leafinventory_workstations");
        config.addDefault("workstation.baseChunkX", 0);
        config.addDefault("workstation.baseChunkZ", 0);
        config.addDefault("workstation.baseY", 64);
        config.addDefault("workstation.stepY", 2);
        config.addDefault("workstation.bypassPermission", "leafinventory.workstation.bypass");

        // Cleanup settings
        config.addDefault("cleanup.inactiveDays", 30);
        config.addDefault("cleanup.intervalMinutes", 60);

        // Permission gating
        config.addDefault("usePermissions", false);

        // Permission nodes (集中配置，方便你后面扩展)
        config.addDefault("permissions.shulkerbox", "leafinventory.shulkerbox");

        // Shulker lock TTL (秒). <=0 表示不启用 TTL，自行手动处理
        config.addDefault("shulker.lockMaxAgeSeconds", 120);

        config.options().copyDefaults(true);
    }

    private boolean isPaperServer() {
        try {
            Class.forName("org.bukkit.inventory.view.builder.InventoryViewBuilder");
            return true;
        } catch (ClassNotFoundException ignored) { }
        return false;
    }
}
