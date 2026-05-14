package me.LeafPixel.LeafInventory.enderchest;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Saves large ender chest sessions on close, quit, and kick.
 */
public final class LargeEnderChestListener implements Listener {

    private final LargeEnderChestService service;

    public LargeEnderChestListener(LargeEnderChestService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        service.handleClose(player, event.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        service.handleQuitOrKick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        service.handleQuitOrKick(event.getPlayer());
    }
}
