package me.LeafPixel.LeafInventory.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Scheduler utility for Paper/Folia compatible task execution.
 * - Entity scheduler: for player/entity related logic.
 * - Region scheduler: for block/world/location related logic.
 * - Global scheduler: for plugin-wide periodic tasks.
 * - Async scheduler: for true async work (do NOT touch Bukkit world state there).
 */
public final class Scheduler {
    private Scheduler() {
        // Utility class
    }

    /**
     * Run on the entity's scheduler immediately.
     */
    public static void runEntity(JavaPlugin plugin, Entity entity, Runnable task) {
        entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
    }

    /**
     * Run on the entity's scheduler after delay ticks.
     */
    public static void runEntityLater(JavaPlugin plugin, Entity entity, long delayTicks, Runnable task) {
        entity.getScheduler().runDelayed(plugin, scheduledTask -> task.run(), null, delayTicks);
    }

    /**
     * Run on the region owning the location.
     */
    public static void runRegion(JavaPlugin plugin, Location location, Runnable task) {
        Bukkit.getRegionScheduler().run(plugin, location, scheduledTask -> task.run());
    }

    /**
     * Run on the region owning the location after delay ticks.
     */
    public static void runRegionLater(JavaPlugin plugin, Location location, long delayTicks, Runnable task) {
        Bukkit.getRegionScheduler().runDelayed(plugin, location, scheduledTask -> task.run(), delayTicks);
    }

    /**
     * Run on the global region.
     */
    public static void runGlobal(JavaPlugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, scheduledTask -> task.run());
    }

    /**
     * Run repeating task on the global region.
     */
    public static void runGlobalTimer(JavaPlugin plugin, long delayTicks, long periodTicks, Runnable task) {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> task.run(),
                delayTicks,
                periodTicks
        );
    }

    /**
     * Run true async work.
     * Warning: do NOT access player/world/block/inventory directly here.
     */
    public static void runAsync(JavaPlugin plugin, Runnable task) {
        Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }
}
