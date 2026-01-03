
package me.LeafPixel.LeafInventory.listener;

import me.LeafPixel.LeafInventory.config.LeafConfig;
import me.LeafPixel.LeafInventory.shulker.ShulkerItemIO;
import me.LeafPixel.LeafInventory.shulker.ShulkerSessionManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ShulkerListener wires the shulker session manager into Bukkit events.
 *
 * Responsibilities:
 * - Open shulker editor UI from:
 *   1) Right-clicking a shulker in the player's inventory
 *   2) Right-clicking air while holding a shulker (main hand)
 * - Forward inventory close to session manager for commit/rollback
 * - Force-close on quit/kick/death (safe due to token verification)
 */
public final class ShulkerListener implements Listener {

    private final JavaPlugin plugin;
    private final LeafConfig cfg;
    private final ShulkerSessionManager sessions;

    public ShulkerListener(JavaPlugin plugin, LeafConfig cfg, ShulkerSessionManager sessions) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.sessions = sessions;
    }

    // -------------------- Open from inventory (player inventory only) --------------------

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryRightClickShulker(InventoryClickEvent e) {
        if (!cfg.enableShulkerbox) return;

        // Only right click, and no shift-click.
        if (!e.isRightClick() || e.isShiftClick()) return;

        if (!(e.getWhoClicked() instanceof Player player)) return;

        // Only allow opening from player's own inventory (bottom inventory).
        Inventory clicked = e.getClickedInventory();
        if (clicked == null || clicked.getType() != InventoryType.PLAYER) return;

        ItemStack item = e.getCurrentItem();
        if (item == null || item.getType().isAir()) return;
        if (item.getAmount() != 1) return;

        Material type = item.getType();
        if (!ShulkerItemIO.isShulkerBox(type)) return;

        // Permission gate (kept consistent with your original behaviour).
        if (cfg.usePermissions && !player.hasPermission("leafinventory.shulkerbox")) return;

        // If already editing, do not allow another open. (Hard safety rule)
        if (sessions.isEditing(player.getUniqueId())) {
            e.setCancelled(true);
            return;
        }

        int slot = e.getSlot(); // Player inventory slot index (0..)
        e.setCancelled(true);

        // Run next tick to avoid conflicts with vanilla click processing.
        plugin.getServer().getScheduler().runTask(plugin, () -> sessions.tryOpenFromSlot(player, slot));
    }

    // -------------------- Open from hand (right-click air) --------------------

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRightClickAirOpenShulker(PlayerInteractEvent e) {
        if (!cfg.enableShulkerbox) return;

        // Only main hand (avoid firing twice for offhand)
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR) return;

        Player player = e.getPlayer();

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) return;
        if (item.getAmount() != 1) return;
        if (!ShulkerItemIO.isShulkerBox(item.getType())) return;

        if (cfg.usePermissions && !player.hasPermission("leafinventory.shulkerbox")) return;

        if (sessions.isEditing(player.getUniqueId())) {
            e.setCancelled(true);
            return;
        }

        // Hotbar slot (0..8). This is a valid PlayerInventory slot index to set/get.
        int slot = player.getInventory().getHeldItemSlot();

        e.setCancelled(true);
        plugin.getServer().getScheduler().runTask(plugin, () -> sessions.tryOpenFromSlot(player, slot));
    }

    // -------------------- Close handling (commit/rollback) --------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        if (!sessions.isEditing(player.getUniqueId())) return;

        // e.getInventory() is the top inventory that is closing
        sessions.handleClose(player, e.getInventory());
    }

    // -------------------- Force-close on lifecycle events --------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent e) {
        if (sessions.isEditing(e.getPlayer().getUniqueId())) {
            sessions.forceClose(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKick(PlayerKickEvent e) {
        if (sessions.isEditing(e.getPlayer().getUniqueId())) {
            sessions.forceClose(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (sessions.isEditing(p.getUniqueId())) {
            sessions.forceClose(p);
        }
    }
}
