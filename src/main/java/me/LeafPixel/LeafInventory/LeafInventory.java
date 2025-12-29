package me.LeafPixel.LeafInventory;


import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class LeafInventory extends JavaPlugin {


    @Override
    public void onEnable() {
        FileConfiguration config = getConfig();
        boolean isPaper = false;
        
        try {
            Class.forName("com.destroystokyo.paper.utils.PaperPluginLogger");
            isPaper = true;
            getLogger().info("You are running PaperMC, some extra features are enabled");
        } catch (ClassNotFoundException e) {
            getLogger().info("You are not running PaperMC");
        }

        PluginManager pm = getServer().getPluginManager();

        
        config.addDefault("enableShulkerbox", true);
        config.addDefault("enableEnderChest", true);
        config.addDefault("enableCraftingTable", true);

        
        if (isPaper) {
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
        }

        config.addDefault("usePermissions", false);
        config.options().copyDefaults(true);
        saveConfig();

        WorkstationManager ws = null;
        LastSeenManager lastSeen = null;

        if (isPaper) {
            ws = new WorkstationManager(this);
            ws.initFromConfig();

            String bypassPerm = config.getString("workstation.bypassPermission", "leafinventory.workstation.bypass");
            pm.registerEvents(new WorkstationGuardListener(ws.getWorld(), bypassPerm), this);

            lastSeen = new LastSeenManager(this);
            lastSeen.load();

            
            pm.registerEvents(new LastSeenListener(lastSeen), this);

            int days = config.getInt("cleanup.inactiveDays", 30);
            int intervalMin = config.getInt("cleanup.intervalMinutes", 60);
            long periodTicks = intervalMin * 60L * 20L;

            
            getServer().getScheduler().runTaskTimer(
                    this,
                    new WorkstationCleanupTask(this, ws, lastSeen, days),
                    20L,
                    periodTicks
            );
        }

        

        boolean hasViewBuilder;
        try {
            Class.forName("org.bukkit.inventory.view.builder.InventoryViewBuilder");
            hasViewBuilder = true;
        } catch (ClassNotFoundException ex) {
            hasViewBuilder = false;
        }

        if (hasViewBuilder) {
            
            pm.registerEvents(new InventoryListener(this, config, isPaper, ws), this);
        } else {
            
            pm.registerEvents(new InventoryListenerLegacy(this, config, ws), this);
        }

    }

}
