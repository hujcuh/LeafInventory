package me.LeafPixel.LeafInventory.listener;

import me.LeafPixel.LeafInventory.largeshulker.LargeShulkerService;
import me.LeafPixel.LeafInventory.shulker.ShulkerRules;
import me.LeafPixel.LeafInventory.shulker.ShulkerService;
import me.LeafPixel.LeafInventory.shulker.ShulkerSession;
import me.LeafPixel.LeafInventory.shulker.ShulkerViewHolder;
import me.LeafPixel.LeafInventory.util.Scheduler;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
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
 * Handles regular 27-slot portable shulker boxes.
 *
 * Important:
 * - Large shulker boxes are handled by LargeShulkerListener.
 * - This listener must not open items that already carry a large shulker id.
 */
public final class ShulkerListener implements Listener {

    private final JavaPlugin plugin;
    private final ShulkerService shulkerService;
    private final LargeShulkerService largeShulkerService;

    public ShulkerListener(
            JavaPlugin plugin,
            ShulkerService shulkerService,
            LargeShulkerService largeShulkerService
    ) {
        this.plugin = plugin;
        this.shulkerService = shulkerService;
        this.largeShulkerService = largeShulkerService;
    }

    /**
     * Open regular shulker box from main hand.
     *
     * This restores:
     * "hold a normal shulker box and right-click air to open 27-slot GUI".
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onHandRightClick(PlayerInteractEvent event) {
        if (!isRegularShulkerFeatureEnabled()) {
            return;
        }

        Player player = event.getPlayer();

        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        /*
        * Only open regular portable shulker from hand when right-clicking air.
        *
        * RIGHT_CLICK_BLOCK must be left to vanilla so normal shulker boxes
        * can still be placed on the ground.
        */
        if (event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isSingleShulker(item)) {
            return;
        }

        /*
        * Existing large shulker:
        * Do not open it as a regular 27-slot shulker.
        */
        if (isLargeShulker(item)) {
            return;
        }

        /*
        * Normal shulker + Shift + create permission:
        * LargeShulkerListener should handle conversion to 54-slot shulker.
        */
        if (shouldLargeShulkerListenerHandleCreation(player)) {
            return;
        }

        if (!hasRegularShulkerPermission(player)) {
            return;
        }

        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);

        Scheduler.runEntity(plugin, player, () -> {
            shulkerService.requestOpenFromMainHand(player);
        });
    }

    /**
     * Open regular shulker box from player inventory by right-clicking it.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryRightClick(InventoryClickEvent event) {
        if (!isRegularShulkerFeatureEnabled()) {
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

        /*
         * Existing large shulker:
         * Do not open it as a regular 27-slot shulker.
         */
        if (isLargeShulker(item)) {
            return;
        }

        /*
         * Normal shulker + Shift-right + create permission:
         * LargeShulkerListener should handle conversion to 54-slot shulker.
         */
        if (click == ClickType.SHIFT_RIGHT && shouldLargeShulkerListenerHandleCreation(player)) {
            return;
        }

        if (!hasRegularShulkerPermission(player)) {
            return;
        }

        int slot = event.getSlot();

        event.setCancelled(true);

        Scheduler.runEntityLater(plugin, player, 1L, () -> {
            shulkerService.requestOpenFromPlayerInventory(player, slot);
        });
    }

    /**
     * Guard dangerous clicks while a regular shulker session is open.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClickGuard(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ShulkerSession session = shulkerService.getSession(player.getUniqueId());
        if (session == null) {
            return;
        }

        if (!(event.getView().getTopInventory().getHolder() instanceof ShulkerViewHolder holder)) {
            return;
        }

        if (!holder.sessionId().equals(session.sessionId)) {
            return;
        }

        if (ShulkerRules.shouldCancelClick(event, session)) {
            event.setCancelled(true);
        }
    }

    /**
     * Conservative drag guard.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ShulkerSession session = shulkerService.getSession(player.getUniqueId());
        if (session == null) {
            return;
        }

        if (!(event.getView().getTopInventory().getHolder() instanceof ShulkerViewHolder holder)) {
            return;
        }

        if (!holder.sessionId().equals(session.sessionId)) {
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
        if (shulkerService.isSessionOpen(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        shulkerService.handleClose(player, event.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        shulkerService.forceAbortIfOpen(event.getPlayer(), "quit", true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKick(PlayerKickEvent event) {
        shulkerService.forceAbortIfOpen(event.getPlayer(), "kick", true);
    }

    private boolean isRegularShulkerFeatureEnabled() {
        return plugin.getConfig().getBoolean("enableShulkerbox", true);
    }

    private boolean hasRegularShulkerPermission(Player player) {
        boolean usePermissions = plugin.getConfig().getBoolean("usePermissions", false);
        return !usePermissions || player.hasPermission("leafinventory.shulkerbox");
    }

    private boolean shouldLargeShulkerListenerHandleCreation(Player player) {
        return largeShulkerService != null
                && largeShulkerService.isEnabled()
                && player.isSneaking()
                && largeShulkerService.canCreate(player);
    }

    private boolean isLargeShulker(ItemStack item) {
        return largeShulkerService != null
                && largeShulkerService.isEnabled()
                && largeShulkerService.isLargeShulker(item);
    }

    private static boolean isSingleShulker(ItemStack stack) {
        return stack != null
                && stack.getType() != Material.AIR
                && stack.getAmount() == 1
                && LargeShulkerService.isShulkerMaterial(stack.getType());
    }

    private static void denyVanillaInteraction(PlayerInteractEvent event) {
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
    }
}
