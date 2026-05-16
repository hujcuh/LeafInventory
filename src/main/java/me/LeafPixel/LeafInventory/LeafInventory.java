package me.LeafPixel.LeafInventory;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.LeafPixel.LeafInventory.command.LeafInventoryCommand;
import me.LeafPixel.LeafInventory.enderchest.LargeEnderChestListener;
import me.LeafPixel.LeafInventory.enderchest.LargeEnderChestService;
import me.LeafPixel.LeafInventory.enderchest.PlacedEnderChestListener;
import me.LeafPixel.LeafInventory.largeshulker.LargeShulkerBlockListener;
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
import me.LeafPixel.LeafInventory.util.Scheduler;
import me.LeafPixel.LeafInventory.workstation.FoliaVirtualWorkstationBackend;
import me.LeafPixel.LeafInventory.workstation.PortableWorkstationBackend;
import me.LeafPixel.LeafInventory.workstation.WorkstationCleanupTask;
import me.LeafPixel.LeafInventory.workstation.WorkstationGuardListener;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
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
        registerCommands();
        startTasks();

        getLogger().info("LeafInventory enabled.");
    }

    @Override
    public void onDisable() {
        stopTasks();
        shutdownServices();
    }

    /*
     * Bootstrap
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

    private void registerCommands() {
        LeafInventoryCommand command = new LeafInventoryCommand(
                this,
                largeEnderChestService,
                largeShulkerService
        );

        PluginCommand pluginCommand = getCommand("leafinventory");

        if (pluginCommand == null) {
            getLogger().warning("Command 'leafinventory' is not defined in plugin.yml.");
            return;
        }

        pluginCommand.setExecutor(command);
        pluginCommand.setTabCompleter(command);
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
        if (largeEnderChestService != null) {
            largeEnderChestService.startAutoSave();
        }

        if (largeShulkerService != null) {
            largeShulkerService.startAutoSave();
        }

        startWorkstationCleanupTask();
    }

    private void stopTasks() {
        if (workstationCleanupTask != null) {
            workstationCleanupTask.cancel();
            workstationCleanupTask = null;
        }

        if (largeEnderChestService != null) {
            largeEnderChestService.stopAutoSave();
        }

        if (largeShulkerService != null) {
            largeShulkerService.stopAutoSave();
        }
    }

    private void shutdownServices() {
        if (largeShulkerService != null) {
            largeShulkerService.shutdown();
            largeShulkerService = null;
        }

        if (largeEnderChestService != null) {
            largeEnderChestService.shutdown();
            largeEnderChestService = null;
        }

        if (workstationBackend != null) {
            workstationBackend.shutdown();
            workstationBackend = null;
        }

        if (lastSeenManager != null) {
            lastSeenManager.save();
            lastSeenManager = null;
        }
    }

    /*
     * Scheduled tasks
     */
    private void startWorkstationCleanupTask() {
        FileConfiguration config = getConfig();

        int inactiveDays = config.getInt("workstation.cleanup.inactiveDays", 30);
        int intervalMinutes = config.getInt("workstation.cleanup.intervalMinutes", 60);

        if (inactiveDays <= 0 || intervalMinutes <= 0) {
            return;
        }

        workstationCleanupTask = Scheduler.runGlobalTimer(
                this,
                20L * 60L,
                20L * 60L * intervalMinutes,
                new WorkstationCleanupTask(
                        this,
                        workstationBackend,
                        lastSeenManager,
                        inactiveDays
                )
        );
    }

    /*
     * Config defaults
     */
    private void applyDefaults(FileConfiguration config) {
        config.addDefault("usePermissions", false);

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

        config.addDefault("enableFurnace", true);
        config.addDefault("enableBlastFurnace", true);
        config.addDefault("enableSmoker", true);

        config.addDefault("workstation.bypassPermission", "leafinventory.workstation.bypass");
        config.addDefault("workstation.cleanup.inactiveDays", 30);
        config.addDefault("workstation.cleanup.intervalMinutes", 60);

        config.addDefault("largeEnderChest.enabled", false);
        config.addDefault("largeEnderChest.permission", "leafinventory.enderchest.large");
        config.addDefault("largeEnderChest.rows", 6);
        config.addDefault("largeEnderChest.syncVanillaRows", true);
        config.addDefault("largeEnderChest.saveIntervalSeconds", 30);
        config.addDefault("largeEnderChest.title", "Large Ender Chest");

        config.addDefault("largeShulker.enabled", false);
        config.addDefault("largeShulker.rows", 6);
        config.addDefault("largeShulker.permissions.create", "leafinventory.shulkerbox.large.create");
        config.addDefault("largeShulker.permissions.open", "leafinventory.shulkerbox.large.open");
        config.addDefault("largeShulker.allowOpenWithoutPermission", true);
        config.addDefault("largeShulker.requireOwnerToOpen", false);
        config.addDefault("largeShulker.saveIntervalSeconds", 30);
        config.addDefault("largeShulker.title", "Large Shulker Box");

        config.addDefault("largeShulker.placement.enabled", true);
        config.addDefault("largeShulker.placement.cancelVanillaOpen", true);
        config.addDefault("largeShulker.placement.preventPistonMove", true);
        config.addDefault("largeShulker.placement.pistonDropInsteadOfCancel", true);
        config.addDefault("largeShulker.placement.handleExplosionDrop", true);
        config.addDefault("largeShulker.placement.blockHopperInteraction", true);
        config.addDefault("largeShulker.placement.convertDuplicatePlacedBlocksToVanilla", true);
    }

    /*
     * Platform checks
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
