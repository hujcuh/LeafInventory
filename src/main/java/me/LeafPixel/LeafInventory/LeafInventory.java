package me.LeafPixel.LeafInventory;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.LeafPixel.LeafInventory.enderchest.LargeEnderChestListener;
import me.LeafPixel.LeafInventory.enderchest.LargeEnderChestService;
import me.LeafPixel.LeafInventory.enderchest.PlacedEnderChestListener;
import me.LeafPixel.LeafInventory.largeshulker.LargeShulkerListener;
import me.LeafPixel.LeafInventory.largeshulker.LargeShulkerService;
import me.LeafPixel.LeafInventory.lastseen.LastSeenListener;
import me.LeafPixel.LeafInventory.lastseen.LastSeenManager;
import me.LeafPixel.LeafInventory.listener.MenuListener;
import me.LeafPixel.LeafInventory.listener.ShulkerListener;
import me.LeafPixel.LeafInventory.listener.WorkstationListener;
import me.LeafPixel.LeafInventory.menu.MenuService;
import me.LeafPixel.LeafInventory.shulker.ShulkerKeys;
import me.LeafPixel.LeafInventory.shulker.ShulkerService;
import me.LeafPixel.LeafInventory.workstation.FoliaVirtualWorkstationBackend;
import me.LeafPixel.LeafInventory.workstation.PortableWorkstationBackend;
import me.LeafPixel.LeafInventory.workstation.WorkstationCleanupTask;
import me.LeafPixel.LeafInventory.workstation.WorkstationGuardListener;
import me.LeafPixel.LeafInventory.largeshulker.LargeShulkerBlockListener;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin bootstrap.
 *
 * LeafInventory 4.x targets Paper/Folia 26.1.x.
 */
public final class LeafInventory extends JavaPlugin {

    /*
     * Core services
     */
    private LastSeenManager lastSeenManager;
    private MenuService menuService;

    /*
     * Regular shulker service
     */
    private ShulkerKeys shulkerKeys;
    private ShulkerService shulkerService;

    /*
     * 4.x virtual storage features
     */
    private LargeEnderChestService largeEnderChestService;
    private LargeShulkerService largeShulkerService;

    /*
     * Workstation backend
     */
    private PortableWorkstationBackend workstationBackend;
    private ScheduledTask workstationCleanupTask;

