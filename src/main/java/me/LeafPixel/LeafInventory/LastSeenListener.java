
package me.LeafPixel.LeafInventory;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class LastSeenListener implements Listener {

    private final LastSeenManager lastSeen;

    public LastSeenListener(LastSeenManager lastSeen) {
        this.lastSeen = lastSeen;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        lastSeen.touch(e.getPlayer().getUniqueId());

    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastSeen.touch(e.getPlayer().getUniqueId());

    }
}
