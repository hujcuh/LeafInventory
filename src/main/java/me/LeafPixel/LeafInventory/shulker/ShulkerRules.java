package me.LeafPixel.LeafInventory.shulker;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Click guard rules while a regular shulker session is open.
 *
 * Conservative first-stage implementation.
 */
public final class ShulkerRules {

    private ShulkerRules() {
    }

    public static boolean shouldCancelClick(InventoryClickEvent event, ShulkerSession session) {
        if (session == null) {
            return false;
        }

        /*
         * Block known dangerous click types while a shulker session is open.
         * This prevents carrier movement/desync.
         */
        ClickType click = event.getClick();
        InventoryAction action = event.getAction();

        return click == ClickType.NUMBER_KEY
                || click == ClickType.SWAP_OFFHAND
                || click == ClickType.DOUBLE_CLICK
                || click == ClickType.DROP
                || click == ClickType.CONTROL_DROP
                || click == ClickType.UNKNOWN
                || action == InventoryAction.HOTBAR_SWAP
                || action == InventoryAction.COLLECT_TO_CURSOR
                || action == InventoryAction.DROP_ALL_CURSOR
                || action == InventoryAction.DROP_ONE_CURSOR
                || action == InventoryAction.DROP_ALL_SLOT
                || action == InventoryAction.DROP_ONE_SLOT;
    }
}