    @Override
    public void onEnable() {
        if (!isPaperServer()) {
            getLogger().severe("LeafInventory is Paper/Folia-only. Please run this plugin on Paper or Folia.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("Detected platform: " + (isFolia() ? "Folia" : "Paper"));

        setupConfig();
        initServices();
        registerListeners();
        startTasks();

        getLogger().info("LeafInventory enabled.");
    }

    @Override
    public void onDisable() {
        stopTasks();
        shutdownServices();
    }

    /*
     * ------------------------------------------------------------
     * Bootstrap
     * ------------------------------------------------------------
     */

    private void setupConfig() {
        saveDefaultConfig();

        FileConfiguration config = getConfig();
        applyDefaults(config);
        config.options().copyDefaults(true);

        saveConfig();
    }

    private void initServices() {
        FileConfiguration config = getConfig();

        lastSeenManager = new LastSeenManager(this);
        lastSeenManager.load();

        largeEnderChestService = new LargeEnderChestService(this, config);
        largeEnderChestService.load();

        largeShulkerService = new LargeShulkerService(this, config);
        largeShulkerService.load();

        shulkerKeys = new ShulkerKeys(this);
        shulkerService = new ShulkerService(this, shulkerKeys);

        menuService = new MenuService(this, config, largeEnderChestService);

        workstationBackend = new FoliaVirtualWorkstationBackend(this);
        workstationBackend.initFromConfig();
    }

    private void registerListeners() {
        FileConfiguration config = getConfig();
        PluginManager pluginManager = getServer().getPluginManager();

        pluginManager.registerEvents(
                new LastSeenListener(lastSeenManager),
                this
        );

        pluginManager.registerEvents(
                new MenuListener(this, menuService, shulkerService),
                this
        );

        pluginManager.registerEvents(
                new ShulkerListener(this, shulkerService, largeShulkerService),
                this
        );

        pluginManager.registerEvents(
                new LargeEnderChestListener(largeEnderChestService),
                this
        );
        pluginManager.registerEvents(
                new PlacedEnderChestListener(this, largeEnderChestService),
                this
        );
        pluginManager.registerEvents(
                new LargeShulkerListener(this, largeShulkerService),
                this
        );
        pluginManager.registerEvents(
                new LargeShulkerBlockListener(this, largeShulkerService),
                this
        );

        pluginManager.registerEvents(
                new WorkstationListener(
                        this,
                        workstationBackend,
                        config.getBoolean("usePermissions", false),
                        config.getBoolean("enableFurnace", true),
                        config.getBoolean("enableBlastFurnace", true),
                        config.getBoolean("enableSmoker", true)
                ),
                this
        );

        registerWorkstationGuardIfNeeded(pluginManager, config);
    }

    private void registerWorkstationGuardIfNeeded(PluginManager pluginManager, FileConfiguration config) {
        if (workstationBackend == null) {
            return;
        }

        World guardWorld = workstationBackend.getGuardWorld();
        if (guardWorld == null) {
            return;
        }

        String bypassPermission = config.getString(
                "workstation.bypassPermission",
                "leafinventory.workstation.bypass"
        );

        pluginManager.registerEvents(
                new WorkstationGuardListener(guardWorld, bypassPermission),
                this
        );
    }

    private void startTasks() {
        largeEnderChestService.startAutoSave();
        largeShulkerService.startAutoSave();
        startWorkstationCleanupTask();
    }

    private void stopTasks() {
        if (workstationCleanupTask != null) {
            workstationCleanupTask.cancel();
            workstationCleanupTask = null;
        }
    }

    private void shutdownServices() {
        /*
         * Save and close large shulker sessions before regular backend shutdown.
         */
        if (largeShulkerService != null) {
            largeShulkerService.shutdown();
            largeShulkerService = null;
        }

        /*
         * Save and close large ender chest sessions.
         */
        if (largeEnderChestService != null) {
            largeEnderChestService.shutdown();
            largeEnderChestService = null;
        }

        /*
         * Shutdown workstation backend.
         */
        if (workstationBackend != null) {
            workstationBackend.shutdown();
            workstationBackend = null;
        }

        /*
         * Save last-seen data last.
         */
        if (lastSeenManager != null) {
            lastSeenManager.save();
            lastSeenManager = null;
        }
    }

    /*
     * ------------------------------------------------------------
     * Scheduled tasks
     * ------------------------------------------------------------
     */

    private void startWorkstationCleanupTask() {
        FileConfiguration config = getConfig();

        int inactiveDays = config.getInt("cleanup.inactiveDays", 30);
        int intervalMinutes = Math.max(1, config.getInt("cleanup.intervalMinutes", 60));

        if (inactiveDays <= 0) {
            getLogger().info("Workstation cleanup is disabled because cleanup.inactiveDays <= 0.");
            return;
        }

        WorkstationCleanupTask cleanupTask = new WorkstationCleanupTask(
                this,
                workstationBackend,
                lastSeenManager,
                inactiveDays
        );

        long initialDelayTicks = 20L * 60L;
        long periodTicks = 20L * 60L * intervalMinutes;

        this.workstationCleanupTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                this,
                task -> cleanupTask.run(),
                initialDelayTicks,
                periodTicks
        );
    }

    /*
     * ------------------------------------------------------------
     * Config defaults
     * ------------------------------------------------------------
     */

    private void applyDefaults(FileConfiguration config) {
        /*
         * Global permission mode.
         */
        config.addDefault("usePermissions", false);

        /*
         * Regular portable containers.
         */
        config.addDefault("enableShulkerbox", true);
        config.addDefault("enableEnderChest", true);
        config.addDefault("enableCraftingTable", true);

        /*
         * Portable Paper menus.
         */
        config.addDefault("enableSmithingTable", true);
        config.addDefault("enableStoneCutter", true);
        config.addDefault("enableGrindstone", true);
        config.addDefault("enableCartographyTable", true);
        config.addDefault("enableLoom", true);
        config.addDefault("enableAnvil", false);
        config.addDefault("enableEnchantingTable", true);

        /*
         * Portable workstations.
         */
        config.addDefault("enableFurnace", true);
        config.addDefault("enableBlastFurnace", true);
        config.addDefault("enableSmoker", true);

        /*
         * Legacy hidden-world workstation settings.
         */
        config.addDefault("workstation.worldName", "leafinventory_workstations");
        config.addDefault("workstation.baseChunkX", 0);
        config.addDefault("workstation.baseChunkZ", 0);
        config.addDefault("workstation.baseY", 64);
        config.addDefault("workstation.stepY", 2);
        config.addDefault("workstation.bypassPermission", "leafinventory.workstation.bypass");

        /*
         * Cleanup settings.
         */
        config.addDefault("cleanup.inactiveDays", 30);
        config.addDefault("cleanup.intervalMinutes", 60);

        /*
         * Large ender chest.
         */
        config.addDefault("largeEnderChest.enabled", false);
        config.addDefault("largeEnderChest.permission", "leafinventory.enderchest.large");
        config.addDefault("largeEnderChest.rows", 6);
        config.addDefault("largeEnderChest.syncVanillaRows", true);
        config.addDefault("largeEnderChest.saveIntervalSeconds", 30);
        config.addDefault("largeEnderChest.title", "Large Ender Chest");

        /*
         * Large shulker box.
         */
        config.addDefault("largeShulker.enabled", false);
        config.addDefault("largeShulker.rows", 6);
        config.addDefault("largeShulker.permissions.create", "leafinventory.shulkerbox.large.create");
        config.addDefault("largeShulker.permissions.open", "leafinventory.shulkerbox.large.open");
        config.addDefault("largeShulker.allowOpenWithoutPermission", true);
        config.addDefault("largeShulker.requireOwnerToOpen", false);

        /*
         * Large shulker placement lifecycle.
         *
         * These options are reserved for the next implementation stage.
         */
        config.addDefault("largeShulker.placement.enabled", true);
        config.addDefault("largeShulker.placement.cancelVanillaOpen", true);
        config.addDefault("largeShulker.placement.preventPistonMove", true);
        config.addDefault("largeShulker.placement.handleExplosionDrop", true);
        config.addDefault("largeShulker.placement.blockHopperInteraction", true);
        config.addDefault("largeShulker.placement.convertDuplicatePlacedBlocksToVanilla", true);

        /*
         * Duplicate item handling.
         */
        config.addDefault("largeShulker.duplicateItemPolicy", "shared");
        config.addDefault("largeShulker.saveIntervalSeconds", 30);
        config.addDefault("largeShulker.title", "Large Shulker Box");
    }

    /*
     * ------------------------------------------------------------
     * Platform checks
     * ------------------------------------------------------------
     */

    private boolean isPaperServer() {
        try {
            Class.forName("org.bukkit.inventory.view.builder.InventoryViewBuilder");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    private boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
