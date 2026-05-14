package me.LeafPixel.LeafInventory;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import me.LeafPixel.LeafInventory.enderchest.LargeEnderChestListener;
import me.LeafPixel.LeafInventory.enderchest.LargeEnderChestService;
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
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main plugin class.
 *
 * LeafInventory 4.x targets Paper/Folia 26.1.x.
 */
public final class LeafInventory extends JavaPlugin {

    private LastSeenManager lastSeenManager;

    private ShulkerKeys shulkerKeys;
    private ShulkerService shulkerService;

    private LargeEnderChestService largeEnderChestService;
    private MenuService menuService;

    private PortableWorkstationBackend workstationBackend;
    private ScheduledTask workstationCleanupTask;

    @Override
    public void onEnable() {
        if (!isPaperServer()) {
            getLogger().severe("LeafInventory is Paper/Folia-only. Please run this plugin on Paper or Folia.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();

        FileConfiguration config = getConfig();
        applyDefaults(config);
        config.options().copyDefaults(true);
        saveConfig();

        this.lastSeenManager = new LastSeenManager(this);
        this.lastSeenManager.load();

        this.shulkerKeys = new ShulkerKeys(this);
        this.shulkerService = new ShulkerService(this, shulkerKeys);

        this.largeEnderChestService = new LargeEnderChestService(this, getConfig());
        this.largeEnderChestService.load();
        this.largeEnderChestService.startAutoSave();

        this.menuService = new MenuService(this, getConfig(), largeEnderChestService);

        this.workstationBackend = new FoliaVirtualWorkstationBackend(this);
        this.workstationBackend.initFromConfig();

        registerListeners();
        startWorkstationCleanupTask();

        getLogger().info("LeafInventory enabled. Folia=" + isFolia());
    }

    @Override
    public void onDisable() {
        if (workstationCleanupTask != null) {
            workstationCleanupTask.cancel();
            workstationCleanupTask = null;
        }

        if (largeEnderChestService != null) {
            largeEnderChestService.shutdown();
        }

        if (workstationBackend != null) {
            workstationBackend.shutdown();
        }

        if (lastSeenManager != null) {
            lastSeenManager.save();
        }
    }

    private void registerListeners() {
        FileConfiguration config = getConfig();
        PluginManager pm = getServer().getPluginManager();

        boolean usePermissions = config.getBoolean("usePermissions", false);

        pm.registerEvents(new LastSeenListener(lastSeenManager), this);

        pm.registerEvents(
                new ShulkerListener(
                        this,
                        shulkerService,
                        config.getBoolean("enableShulkerbox", true),
                        usePermissions,
                        "leafinventory.shulkerbox"
                ),
                this
        );

        pm.registerEvents(new LargeEnderChestListener(largeEnderChestService), this);

        pm.registerEvents(new MenuListener(this, menuService, shulkerService), this);

        pm.registerEvents(
                new WorkstationListener(
                        this,
                        workstationBackend,
                        usePermissions,
                        config.getBoolean("enableFurnace", true),
                        config.getBoolean("enableBlastFurnace", true),
                        config.getBoolean("enableSmoker", true)
                ),
                this
        );

        World guardWorld = workstationBackend.getGuardWorld();

        if (guardWorld != null) {
            String bypassPermission = config.getString(
                    "workstation.bypassPermission",
                    "leafinventory.workstation.bypass"
            );

            pm.registerEvents(new WorkstationGuardListener(guardWorld, bypassPermission), this);
        }
    }

    private void startWorkstationCleanupTask() {
        FileConfiguration config = getConfig();

        int inactiveDays = config.getInt("cleanup.inactiveDays", 30);
        int intervalMinutes = Math.max(1, config.getInt("cleanup.intervalMinutes", 60));

        if (inactiveDays <= 0) {
            getLogger().info("Workstation cleanup is disabled because cleanup.inactiveDays <= 0.");
            return;
        }

        WorkstationCleanupTask cleanup = new WorkstationCleanupTask(
                this,
                workstationBackend,
                lastSeenManager,
                inactiveDays
        );

        long initialDelayTicks = 20L * 60L;
        long periodTicks = 20L * 60L * intervalMinutes;

        this.workstationCleanupTask = Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                this,
                task -> cleanup.run(),
                initialDelayTicks,
                periodTicks
        );
    }

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

        config.addDefault("workstation.worldName", "leafinventory_workstations");
        config.addDefault("workstation.baseChunkX", 0);
        config.addDefault("workstation.baseChunkZ", 0);
        config.addDefault("workstation.baseY", 64);
        config.addDefault("workstation.stepY", 2);
        config.addDefault("workstation.bypassPermission", "leafinventory.workstation.bypass");

        config.addDefault("cleanup.inactiveDays", 30);
        config.addDefault("cleanup.intervalMinutes", 60);

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

        config.addDefault("largeShulker.placement.enabled", true);
        config.addDefault("largeShulker.placement.cancelVanillaOpen", true);
        config.addDefault("largeShulker.placement.preventPistonMove", true);
        config.addDefault("largeShulker.placement.handleExplosionDrop", true);
        config.addDefault("largeShulker.placement.blockHopperInteraction", true);
        config.addDefault("largeShulker.placement.convertDuplicatePlacedBlocksToVanilla", true);

        config.addDefault("largeShulker.duplicateItemPolicy", "shared");
        config.addDefault("largeShulker.saveIntervalSeconds", 30);
        config.addDefault("largeShulker.title", "Large Shulker Box");
    }

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
