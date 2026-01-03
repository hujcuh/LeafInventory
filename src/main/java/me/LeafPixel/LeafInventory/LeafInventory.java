
package me.LeafPixel.LeafInventory;

import me.LeafPixel.LeafInventory.config.LeafConfig;
import me.LeafPixel.LeafInventory.lastseen.LastSeenListener;
import me.LeafPixel.LeafInventory.lastseen.LastSeenManager;
import me.LeafPixel.LeafInventory.listener.MenuItemListener;
import me.LeafPixel.LeafInventory.listener.ShulkerGuardListener;
import me.LeafPixel.LeafInventory.listener.ShulkerListener;
import me.LeafPixel.LeafInventory.shulker.ShulkerSessionManager;
import me.LeafPixel.LeafInventory.workstation.WorkstationCleanupTask;
import me.LeafPixel.LeafInventory.workstation.WorkstationGuardListener;
import me.LeafPixel.LeafInventory.workstation.WorkstationManager;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Route-2 main plugin class.
 *
 * Key changes from the old version:
 * - Paper-only (1.21.1) code path: no more legacy listener selection.
 * - Listeners are split by responsibility: menu items, shulker session, shulker guard.
 * - Config is loaded once into an immutable snapshot (LeafConfig).
 * - Added onDisable() save for lastSeen for better data safety.
 */
public final class LeafInventory extends JavaPlugin {

    private LeafConfig runtimeConfig;
    private WorkstationManager workstationManager;
    private LastSeenManager lastSeenManager;
    private ShulkerSessionManager shulkerSessions;

    @Override
    public void onEnable() {
        // ---- Paper-only assumption (you decided to only support Paper) ----
        // You can keep this detection for logging; it should always be true on Paper.
        boolean isPaper = false;
        try {
            Class.forName("com.destroystokyo.paper.utils.PaperPluginLogger");
            isPaper = true;
            getLogger().info("Paper detected: enabling Paper-only features.");
        } catch (ClassNotFoundException ignored) {
            getLogger().warning("Paper classes not found. This build is intended for Paper 1.21.1.");
        }

        // ---- Apply defaults and load config snapshot (route2) ----
        // Old code used config.addDefault(...) directly in this class. (Still OK, but now centralized.)
        LeafConfig.applyDefaults(this, isPaper);
        this.runtimeConfig = LeafConfig.load(this, isPaper);

        PluginManager pm = getServer().getPluginManager();

        // ---- Workstation + lastseen (keep existing behavior) ----
        // Old code initialized workstation only when isPaper == true. (We keep the same behavior.)
        if (isPaper) {
            this.workstationManager = new WorkstationManager(this);
            workstationManager.initFromConfig();

            String bypassPerm = getConfig().getString(
                    "workstation.bypassPermission",
                    "leafinventory.workstation.bypass"
            );
            pm.registerEvents(new WorkstationGuardListener(workstationManager.getWorld(), bypassPerm), this);

            this.lastSeenManager = new LastSeenManager(this);
            lastSeenManager.load();
            pm.registerEvents(new LastSeenListener(lastSeenManager), this);

            int days = getConfig().getInt("cleanup.inactiveDays", 30);
            int intervalMin = getConfig().getInt("cleanup.intervalMinutes", 60);
            long periodTicks = intervalMin * 60L * 20L;

            getServer().getScheduler().runTaskTimer(
                    this,
                    new WorkstationCleanupTask(this, workstationManager, lastSeenManager, days),
                    20L,
                    periodTicks
            );
        }

        // ---- Shulker session manager (route2 core safety) ----
        // Sessions handle token verification + commit/rollback safely.
        this.shulkerSessions = new ShulkerSessionManager(this, runtimeConfig);

        // ---- Register route2 listeners ----
        // 1) Shulker open/close/force-close logic
        pm.registerEvents(new ShulkerListener(this, runtimeConfig, shulkerSessions), this);

        // 2) Shulker guard: blocks your repro case (right-click another shulker while UI open),
        //    and blocks dangerous actions during editing
        pm.registerEvents(new ShulkerGuardListener(runtimeConfig, shulkerSessions), this);

        // 3) Menu items: keep all your "open menu by right-click in inventory or right-click air" features
        // IMPORTANT: This assumes MenuItemListener constructor is (JavaPlugin, LeafConfig, WorkstationManager).
        // If yours is currently (LeafConfig, WorkstationManager), just update it to accept plugin too.
        pm.registerEvents(new MenuItemListener(this, runtimeConfig, workstationManager), this);

        getLogger().info("LeafInventory route2 enabled. Shulker session safety is active.");
    }

    @Override
    public void onDisable() {
        // Best-effort persistence: avoid losing lastSeen updates on abrupt shutdown.
        if (lastSeenManager != null) {
            lastSeenManager.save();
        }
        getLogger().info("LeafInventory disabled.");
    }

    // Optional getters if other classes need access
    public LeafConfig getRuntimeConfig() {
        return runtimeConfig;
    }

    public WorkstationManager getWorkstationManager() {
        return workstationManager;
    }

    public ShulkerSessionManager getShulkerSessions() {
        return shulkerSessions;
    }
}
