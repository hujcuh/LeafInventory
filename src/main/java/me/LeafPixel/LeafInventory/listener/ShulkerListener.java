package me.LeafPixel.LeafInventory.listener;

import me.LeafPixel.LeafInventory.shulker.ShulkerRules;
import me.LeafPixel.LeafInventory.shulker.ShulkerService;
import me.LeafPixel.LeafInventory.shulker.ShulkerSession;
import me.LeafPixel.LeafInventory.shulker.ShulkerViewHolder;
import me.LeafPixel.LeafInventory.util.Scheduler;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShulkerListener implements Listener {

    private final JavaPlugin plugin;
    private final ShulkerService shulker;
    private final boolean enableShulker;
    private final boolean usePermissions;
    private final String permNode;

    public ShulkerListener(JavaPlugin plugin,
                           ShulkerService shulker,
                           boolean enableShulker,
                           boolean usePermissions,
                           String permNode) {
        this.plugin = plugin;
        this.shulker = shulker;
        this.enableShulker = enableShulker;
        this.usePermissions = usePermissions;
        this.permNode = permNode;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryRightClick(InventoryClickEvent e) {
        if (!enableShulker) return;
        if (!(e.getWhoClicked() instanceof Player player)) return;

        if (!e.isRightClick() || e.isShiftClick()) return;

        Inventory clicked = e.getClickedInventory();
        if (clicked == null) return;
        if (clicked.getType() != InventoryType.PLAYER) return;

        ItemStack current = e.getCurrentItem();
        if (!isSingleShulker(current)) return;

        if (usePermissions && !player.hasPermission(permNode)) return;

        e.setCancelled(true);
        int slot = e.getSlot();
        Scheduler.runEntityLater(plugin, player, 1L, () -> shulker.requestOpenFromPlayerInventory(player, slot));
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onHandRightClick(PlayerInteractEvent e) {
        if (!enableShulker) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR) return;

        Player player = e.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!isSingleShulker(hand)) return;

        if (usePermissions && !player.hasPermission(permNode)) return;

        e.setCancelled(true);
        Scheduler.runEntityLater(plugin, player, 1L, () -> shulker.requestOpenFromMainHand(player));
    }

    /**
     *  guard clicks while our shulker GUI is open.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        ShulkerSession session = shulker.getSession(player.getUniqueId());
        if (session == null) return;

        // only enforce when top inventory is ours and matches session id
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof ShulkerViewHolder holder)) return;
        if (!holder.sessionId().equals(session.sessionId)) return;

        // deny dangerous clicks by rules
        if (ShulkerRules.shouldCancelClick(e, session)) {
            e.setCancelled(true);
            return;
        }

        // also deny drop actions/click types inside our view (prevents Q drop duplication edge cases)
        if (isDropAction(e.getAction()) || e.getClick() == ClickType.DROP || e.getClick() == ClickType.CONTROL_DROP) {
            e.setCancelled(true);
            return;
        }

        // deny offhand swap
        if (e.getClick() == ClickType.SWAP_OFFHAND || e.getAction() == InventoryAction.SWAP_WITH_CURSOR) {
            // SWAP_WITH_CURSOR is conservative; keep for safety when cursor interactions behave oddly
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!shulker.isSessionOpen(player.getUniqueId())) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDropItem(PlayerDropItemEvent e) {
        if (!shulker.isSessionOpen(e.getPlayer().getUniqueId())) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        if (!shulker.isSessionOpen(e.getPlayer().getUniqueId())) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        shulker.handleClose(player, e.getInventory());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent e) {
        // quit: abort + refund virtual inventory
        shulker.forceAbortIfOpen(e.getPlayer(), "quit", true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKick(PlayerKickEvent e) {
        // kick: abort + refund virtual inventory
        shulker.forceAbortIfOpen(e.getPlayer(), "kick", true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        // death: conservative - abort without refund (avoid weird interactions with drops)
        shulker.forceAbortIfOpen(e.getEntity(), "death", false);
    }

    private static boolean isDropAction(InventoryAction action) {
        return action == InventoryAction.DROP_ALL_CURSOR
                || action == InventoryAction.DROP_ONE_CURSOR
                || action == InventoryAction.DROP_ALL_SLOT
                || action == InventoryAction.DROP_ONE_SLOT;
    }

    private static boolean isSingleShulker(ItemStack stack) {
        if (stack == null) return false;
        if (stack.getAmount() != 1) return false;
        Material t = stack.getType();
        return switch (t) {
            case SHULKER_BOX,
                 WHITE_SHULKER_BOX, ORANGE_SHULKER_BOX, MAGENTA_SHULKER_BOX, LIGHT_BLUE_SHULKER_BOX,
                 YELLOW_SHULKER_BOX, LIME_SHULKER_BOX, PINK_SHULKER_BOX, GRAY_SHULKER_BOX,
                 LIGHT_GRAY_SHULKER_BOX, CYAN_SHULKER_BOX, PURPLE_SHULKER_BOX, BLUE_SHULKER_BOX,
                 BROWN_SHULKER_BOX, GREEN_SHULKER_BOX, RED_SHULKER_BOX, BLACK_SHULKER_BOX -> true;
            default -> false;
        };
    }
}
