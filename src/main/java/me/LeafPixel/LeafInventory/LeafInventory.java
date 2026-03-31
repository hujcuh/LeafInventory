package me.LeafPixel.LeafInventory;

import me.LeafPixel.LeafInventory.lastseen.LastSeenListener;
import me.LeafPixel.LeafInventory.lastseen.LastSeenManager;
import me.LeafPixel.LeafInventory.listener.MenuListener;
import me.LeafPixel.LeafInventory.listener.ShulkerListener;
import me.LeafPixel.LeafInventory.listener.WorkstationListener;
import me.LeafPixel.LeafInventory.menu.MenuService;
import me.LeafPixel.LeafInventory.shulker.ShulkerKeys;
import me.LeafPixel.LeafInventory.shulker.ShulkerService;
import me.LeafPixel.LeafInventory.util.Scheduler;
import me.LeafPixel.LeafInventory.workstation.FoliaVirtualWorkstationBackend;
import me.LeafPixel.LeafInventory.workstation.PortableWorkstationBackend;
import me.LeafPixel.LeafInventory.workstation.WorkstationCleanupTask;
import me.LeafPixel.LeafInventory.workstation.WorkstationGuardListener;
import me.LeafPixel.LeafInventory.workstation.WorkstationManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class (Paper/Folia aware bootstrap).
 */
public final class LeafInventory extends JavaPlugin {

    private PortableWorkstationBackend workstationBackend;
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
        boolean folia = isFolia();

        // 2) Initialize services
        ShulkerKeys shulkerKeys = new ShulkerKeys(this);
        this.shulkerService = new ShulkerService(this, shulkerKeys);
        this.menuService = new MenuService(this, config);

        // 3) Select workstation backend by platform
        if (folia) {
            this.workstationBackend = new FoliaVirtualWorkstationBackend(this);
            getLogger().info("Detected Folia. Using virtual workstation backend.");
        } else {
            this.workstationBackend = new WorkstationManager(this);
        }
        this.workstationBackend.initFromConfig();

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
                        workstationBackend,
                        usePermissions,
                        enableFurnace,
                        enableBlastFurnace,
                        enableSmoker
                ),
                this
        );

        String bypassPerm = config.getString("workstation.bypassPermission", "leafinventory.workstation.bypass");
        var guardWorld = workstationBackend.getGuardWorld();
        if (guardWorld != null) {
            pm.registerEvents(new WorkstationGuardListener(guardWorld, bypassPerm), this);
        }

        pm.registerEvents(new LastSeenListener(lastSeenManager), this);

        // 6) Cleanup task scheduling
        int inactiveDays = config.getInt("cleanup.inactiveDays", 30);
        int intervalMin = config.getInt("cleanup.intervalMinutes", 60);
        long periodTicks = Math.max(1, intervalMin) * 60L * 20L;

        Scheduler.runGlobalTimer(
                this,
                20L,
                periodTicks,
                new WorkstationCleanupTask(this, workstationBackend, lastSeenManager, inactiveDays)
        );

        getLogger().info("LeafInventory enabled" + (folia ? " (Folia detected)" : " (Paper mode)") + ".");
    }

    @Override
    public void onDisable() {
        if (workstationBackend != null) {
            workstationBackend.shutdown();
        }

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

        // Permission nodes
        config.addDefault("permissions.shulkerbox", "leafinventory.shulkerbox");

        // Shulker lock TTL (seconds). <=0 means disabled.
        config.addDefault("shulker.lockMaxAgeSeconds", 120);

        config.options().copyDefaults(true);
    }

    private boolean isPaperServer() {
        try {
            Class.forName("org.bukkit.inventory.view.builder.InventoryViewBuilder");
            return true;
        } catch (ClassNotFoundException ignored) {
        }
        return false;
    }

    private boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
        }
        return false;
    }
}
