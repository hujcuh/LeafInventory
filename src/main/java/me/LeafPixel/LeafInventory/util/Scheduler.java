package me.LeafPixel.LeafInventory.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Scheduler utility for running tasks on the main server thread.
 * Keep all inventory open/close and write-back operations on the main thread.
 */
public final class Scheduler {

    private Scheduler() {
        // Utility class
    }

    /**
     * Run a task on the next server tick (main thread).
     *
     * @param plugin plugin instance
     * @param task   runnable task
     */
    public static void runNextTick(JavaPlugin plugin, Runnable task) {
        // English comment: Inventory interactions during click events are unsafe; defer to next tick.
        Bukkit.getScheduler().runTask(plugin, task);
    }

    /**
     * Run a task after the given number of ticks (main thread).
     *
     * @param plugin plugin instance
     * @param delayTicks delay in ticks
     * @param task runnable task
     */
    public static void runLater(JavaPlugin plugin, long delayTicks, Runnable task) {
        // English comment: Use this for cooldowns or delayed commits if needed.
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    /**
     * Ensure a task runs on the main thread; if already on main thread, run immediately.
     *
     * @param plugin plugin instance
     * @param task runnable task
     */
    public static void runSync(JavaPlugin plugin, Runnable task) {
        // English comment: Always mutate inventories on the main thread.
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /**
     * Run a repeating task (main thread).
     *
     * @param plugin plugin instance
     * @param delay initial delay
     * @param period repeating period
     * @param task runnable task
     */
    public static void runTimer(JavaPlugin plugin, long delay, long period, Runnable task) {
        // English comment: Useful for cleanup tasks or periodic checks.
        Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period);
    }
}
