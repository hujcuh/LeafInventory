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
 * Routes portable menu open events to MenuService.
 *
 * This listener avoids opening other menus while a shulker session is active,
 * because switching views while a shulker is being edited can cause unsafe
 * close/writeback ordering.
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

    /**
     * Right-click a supported item inside the player's inventory.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryRightClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (shulker.isSessionOpen(player.getUniqueId())) {
            return;
        }

        if (!event.isRightClick() || event.isShiftClick()) {
            return;
        }

        Inventory clicked = event.getClickedInventory();
        if (clicked == null) {
            return;
        }

        if (clicked.getType() != InventoryType.PLAYER) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir()) {
            return;
        }

        Material type = item.getType();

        if (!menus.isSupportedMenuItem(type)) {
            return;
        }

        event.setCancelled(true);

        Scheduler.runEntityLater(plugin, player, 1L, () -> menus.openFromItem(player, type));
    }

    /**
     * Right-click air or block while holding a supported item in the main hand.
     *
     * RIGHT_CLICK_BLOCK is handled too, so the player does not accidentally place
     * or interact with the item when they intended to open the portable menu.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onHandRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        /*
        * Only open portable menus when right-clicking air.
        *
        * RIGHT_CLICK_BLOCK must be left to vanilla so players can place
        * ender chests, crafting tables, and other workstation blocks.
        */
        if (event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }

        Player player = event.getPlayer();

        if (shulker.isSessionOpen(player.getUniqueId())) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType().isAir()) {
            return;
        }

        Material type = item.getType();

        if (!menus.isSupportedMenuItem(type)) {
            return;
        }

        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);

        Scheduler.runEntityLater(plugin, player, 1L, () -> menus.openFromItem(player, type));
    }
}
