package me.LeafPixel.LeafInventory.workstation;

import me.LeafPixel.LeafInventory.lastseen.LastSeenManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Periodically clears workstation inventories for inactive players.
 */
public final class WorkstationCleanupTask implements Runnable {

    private final JavaPlugin plugin;
    private final PortableWorkstationBackend ws;
    private final LastSeenManager lastSeen;
    private final int inactiveDays;

    public WorkstationCleanupTask(JavaPlugin plugin,
                              PortableWorkstationBackend ws,
                              LastSeenManager lastSeen,
                              int inactiveDays) {
        this.plugin = plugin;
        this.ws = ws;
        this.lastSeen = lastSeen;
        this.inactiveDays = inactiveDays;
    }

    @Override
    public void run() {
        if (inactiveDays <= 0) return;

        long now = System.currentTimeMillis();
        long cutoff = now - TimeUnit.DAYS.toMillis(inactiveDays);

        for (UUID uuid : lastSeen.all()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) continue;

            long seen = lastSeen.getLastSeen(uuid);
            if (seen > 0 && seen < cutoff) {
                ws.clearAll(uuid);

                //  Touch to avoid repeated clearing spam, then persist.
                lastSeen.touch(uuid);
                plugin.getLogger().info("[LeafInventory] Cleared workstation inventories for inactive player: " + uuid);
            }
        }

        lastSeen.save();
    }
}
