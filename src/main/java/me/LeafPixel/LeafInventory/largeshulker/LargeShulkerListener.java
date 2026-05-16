package me.LeafPixel.LeafInventory.largeshulker;

import me.LeafPixel.LeafInventory.util.Scheduler;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles item-based large shulker opening/conversion.
 *
 * Placed block lifecycle is handled by LargeShulkerBlockListener.
 */
public final class LargeShulkerListener implements Listener {

    private final JavaPlugin plugin;
    private final LargeShulkerService service;

    public LargeShulkerListener(JavaPlugin plugin, LargeShulkerService service) {
        this.plugin = plugin;
        this.service = service;
    }

    /**
     * Open or create large shulker from player inventory.
     *
     * Rules:
     * - Existing large shulker: open 54-slot GUI.
     * - Normal shulker + Shift-right + create permission: create 54-slot shulker.
     * - Normal shulker + normal right-click: do nothing; regular ShulkerListener handles it.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryRightClick(InventoryClickEvent event) {
        if (!service.isEnabled()) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() == null) {
            return;
        }

        if (event.getClickedInventory().getType() != InventoryType.PLAYER) {
            return;
        }

        ClickType click = event.getClick();

        if (click != ClickType.RIGHT && click != ClickType.SHIFT_RIGHT) {
            return;
        }

        ItemStack item = event.getCurrentItem();

        if (!isSingleShulker(item)) {
            return;
        }

        int slot = event.getSlot();

        /*
         * Existing large shulker:
         * LargeShulker owns this event.
         */
        if (service.isLargeShulker(item)) {
            event.setCancelled(true);

            Scheduler.runEntityLater(plugin, player, 1L, () -> {
                service.openExistingFromPlayerInventory(player, slot);
            });
            return;
        }

        /*
         * Normal shulker + Shift-right + create permission:
         * Convert/create as large shulker.
         */
        if (click == ClickType.SHIFT_RIGHT && service.canCreate(player)) {
            event.setCancelled(true);

            Scheduler.runEntityLater(plugin, player, 1L, () -> {
                service.createAndOpenFromPlayerInventory(player, slot);
            });
        }
    }

    /**
     * Open or create large shulker from main hand.
     *
     * Rules:
     * - Existing large shulker: open 54-slot GUI.
     * - Normal shulker + sneaking + create permission: create 54-slot shulker.
     * - Normal shulker + normal right-click: do nothing; regular ShulkerListener handles it.
     *
     * IMPORTANT:
     * Only RIGHT_CLICK_AIR is handled here.
     * RIGHT_CLICK_BLOCK is reserved for vanilla placement or LargeShulkerBlockListener.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onHandRightClick(PlayerInteractEvent event) {
        if (!service.isEnabled()) {
            return;
        }

        Player player = event.getPlayer();

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }

        ItemStack item = event.getItem();

        if (!isSingleShulker(item)) {
            return;
        }

        /*
         * Existing large shulker:
         * LargeShulker owns this air-right-click event.
         */
        if (service.isLargeShulker(item)) {
            denyVanillaInteraction(event);

            Scheduler.runEntity(plugin, player, () -> {
                service.openExistingFromMainHand(player);
            });
            return;
        }

        /*
         * Normal shulker + sneaking + create permission:
         * Convert/create as large shulker.
         */
        if (player.isSneaking() && service.canCreate(player)) {
            denyVanillaInteraction(event);

            Scheduler.runEntity(plugin, player, () -> {
                service.createAndOpenFromMainHand(player);
            });
        }
    }

    /**
     * Guard dangerous interactions while a large shulker GUI is open.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClickGuard(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        LargeShulkerSession session = service.getSession(player.getUniqueId());

        if (session == null) {
            return;
        }

        if (!(event.getView().getTopInventory().getHolder() instanceof LargeShulkerHolder holder)) {
            return;
        }

        if (!holder.shulkerId().equals(session.shulkerId())) {
            return;
        }

        if (service.isCarrierSlotClick(player, session, event.getRawSlot(), event.getSlot())) {
            event.setCancelled(true);
            return;
        }

        if (isDangerousClick(event.getClick(), event.getAction())) {
            event.setCancelled(true);
        }
    }

    /**
     * Conservative drag guard for large shulker.
     *
     * Dragging inside the top large shulker inventory is allowed.
     * Dragging into the player inventory is blocked to avoid carrier movement/desync.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        LargeShulkerSession session = service.getSession(player.getUniqueId());

        if (session == null) {
            return;
        }

        if (!(event.getView().getTopInventory().getHolder() instanceof LargeShulkerHolder holder)) {
            return;
        }

        if (!holder.shulkerId().equals(session.shulkerId())) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();

        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (service.isSessionOpen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
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

    private static boolean isSingleShulker(ItemStack stack) {
        return stack != null
                && stack.getType() != Material.AIR
                && stack.getAmount() == 1
                && LargeShulkerService.isShulkerMaterial(stack.getType());
    }

    private static boolean isDangerousClick(ClickType click, InventoryAction action) {
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

    private static void denyVanillaInteraction(PlayerInteractEvent event) {
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
    }
}
