
package me.LeafPixel.LeafInventory.listener;

import me.LeafPixel.LeafInventory.util.Scheduler;
import me.LeafPixel.LeafInventory.workstation.WorkstationManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * WorkstationListener: opens per-player workstation inventories (furnace/blast/smoker).
 * Block/world allocation and storage remain in WorkstationManager.
 */
public final class WorkstationListener implements Listener {

    private final JavaPlugin plugin;
    private final WorkstationManager ws;
    private final boolean usePermissions;

    private final boolean enableFurnace;
    private final boolean enableBlastFurnace;
    private final boolean enableSmoker;

    public WorkstationListener(JavaPlugin plugin,
                               WorkstationManager ws,
                               boolean usePermissions,
                               boolean enableFurnace,
                               boolean enableBlastFurnace,
                               boolean enableSmoker) {
        this.plugin = plugin;
        this.ws = ws;
        this.usePermissions = usePermissions;
        this.enableFurnace = enableFurnace;
        this.enableBlastFurnace = enableBlastFurnace;
        this.enableSmoker = enableSmoker;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryRightClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        if (!e.isRightClick() || e.isShiftClick()) return;

        Inventory clicked = e.getClickedInventory();
        if (clicked == null) return;
        if (clicked.getType() != InventoryType.PLAYER) return;

        ItemStack current = e.getCurrentItem();
        if (current == null || current.getAmount() != 1) return;

        Material type = current.getType();
        if (!isEnabledWorkstationItem(type)) return;

        if (usePermissions && !player.hasPermission(permissionOf(type))) return;

        e.setCancelled(true);
        // English comment: Delay to next tick to avoid inventory mutation conflicts.
        Scheduler.runNextTick(plugin, () -> openWorkstation(player, type));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onHandRightClick(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR) return;

        Player player = e.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getAmount() != 1) return;

        Material type = hand.getType();
        if (!isEnabledWorkstationItem(type)) return;

        if (usePermissions && !player.hasPermission(permissionOf(type))) return;

        e.setCancelled(true);
        // English comment: Delay to next tick to avoid inventory mutation conflicts.
        Scheduler.runNextTick(plugin, () -> openWorkstation(player, type));
    }

    private boolean isEnabledWorkstationItem(Material type) {
        if (type == Material.FURNACE) return enableFurnace;
        if (type == Material.BLAST_FURNACE) return enableBlastFurnace;
        if (type == Material.SMOKER) return enableSmoker;
        return false;
    }

    private void openWorkstation(Player player, Material type) {
        // English comment: Delegate to WorkstationManager which ensures blocks/world are ready.
        if (type == Material.FURNACE) ws.openFurnace(player);
        else if (type == Material.BLAST_FURNACE) ws.openBlastFurnace(player);
        else if (type == Material.SMOKER) ws.openSmoker(player);
    }

    private static String permissionOf(Material type) {
        // English comment: Keep permission nodes centralized and predictable.
        if (type == Material.FURNACE) return "leafinventory.furnace";
        if (type == Material.BLAST_FURNACE) return "leafinventory.blastfurnace";
        return "leafinventory.smoker";
    }
}
