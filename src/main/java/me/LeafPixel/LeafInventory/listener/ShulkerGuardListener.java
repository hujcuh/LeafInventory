
package me.LeafPixel.LeafInventory.listener;

import me.LeafPixel.LeafInventory.config.LeafConfig;
import me.LeafPixel.LeafInventory.config.ShulkerLockMode;
import me.LeafPixel.LeafInventory.shulker.ShulkerEditSession;
import me.LeafPixel.LeafInventory.shulker.ShulkerItemIO;
import me.LeafPixel.LeafInventory.shulker.ShulkerSessionManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Extra protection layer for shulker editing sessions.
 *
 * This listener specifically addresses your reproduction case:
 * - While the shulker UI is open, players right-click another shulker
 *   in the bottom inventory (player inventory) -> can cause dupe/desync.
 *
 * Strategy:
 * - If a player has an active session:
 *   - Optionally block opening other shulkers while editing (config controlled)
 *   - Cancel dangerous click types/actions that can move/replace the backing item
 *   - Optionally (FULL_INVENTORY) cancel almost all inventory interactions
 */
public final class ShulkerGuardListener implements Listener {

    private final LeafConfig cfg;
    private final ShulkerSessionManager sessions;

    // Stable actions that frequently cause desync/slot replacement.
    private static final Set<InventoryAction> DANGEROUS_ACTIONS = Set.of(
            InventoryAction.HOTBAR_SWAP,
            InventoryAction.MOVE_TO_OTHER_INVENTORY,
            InventoryAction.COLLECT_TO_CURSOR
    );

    // Click types that can bypass typical checks and move items quickly.
    private static final Set<ClickType> DANGEROUS_CLICKS = Set.of(
            ClickType.NUMBER_KEY,
            ClickType.SWAP_OFFHAND,
            ClickType.DOUBLE_CLICK
    );

    public ShulkerGuardListener(LeafConfig cfg, ShulkerSessionManager sessions) {
        this.cfg = cfg;
        this.sessions = sessions;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClickWhileEditing(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        ShulkerEditSession s = sessions.get(player.getUniqueId());
        if (s == null) return; // not editing

        // FULL_INVENTORY mode: safest option, cancel almost everything.
        if (cfg.shulkerLockMode == ShulkerLockMode.FULL_INVENTORY) {
            e.setCancelled(true);
            return;
        }

        // SLOT_ONLY mode: allow normal editing, but block dangerous operations.

        // 1) Block attempts to open another shulker from bottom inventory while UI is open.
        //    This is the exact repro case you reported.
        if (cfg.shulkerBlockOpenWhileEditing) {
            Inventory clicked = e.getClickedInventory();
            if (clicked != null && clicked.getType() == InventoryType.PLAYER) {
                ItemStack cur = e.getCurrentItem();
                if (cur != null && cur.getAmount() == 1 && ShulkerItemIO.isShulkerBox(cur.getType())) {
                    if (e.isRightClick() && !e.isShiftClick()) {
                        e.setCancelled(true);
                        return;
                    }
                }
            }
        }

        // 2) Lock the original backing item slot (prevent moving/replacing it).
        Inventory clicked = e.getClickedInventory();
        if (clicked != null && clicked.getType() == InventoryType.PLAYER) {
            if (e.getSlot() == s.slot) {
                e.setCancelled(true);
                return;
            }
        }

        // 3) Block dangerous click types and actions that frequently cause desync.
        if (DANGEROUS_CLICKS.contains(e.getClick())) {
            e.setCancelled(true);
            return;
        }

        if (isDangerousAction(e.getAction())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDragWhileEditing(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!sessions.isEditing(player.getUniqueId())) return;

        // In SLOT_ONLY mode we still prefer to block dragging: it can affect many slots at once.
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDropWhileEditing(PlayerDropItemEvent e) {
        if (!sessions.isEditing(e.getPlayer().getUniqueId())) return;

        // Dropping items while editing can lead to client/server desync with virtual UI.
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHandWhileEditing(PlayerSwapHandItemsEvent e) {
        if (!sessions.isEditing(e.getPlayer().getUniqueId())) return;

        // Offhand swap can replace/move the backing item unexpectedly.
        e.setCancelled(true);
    }

    /**
     * Returns true if the action is considered dangerous for shulker editing.
     *
     * IMPORTANT (Plan A):
     * - We do NOT reference InventoryAction.HOTBAR_MOVE_AND_READD directly (deprecated since 1.20.6).
     * - Instead, we check action.name() to catch it when it exists at runtime without compile-time deprecation.
     */
    private static boolean isDangerousAction(InventoryAction action) {
        if (action == null) return false;

        // Stable dangerous actions
        if (DANGEROUS_ACTIONS.contains(action)) return true;

        // Compatibility: catch deprecated/removed enum constant by name without referencing it directly.
        return "HOTBAR_MOVE_AND_READD".equals(action.name());
    }
}
