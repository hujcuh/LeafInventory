package me.LeafPixel.LeafInventory.listener;

import me.LeafPixel.LeafInventory.menu.MenuService;
import me.LeafPixel.LeafInventory.shulker.ShulkerService;
import me.LeafPixel.LeafInventory.util.Scheduler;
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
 * MenuListener: routes "open menu from item" events to MenuService.
 * Also blocks menu opening while a shulker session is active to avoid view switching edge-cases.
 */
public final class MenuListener implements Listener {

    private final JavaPlugin plugin;
    private final MenuService menus;
    private final ShulkerService shulker;

    public MenuListener(JavaPlugin plugin, MenuService menus, ShulkerService shulker) {
        this.plugin = plugin;
        this.menus = menus;
        this.shulker = shulker;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryRightClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        // English comment: Block menu opening while a shulker session is active (prevents view switching issues).
        if (shulker.isSessionOpen(player.getUniqueId())) return;

        // English comment: We only react to a simple right-click without shift.
        if (!e.isRightClick() || e.isShiftClick()) return;

        Inventory clicked = e.getClickedInventory();
        if (clicked == null) return;

        // English comment: Stable baseline: only allow opening from player's own inventory.
        if (clicked.getType() != InventoryType.PLAYER) return;

        ItemStack current = e.getCurrentItem();
        if (current == null || current.getAmount() != 1) return;

        Material type = current.getType();
        if (!menus.isSupportedMenuItem(type)) return;

        // English comment: Delay to next tick to avoid inventory mutation conflicts during click event.
        e.setCancelled(true);
        Scheduler.runNextTick(plugin, () -> menus.openFromItem(player, type));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onHandRightClick(PlayerInteractEvent e) {
        // English comment: Only handle main-hand to avoid double-trigger.
        if (e.getHand() != EquipmentSlot.HAND) return;

        // English comment: Keep same behavior as original: only right-click air opens.
        if (e.getAction() != Action.RIGHT_CLICK_AIR) return;

        Player player = e.getPlayer();

        // English comment: Block menu opening while a shulker session is active (prevents view switching issues).
        if (shulker.isSessionOpen(player.getUniqueId())) return;

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getAmount() != 1) return;

        Material type = hand.getType();
        if (!menus.isSupportedMenuItem(type)) return;

        e.setCancelled(true);
        Scheduler.runNextTick(plugin, () -> menus.openFromItem(player, type));
    }
}
