package me.LeafPixel.LeafInventory.workstation;

import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Prevents non-privileged players from accessing/modifying the workstation world.
 */
public final class WorkstationGuardListener implements Listener {

    private final World workstationWorld;
    private final String bypassPerm;

    public WorkstationGuardListener(World workstationWorld, String bypassPerm) {
        this.workstationWorld = workstationWorld;
        this.bypassPerm = bypassPerm;
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo() == null || e.getTo().getWorld() == null) return;
        if (!e.getTo().getWorld().equals(workstationWorld)) return;

        // Only players with bypass permission may enter workstation world.
        if (!e.getPlayer().hasPermission(bypassPerm)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        if (e.getTo() == null || e.getTo().getWorld() == null) return;
        if (!e.getTo().getWorld().equals(workstationWorld)) return;

        // Only players with bypass permission may enter workstation world.
        if (!e.getPlayer().hasPermission(bypassPerm)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (!e.getBlock().getWorld().equals(workstationWorld)) return;

        // Disallow block break unless bypass permission is present.
        if (!e.getPlayer().hasPermission(bypassPerm)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (!e.getBlock().getWorld().equals(workstationWorld)) return;

        // Disallow block place unless bypass permission is present.
        if (!e.getPlayer().hasPermission(bypassPerm)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        if (!e.getClickedBlock().getWorld().equals(workstationWorld)) return;

        // Disallow interaction unless bypass permission is present.
        if (!e.getPlayer().hasPermission(bypassPerm)) {
            e.setCancelled(true);
        }
    }
}
