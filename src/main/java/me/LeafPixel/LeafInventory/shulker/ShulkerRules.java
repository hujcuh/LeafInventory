
package me.LeafPixel.LeafInventory.shulker;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;

/**
 * Click guard rules while a shulker session is open.
 * This is intentionally conservative to prevent carrier movement/desync.
 */
public final class ShulkerRules {

    private ShulkerRules() {}

    public static boolean shouldCancelClick(InventoryClickEvent e, ShulkerSession session) {
        if (session == null) return false;

        // English comment: Always deny hotbar number key swaps to prevent moving the carrier indirectly.
        if (e.getClick() == ClickType.NUMBER_KEY) return true;

        // English comment: In modern Paper versions, HOTBAR_MOVE_AND_READD no longer happens.
        // Everything is represented as HOTBAR_SWAP instead.
        if (e.getAction() == InventoryAction.HOTBAR_SWAP) return true;

        // Optional: deny shift-click "move to other inventory" for maximum safety.
        // if (e.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) return true;

        // English comment: Deny clicking/moving the carrier slot itself (player inventory).
        if (session.binding.type() == ShulkerSession.CarrierBinding.Type.PLAYER_SLOT
                && e.getClickedInventory() != null
                && e.getClickedInventory().getType() == InventoryType.PLAYER
                && e.getSlot() == session.binding.slot()) {
            return true;
        }

        return false;
    }
}
