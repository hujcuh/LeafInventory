package me.LeafPixel.LeafInventory.lastseen;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Tracks player last seen time on join/quit.
 */
public final class LastSeenListener implements Listener {

    private final LastSeenManager lastSeen;

    public LastSeenListener(LastSeenManager lastSeen) {
        this.lastSeen = lastSeen;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // English comment: Update last seen time when the player joins.
        lastSeen.touch(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // English comment: Update last seen time when the player quits.
        lastSeen.touch(e.getPlayer().getUniqueId());
    }
}
